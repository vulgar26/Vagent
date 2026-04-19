package com.vagent.eval;

import com.vagent.kb.dto.RetrieveHit;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quote-only：答案中的「高风险片段」必须能在本次检索候选的正文里找到子串，否则一次性降级为
 * {@code deny} + {@code GUARDRAIL_TRIGGERED}。严格度由配置 {@code vagent.guardrails.quote-only.strictness} 控制，
 * 语义见 {@code plans/quote-only-guardrails.md}。
 */
public final class EvalQuoteOnlyGuard {

    private static final Pattern DIGIT_RUN = Pattern.compile("\\d{3,}");
    /** 字母数字连续段（含中文等 Unicode 字母） */
    private static final Pattern ALNUM_TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern ASCII_WORD = Pattern.compile("[A-Za-z]{5,}");

    /**
     * 常见英文功能词；仅用于 STRICT 的「长英文词」过滤，避免把 the、about 之类当硬锚点。
     */
    private static final Set<String> ASCII_STOPWORDS =
            Set.of(
                    "about", "after", "again", "also", "before", "being", "below", "between", "could",
                    "every", "first", "found", "great", "hello", "their", "there", "these", "those",
                    "through", "under", "where", "which", "while", "would", "couldn", "should", "wouldn");

    private EvalQuoteOnlyGuard() {}

    public enum Strictness {
        RELAXED,
        MODERATE,
        STRICT;

        public static Strictness fromConfig(String raw) {
            if (raw == null || raw.isBlank()) {
                return MODERATE;
            }
            try {
                return Strictness.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return MODERATE;
            }
        }
    }

    /**
     * @param strictness 严格度档位
     * @param answer     拟返回正文
     * @param corpusHits 候选命中正文（与检索 top-N 同源，通常来自 chunk content）
     */
    public static Optional<EvalReflectionOneShotGuard.Patch> evaluate(
            Strictness strictness, String answer, List<String> corpusHits) {
        if (corpusHits == null || corpusHits.isEmpty()) {
            return Optional.empty();
        }
        String corpus = joinCorpus(corpusHits);
        if (corpus.isEmpty()) {
            return Optional.empty();
        }
        String a = answer != null ? answer : "";

        Optional<String> relaxedFail = checkRelaxed(a, corpus);
        if (relaxedFail.isPresent()) {
            return patch(relaxedFail.get());
        }
        if (strictness == Strictness.RELAXED) {
            return Optional.empty();
        }

        Optional<String> moderateFail = checkModerateBeyondRelaxed(a, corpus);
        if (moderateFail.isPresent()) {
            return patch(moderateFail.get());
        }
        if (strictness == Strictness.MODERATE) {
            return Optional.empty();
        }

        Optional<String> strictFail = checkStrictAsciiWords(a, corpus);
        return strictFail.map(EvalQuoteOnlyGuard::patch).orElse(Optional.empty());
    }

    private static Optional<EvalReflectionOneShotGuard.Patch> patch(String reasonDetail) {
        return Optional.of(
                new EvalReflectionOneShotGuard.Patch(
                        "回答中存在无法在检索正文中核对的片段（quote-only），已拒绝输出。",
                        "deny",
                        "GUARDRAIL_TRIGGERED",
                        "deny",
                        List.of("QUOTE_ONLY_UNGROUNDED", reasonDetail)));
    }

    private static String joinCorpus(List<String> hits) {
        StringBuilder sb = new StringBuilder();
        for (String h : hits) {
            if (h == null || h.isBlank()) {
                continue;
            }
            String n = h.replaceAll("\\s+", " ").trim();
            if (!n.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(n);
            }
        }
        return sb.toString();
    }

    /** 所有长度 ≥3 的连续数字串必须在 corpus 中出现。 */
    private static Optional<String> checkRelaxed(String answer, String corpus) {
        Matcher m = DIGIT_RUN.matcher(answer);
        while (m.find()) {
            String run = m.group();
            if (!corpus.contains(run)) {
                return Optional.of("digit_run:" + run);
            }
        }
        return Optional.empty();
    }

    /**
     * 在 RELAXED 之上：长度 ≥8 的字母数字段，或「长度 ≥4 且含数字」的段，须在 corpus 中出现（纯数字段已由 RELAXED
     * 覆盖，此处仍会命中混合码）。
     */
    private static Optional<String> checkModerateBeyondRelaxed(String answer, String corpus) {
        Matcher m = ALNUM_TOKEN.matcher(answer);
        while (m.find()) {
            String tok = m.group();
            if (tok.length() >= 8) {
                if (!corpusContainsToken(corpus, tok)) {
                    return Optional.of("long_token:" + tok);
                }
            } else if (tok.length() >= 4 && containsDigit(tok)) {
                if (!corpusContainsToken(corpus, tok)) {
                    return Optional.of("short_mixed_token:" + tok);
                }
            }
        }
        return Optional.empty();
    }

    /** STRICT：仅英文、长度 ≥5、非停用词的整词，须在 corpus 中可找到（忽略大小写）。 */
    private static Optional<String> checkStrictAsciiWords(String answer, String corpus) {
        String lowerCorpus = corpus.toLowerCase(Locale.ROOT);
        Matcher m = ASCII_WORD.matcher(answer);
        while (m.find()) {
            String w = m.group();
            String lw = w.toLowerCase(Locale.ROOT);
            if (ASCII_STOPWORDS.contains(lw)) {
                continue;
            }
            if (!lowerCorpus.contains(lw)) {
                return Optional.of("ascii_word:" + w);
            }
        }
        return Optional.empty();
    }

    private static boolean corpusContainsToken(String corpus, String token) {
        if (containsOnlyAsciiLettersAndDigits(token)) {
            return corpus.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
        }
        return corpus.contains(token);
    }

    private static boolean containsOnlyAsciiLettersAndDigits(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c > 0x7F) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** 与 {@code EvalChatController} / SSE 主链路共用：从检索候选取正文组成 quote-only corpus。 */
    public static List<String> corpusFromRetrieveHits(List<RetrieveHit> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(RetrieveHit::getContent)
                .filter(c -> c != null && !c.isBlank())
                .toList();
    }
}
