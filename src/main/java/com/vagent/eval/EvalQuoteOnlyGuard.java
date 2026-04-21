package com.vagent.eval;

import com.vagent.eval.dto.EvalChatResponse;
import com.vagent.eval.evidence.EvidenceMapExtractor;
import com.vagent.kb.dto.RetrieveHit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quote-only：答案中的「高风险片段」必须能在本次检索候选的正文里找到子串，否则一次性降级为
 * {@code deny} + {@code GUARDRAIL_TRIGGERED}。严格度由配置 {@code vagent.guardrails.quote-only.strictness} 控制，
 * 规则包范围由 {@code vagent.guardrails.quote-only.scope} 控制，语义见 {@code plans/quote-only-guardrails.md}。
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
     * 与 {@code vagent.guardrails.quote-only.scope} 对齐：控制启用数字 / token / 证据绑定哪几层。
     */
    public enum Scope {
        /** 仅连续数字串（≥3）子串核对。 */
        DIGITS_ONLY,
        /** 数字 +（按 strictness）moderate token / strict 英文词。 */
        DIGITS_PLUS_TOKENS,
        /** 在 {@link #DIGITS_PLUS_TOKENS} 之上，要求长度 ≥3 的每个数字串在可规则生成的 {@code evidence_map} 中有 numeric 支撑。 */
        DIGITS_PLUS_TOKENS_PLUS_EVIDENCE;

        public static Scope fromConfig(String raw) {
            if (raw == null || raw.isBlank()) {
                return DIGITS_PLUS_TOKENS;
            }
            String n = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            return switch (n) {
                case "digits_only" -> DIGITS_ONLY;
                case "digits_plus_tokens" -> DIGITS_PLUS_TOKENS;
                case "digits_plus_tokens_plus_evidence" -> DIGITS_PLUS_TOKENS_PLUS_EVIDENCE;
                default -> DIGITS_PLUS_TOKENS;
            };
        }
    }

    /**
     * 评测 {@code capabilities.guardrails.quote_only_scopes_supported} 等契约：本实现识别、可在配置中使用的全部
     * {@code scope} 名（小写 snake_case，与 {@link Scope} 枚举顺序一致）。
     */
    public static List<String> supportedScopeConfigNames() {
        return Arrays.stream(Scope.values())
                .map(e -> e.name().toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * 与 {@link #evaluate(Strictness, Scope, String, List, List)} 相同，默认 {@link Scope#DIGITS_PLUS_TOKENS}、不跑证据绑定。
     */
    public static Optional<EvalReflectionOneShotGuard.Patch> evaluate(
            Strictness strictness, String answer, List<String> corpusHits) {
        return evaluate(strictness, Scope.DIGITS_PLUS_TOKENS, answer, corpusHits, null);
    }

    /**
     * @param strictness 严格度档位
     * @param scope      启用 A/B/C 哪几层
     * @param answer     拟返回正文
     * @param corpusHits 候选命中正文（与检索 top-N 同源，通常来自 chunk content）
     * @param sources    与评测 {@code sources[]} 同源；仅 {@link Scope#DIGITS_PLUS_TOKENS_PLUS_EVIDENCE} 需要，可为 {@code null}
     */
    public static Optional<EvalReflectionOneShotGuard.Patch> evaluate(
            Strictness strictness,
            Scope scope,
            String answer,
            List<String> corpusHits,
            List<EvalChatResponse.Source> sources) {
        if (corpusHits == null || corpusHits.isEmpty()) {
            return Optional.empty();
        }
        String corpus = joinCorpus(corpusHits);
        if (corpus.isEmpty()) {
            return Optional.empty();
        }
        Scope s = scope != null ? scope : Scope.DIGITS_PLUS_TOKENS;
        String a = answer != null ? answer : "";

        Optional<String> relaxedFail = checkRelaxed(a, corpus);
        if (relaxedFail.isPresent()) {
            return patch(relaxedFail.get());
        }
        if (s == Scope.DIGITS_ONLY) {
            return Optional.empty();
        }

        if (strictness != Strictness.RELAXED) {
            Optional<String> moderateFail = checkModerateBeyondRelaxed(a, corpus);
            if (moderateFail.isPresent()) {
                return patch(moderateFail.get());
            }
        }
        if (strictness == Strictness.STRICT) {
            Optional<String> strictFail = checkStrictAsciiWords(a, corpus);
            if (strictFail.isPresent()) {
                return patch(strictFail.get());
            }
        }

        if (s == Scope.DIGITS_PLUS_TOKENS_PLUS_EVIDENCE) {
            return checkEvidenceBinding(a, sources).flatMap(EvalQuoteOnlyGuard::patch);
        }
        return Optional.empty();
    }

    private static Optional<EvalReflectionOneShotGuard.Patch> patch(String reasonDetail) {
        boolean evidenceBind = reasonDetail != null && reasonDetail.startsWith("evidence_");
        String msg =
                evidenceBind
                        ? "回答中的数字结论无法在 evidence_map 中与引用片段规则对齐（quote-only），已拒绝输出。"
                        : "回答中存在无法在检索正文中核对的片段（quote-only），已拒绝输出。";
        String tag = evidenceBind ? "QUOTE_ONLY_EVIDENCE_UNBOUND" : "QUOTE_ONLY_UNGROUNDED";
        return Optional.of(
                new EvalReflectionOneShotGuard.Patch(
                        msg,
                        "deny",
                        "GUARDRAIL_TRIGGERED",
                        "deny",
                        List.of(tag, reasonDetail)));
    }

    /**
     * 将检索命中映射为 evidence 绑定所需的 {@link EvalChatResponse.Source}（id + snippet，与 eval 响应同源口径）。
     */
    public static List<EvalChatResponse.Source> sourcesFromRetrieveHits(List<RetrieveHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<EvalChatResponse.Source> out = new ArrayList<>();
        for (RetrieveHit h : hits) {
            if (h == null) {
                continue;
            }
            String id = h.getChunkId() != null && !h.getChunkId().isBlank() ? h.getChunkId().trim() : "";
            if (id.isEmpty() && h.getDocumentId() != null && !h.getDocumentId().isBlank()) {
                id = h.getDocumentId().trim();
            }
            if (id.isEmpty()) {
                continue;
            }
            String snippet = h.getContent() != null ? h.getContent() : "";
            out.add(new EvalChatResponse.Source(id, null, snippet));
        }
        return List.copyOf(out);
    }

    /** 证据绑定层：每个长度 ≥3 的数字串必须在 {@link EvidenceMapExtractor} 产出的 numeric 条目中可比对命中。 */
    private static Optional<String> checkEvidenceBinding(
            String answer, List<EvalChatResponse.Source> sources) {
        if (sources == null || sources.isEmpty()) {
            Matcher dm = DIGIT_RUN.matcher(answer);
            if (dm.find()) {
                return Optional.of("evidence_sources_missing");
            }
            return Optional.empty();
        }
        List<EvalChatResponse.EvidenceMapItem> map = EvidenceMapExtractor.buildEvidenceMap(answer, sources);
        Matcher m = DIGIT_RUN.matcher(answer);
        LinkedHashSet<String> runs = new LinkedHashSet<>();
        while (m.find()) {
            runs.add(m.group());
        }
        if (runs.isEmpty()) {
            return Optional.empty();
        }
        boolean anyNumericItem =
                map.stream()
                        .anyMatch(
                                it -> it != null && "numeric".equals(it.getClaimType()));
        if (!anyNumericItem) {
            return Optional.of("evidence_map_missing_numeric");
        }
        for (String run : runs) {
            String norm = EvidenceMapExtractor.normalizeNumericClaimValue(run);
            if (norm.isEmpty()) {
                continue;
            }
            if (!numericEvidenceExplainsNorm(map, sources, norm)) {
                return Optional.of("evidence_missing_digit:" + run);
            }
        }
        return Optional.empty();
    }

    private static boolean numericEvidenceExplainsNorm(
            List<EvalChatResponse.EvidenceMapItem> map,
            List<EvalChatResponse.Source> sources,
            String norm) {
        for (EvalChatResponse.EvidenceMapItem it : map) {
            if (it == null || !"numeric".equals(it.getClaimType())) {
                continue;
            }
            String claimNorm = EvidenceMapExtractor.normalizeNumericClaimValue(it.getClaimValue());
            if (norm.equals(claimNorm)) {
                return true;
            }
            if (it.getSourceIds() == null || it.getSourceIds().isEmpty()) {
                continue;
            }
            for (String sid : it.getSourceIds()) {
                EvalChatResponse.Source s = sourceById(sources, sid);
                if (s != null && EvidenceMapExtractor.snippetSupportsNumericNorm(s.getSnippet(), norm)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static EvalChatResponse.Source sourceById(
            List<EvalChatResponse.Source> sources, String id) {
        if (sources == null || id == null || id.isBlank()) {
            return null;
        }
        for (EvalChatResponse.Source s : sources) {
            if (s != null && id.equals(s.getId())) {
                return s;
            }
        }
        return null;
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
                .filter(Objects::nonNull)
                .map(RetrieveHit::getContent)
                .filter(c -> c != null && !c.isBlank())
                .toList();
    }
}
