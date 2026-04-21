package com.vagent.eval;

/** 低置信相关“值域”SSOT（reasons / gate）。 */
public final class EvalLowConfidence {

    private EvalLowConfidence() {}

    /** meta.low_confidence_reasons[] 的枚举值（字符串）。 */
    public static final class Reasons {
        private Reasons() {}

        public static final String SAFETY_QUERY_GATE = "SAFETY_QUERY_GATE";
        public static final String EMPTY_HITS = "EMPTY_HITS";
        public static final String QUERY_TOO_SHORT = "QUERY_TOO_SHORT";
        public static final String WEAK_TOP_HIT_DISTANCE = "WEAK_TOP_HIT_DISTANCE";
        public static final String VAGUE_QUERY_REFERENCE = "VAGUE_QUERY_REFERENCE";
    }

    /** meta.low_confidence_gate 的枚举值（字符串）。 */
    public static final class Gates {
        private Gates() {}

        public static final String PRE_RETRIEVAL_SAFETY = "pre_retrieval_safety";
        public static final String POST_RETRIEVE_GATE = "post_retrieve_gate";
        public static final String POST_RETRIEVE_ALLOW_LLM = "post_retrieve_allow_llm";
        public static final String EMPTY_HITS_ALLOW_LLM = "empty_hits_allow_llm";
        public static final String NONE = "none";
    }
}

