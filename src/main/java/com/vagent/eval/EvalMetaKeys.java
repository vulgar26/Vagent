package com.vagent.eval;

/**
 * 评测与 SSE 对齐用的 meta 键 SSOT（避免散落的 magic string）。
 * <p>
 * 约定：键名均为 snake_case，与 {@code plans/eval-meta-trace-keys-vagent.md} / {@code plans/vagent-upgrade.md} 对齐。
 */
public final class EvalMetaKeys {

    private EvalMetaKeys() {}

    public static final String BEHAVIOR = "behavior";
    public static final String ERROR_CODE = "error_code";

    public static final String LOW_CONFIDENCE = "low_confidence";
    public static final String LOW_CONFIDENCE_REASONS = "low_confidence_reasons";
    public static final String LOW_CONFIDENCE_GATE = "low_confidence_gate";

    public static final String RETRIEVE_HIT_COUNT = "retrieve_hit_count";
    public static final String CANONICAL_HIT_ID_SCHEME = "canonical_hit_id_scheme";
    public static final String RETRIEVAL_CANDIDATE_TOTAL = "retrieval_candidate_total";
    public static final String RETRIEVAL_CANDIDATE_LIMIT_N = "retrieval_candidate_limit_n";
    public static final String RETRIEVAL_HIT_ID_HASHES = "retrieval_hit_id_hashes";

    public static final String EVAL_SAFETY_RULE_ID = "eval_safety_rule_id";

    // Tool attribution (meta only)
    public static final String TOOL_ERROR_CODE = "tool_error_code";
    public static final String TOOL_SCHEMA_VIOLATIONS = "tool_schema_violations";
    public static final String TOOL_VERSION = "tool_version";
    public static final String TOOL_SCHEMA_HASH = "tool_schema_hash";
}

