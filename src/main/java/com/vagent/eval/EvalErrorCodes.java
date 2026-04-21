package com.vagent.eval;

/**
 * 根级 {@code error_code} 的 SSOT（评测接口 + SSE 对齐）。
 * <p>
 * 注意：不是所有分支都会写入 error_code；成功路径通常缺省/为 null。
 */
public final class EvalErrorCodes {

    private EvalErrorCodes() {}

    // Auth / policy / safety
    public static final String AUTH = "AUTH";
    public static final String POLICY_DISABLED = "POLICY_DISABLED";
    public static final String POLICY_DENY = "POLICY_DENY";
    public static final String GUARDRAIL_TRIGGERED = "GUARDRAIL_TRIGGERED";

    // Retrieval gate
    public static final String RETRIEVE_EMPTY = "RETRIEVE_EMPTY";
    public static final String RETRIEVE_LOW_CONFIDENCE = "RETRIEVE_LOW_CONFIDENCE";

    // Tool eval
    public static final String TOOL_TIMEOUT = "TOOL_TIMEOUT";
    public static final String TOOL_ERROR = "TOOL_ERROR";
}

