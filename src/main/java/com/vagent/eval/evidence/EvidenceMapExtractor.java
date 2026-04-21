package com.vagent.eval.evidence;

import com.vagent.eval.dto.EvalChatResponse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * P1-S1：从 {@code answer + sources[]} 生成可规则验证的 {@code evidence_map[]}。
 * <p>
 * 约束：claim 必须由规则提取器生成（禁止 LLM 自由文本“伪结构化”），并且每条 claim 必须能在 sources.snippet 中被规则匹配支撑。
 */
public final class EvidenceMapExtractor {

    private EvidenceMapExtractor() {}

    // -------------------------
    // numeric/date extraction
    // -------------------------
    private static final Pattern ANSWER_NUMERIC = Pattern.compile("\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?");
    private static final Pattern ANSWER_DATE_YMD =
            Pattern.compile("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})");
    private static final Pattern ANSWER_DATE_CN =
            Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");

    // -------------------------
    // enum extraction (general)
    // -------------------------
    private static final Pattern ANSWER_ENUM_TOKEN = Pattern.compile("\\b[A-Z][A-Z0-9_]{2,}\\b");

    // -------------------------
    // enum domains (small keyword tables)
    // -------------------------
    // WEATHER: RAIN / NO_RAIN
    private static final Pattern WORD_RAIN = Pattern.compile("\\brain\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_RAINING = Pattern.compile("\\braining\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_PRECIPITATION = Pattern.compile("\\bprecipitation\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_DRY = Pattern.compile("\\bdry\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHRASE_NO_RAIN = Pattern.compile("\\bno\\s+rain\\b", Pattern.CASE_INSENSITIVE);
    private static final List<String> WEATHER_RAIN_CN = List.of("下雨", "有雨", "降雨", "降水");
    private static final List<String> WEATHER_NO_RAIN_CN = List.of("无雨", "不下雨");

    // TRANSPORT: TRAIN / FLIGHT
    private static final Pattern WORD_TRAIN = Pattern.compile("\\btrain\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_FLIGHT = Pattern.compile("\\bflight\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_PLANE = Pattern.compile("\\bplane\\b", Pattern.CASE_INSENSITIVE);
    private static final List<String> TRANSPORT_TRAIN_CN = List.of("火车", "高铁", "动车");
    private static final List<String> TRANSPORT_FLIGHT_CN = List.of("航班", "飞机");

    // RISK: HIGH / MEDIUM / LOW
    private static final Pattern WORD_RISK_HIGH = Pattern.compile("\\bhigh\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_RISK_MEDIUM = Pattern.compile("\\bmedium\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_RISK_LOW = Pattern.compile("\\blow\\b", Pattern.CASE_INSENSITIVE);
    private static final List<String> RISK_HIGH_CN = List.of("高风险", "风险高");
    private static final List<String> RISK_MEDIUM_CN = List.of("中风险", "风险中");
    private static final List<String> RISK_LOW_CN = List.of("低风险", "风险低");

    /**
     * Build evidence_map from answer and sources. Only emits claims that are supported by sources snippets.
     */
    public static List<EvalChatResponse.EvidenceMapItem> buildEvidenceMap(
            String answer, List<EvalChatResponse.Source> sources) {
        if (answer == null || answer.isBlank() || sources == null || sources.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<EvalChatResponse.EvidenceMapItem> out = new LinkedHashSet<>();
        List<EvalChatResponse.Source> safeSources =
                sources.stream()
                        .filter(s -> s != null && s.getId() != null && !s.getId().isBlank())
                        .toList();

        // numeric
        Matcher nm = ANSWER_NUMERIC.matcher(answer);
        while (nm.find() && out.size() < 8) {
            String norm = normalizeNumeric(nm.group());
            if (norm.isBlank()) {
                continue;
            }
            List<String> supporting =
                    safeSources.stream()
                            .filter(s -> snippetContainsNumeric(s.getSnippet(), norm))
                            .map(EvalChatResponse.Source::getId)
                            .toList();
            if (!supporting.isEmpty()) {
                out.add(new EvalChatResponse.EvidenceMapItem("numeric", norm, null, supporting, null));
            }
        }

        // date (ISO)
        for (Pattern p : List.of(ANSWER_DATE_YMD, ANSWER_DATE_CN)) {
            Matcher dm = p.matcher(answer);
            while (dm.find() && out.size() < 8) {
                String iso = toIsoDate(dm.group(1), dm.group(2), dm.group(3));
                if (iso == null) {
                    continue;
                }
                List<String> supporting =
                        safeSources.stream()
                                .filter(s -> snippetContainsDate(s.getSnippet(), iso))
                                .map(EvalChatResponse.Source::getId)
                                .toList();
                if (!supporting.isEmpty()) {
                    out.add(new EvalChatResponse.EvidenceMapItem("date", iso, null, supporting, null));
                }
            }
        }

        // enum (domains + explicit tokens)
        for (String enumValue : extractEnumClaims(answer)) {
            if (out.size() >= 8) {
                break;
            }
            List<String> supporting =
                    safeSources.stream()
                            .filter(s -> snippetSupportsEnum(s.getSnippet(), enumValue))
                            .map(EvalChatResponse.Source::getId)
                            .toList();
            if (!supporting.isEmpty()) {
                out.add(new EvalChatResponse.EvidenceMapItem("enum", enumValue, null, supporting, null));
            }
        }

        return List.copyOf(out);
    }

    // -------------------------
    // numeric helpers
    // -------------------------
    private static String normalizeNumeric(String raw) {
        return normalizeNumericClaimValue(raw);
    }

    /**
     * 与 {@code evidence_map} 中 numeric 条目的 {@code claim_value} 同一套归一化，供 quote-only 证据绑定层比对。
     */
    public static String normalizeNumericClaimValue(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace(",", "").trim();
        if (s.length() <= 1) {
            return "";
        }
        return s;
    }

    private static boolean snippetContainsNumeric(String snippet, String numericNorm) {
        return snippetSupportsNumericNorm(snippet, numericNorm);
    }

    /** 供 quote-only 证据绑定：snippet 去逗号后是否包含归一化数字串。 */
    public static boolean snippetSupportsNumericNorm(String snippet, String numericNorm) {
        if (snippet == null || snippet.isBlank() || numericNorm == null || numericNorm.isBlank()) {
            return false;
        }
        return snippet.replace(",", "").contains(numericNorm);
    }

    // -------------------------
    // date helpers
    // -------------------------
    private static boolean snippetContainsDate(String snippet, String isoDate) {
        if (snippet == null || snippet.isBlank() || isoDate == null || isoDate.isBlank()) {
            return false;
        }
        if (snippet.contains(isoDate)) {
            return true;
        }
        String[] parts = isoDate.split("-");
        if (parts.length != 3) {
            return false;
        }
        String y = parts[0];
        String m = String.valueOf(Integer.parseInt(parts[1]));
        String d = String.valueOf(Integer.parseInt(parts[2]));
        return snippet.contains(y + "/" + m + "/" + d) || snippet.contains(y + "年" + m + "月" + d + "日");
    }

    private static String toIsoDate(String yyyy, String mm, String dd) {
        try {
            int y = Integer.parseInt(yyyy);
            int m = Integer.parseInt(mm);
            int d = Integer.parseInt(dd);
            if (y < 1970 || m < 1 || m > 12 || d < 1 || d > 31) {
                return null;
            }
            return String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d);
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------
    // enum helpers
    // -------------------------
    private static String normalizeEnumToken(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.length() < 3) {
            return "";
        }
        return s;
    }

    private static boolean snippetContainsEnumToken(String snippet, String enumToken) {
        if (snippet == null || snippet.isBlank() || enumToken == null || enumToken.isBlank()) {
            return false;
        }
        return snippet.toUpperCase(Locale.ROOT).contains(enumToken.toUpperCase(Locale.ROOT));
    }

    private static List<String> extractEnumClaims(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();

        // explicit enum tokens (generic)
        Matcher em = ANSWER_ENUM_TOKEN.matcher(answer);
        while (em.find() && out.size() < 8) {
            String norm = normalizeEnumToken(em.group());
            if (!norm.isBlank()) {
                out.add(norm);
            }
        }

        // WEATHER: order matters (NO_RAIN first)
        if (matchesWeatherNoRain(answer)) {
            out.add("NO_RAIN");
        } else if (matchesWeatherRain(answer)) {
            out.add("RAIN");
        }

        // TRANSPORT
        if (matchesTransportTrain(answer)) {
            out.add("TRAIN");
        } else if (matchesTransportFlight(answer)) {
            out.add("FLIGHT");
        }

        // RISK
        if (matchesRiskHigh(answer)) {
            out.add("HIGH");
        } else if (matchesRiskMedium(answer)) {
            out.add("MEDIUM");
        } else if (matchesRiskLow(answer)) {
            out.add("LOW");
        }

        return List.copyOf(out);
    }

    private static boolean snippetSupportsEnum(String snippet, String enumValue) {
        if (snippetContainsEnumToken(snippet, enumValue)) {
            return true;
        }
        if ("RAIN".equals(enumValue)) {
            return snippetMatchesAny(snippet, WEATHER_RAIN_CN)
                    || WORD_RAIN.matcher(snippet).find()
                    || WORD_RAINING.matcher(snippet).find()
                    || WORD_PRECIPITATION.matcher(snippet).find();
        }
        if ("NO_RAIN".equals(enumValue)) {
            return snippetMatchesAny(snippet, WEATHER_NO_RAIN_CN)
                    || PHRASE_NO_RAIN.matcher(snippet).find()
                    || WORD_DRY.matcher(snippet).find();
        }
        if ("TRAIN".equals(enumValue)) {
            return snippetMatchesAny(snippet, TRANSPORT_TRAIN_CN) || WORD_TRAIN.matcher(snippet).find();
        }
        if ("FLIGHT".equals(enumValue)) {
            return snippetMatchesAny(snippet, TRANSPORT_FLIGHT_CN)
                    || WORD_FLIGHT.matcher(snippet).find()
                    || WORD_PLANE.matcher(snippet).find();
        }
        if ("HIGH".equals(enumValue)) {
            return snippetMatchesAny(snippet, RISK_HIGH_CN) || WORD_RISK_HIGH.matcher(snippet).find();
        }
        if ("MEDIUM".equals(enumValue)) {
            return snippetMatchesAny(snippet, RISK_MEDIUM_CN) || WORD_RISK_MEDIUM.matcher(snippet).find();
        }
        if ("LOW".equals(enumValue)) {
            return snippetMatchesAny(snippet, RISK_LOW_CN) || WORD_RISK_LOW.matcher(snippet).find();
        }
        return false;
    }

    private static boolean matchesWeatherRain(String text) {
        return snippetMatchesAny(text, WEATHER_RAIN_CN)
                || WORD_RAIN.matcher(text).find()
                || WORD_RAINING.matcher(text).find()
                || WORD_PRECIPITATION.matcher(text).find();
    }

    private static boolean matchesWeatherNoRain(String text) {
        return snippetMatchesAny(text, WEATHER_NO_RAIN_CN)
                || PHRASE_NO_RAIN.matcher(text).find()
                || WORD_DRY.matcher(text).find();
    }

    private static boolean matchesTransportTrain(String text) {
        return snippetMatchesAny(text, TRANSPORT_TRAIN_CN) || WORD_TRAIN.matcher(text).find();
    }

    private static boolean matchesTransportFlight(String text) {
        return snippetMatchesAny(text, TRANSPORT_FLIGHT_CN)
                || WORD_FLIGHT.matcher(text).find()
                || WORD_PLANE.matcher(text).find();
    }

    private static boolean matchesRiskHigh(String text) {
        return snippetMatchesAny(text, RISK_HIGH_CN) || WORD_RISK_HIGH.matcher(text).find();
    }

    private static boolean matchesRiskMedium(String text) {
        return snippetMatchesAny(text, RISK_MEDIUM_CN) || WORD_RISK_MEDIUM.matcher(text).find();
    }

    private static boolean matchesRiskLow(String text) {
        return snippetMatchesAny(text, RISK_LOW_CN) || WORD_RISK_LOW.matcher(text).find();
    }

    private static boolean snippetMatchesAny(String text, List<String> keywords) {
        if (text == null || text.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String k : keywords) {
            if (k != null && !k.isBlank() && text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}

