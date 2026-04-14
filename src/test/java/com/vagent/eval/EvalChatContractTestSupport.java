package com.vagent.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 与 vagent-eval {@code EvalChatContractValidator#validate} 顶层规则对齐（本仓不依赖 eval 模块）。
 *
 * <p>校验：{@code answer} string、{@code behavior} string、{@code latency_ms} number、{@code capabilities} object、
 * {@code meta} object 且 {@code meta.mode} string；根级 {@code retrieval_hits} 须为数组（可为空，与 vagent-eval Day6+ 对齐）。</p>
 */
public final class EvalChatContractTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String TOKEN_PLAINTEXT = "s1d3-test-token";
    public static final String TOKEN_HASH = "9768cbb10efdc9a0cec74fbe4314cadd8885ba4993e19fa02e554902b2ce7533";

    private EvalChatContractTestSupport() {}

    public static void assertTopLevelEvalChatContract(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        assertTrue(root.hasNonNull("answer") && root.get("answer").isTextual(), "answer must be string");
        assertTrue(root.hasNonNull("behavior") && root.get("behavior").isTextual(), "behavior must be string");
        assertTrue(root.has("latency_ms") && root.get("latency_ms").isNumber(), "latency_ms must be number");
        assertTrue(root.hasNonNull("capabilities") && root.get("capabilities").isObject(), "capabilities must be object");
        assertTrue(root.hasNonNull("meta") && root.get("meta").isObject(), "meta must be object");
        JsonNode meta = root.get("meta");
        assertTrue(meta.hasNonNull("mode") && meta.get("mode").isTextual(), "meta.mode must be string");
        assertTrue(root.has("retrieval_hits") && root.get("retrieval_hits").isArray(), "retrieval_hits must be array");
    }
}
