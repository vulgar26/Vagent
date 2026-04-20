package com.vagent.eval;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 评测接口安全边界（SSOT）：是否启用 + token 哈希。
 *
 * <p>约定（P0）：disabled 时对外返回 404（隐藏存在性）；enabled 时才校验 {@code X-Eval-Token}。</p>
 *
 * <p>tokenHash 存放的是“明文 token 的 SHA-256 十六进制小写串”（或逗号分隔多值用于轮换）。</p>
 *
 * <p>debugEnabled：仅当为 true 且请求 {@code mode=EVAL_DEBUG} 时，响应 {@code meta} 才允许出现明文
 * {@code retrieval_hit_ids[]}；生产默认 false，避免误开侧信道。</p>
 *
 * <p>allowCidrs：非空时，仅当客户端 IP（见 {@link #trustForwardedHeaders}）落入其中任一 CIDR 时，才允许输出明文
 * {@code retrieval_hit_ids[]}；为空则不按 IP 限制（仍须 debugEnabled + {@code mode=EVAL_DEBUG} + 合法 token）。</p>
 */
@ConfigurationProperties(prefix = "vagent.eval.api")
public class EvalApiProperties {

    /**
     * 是否启用 /api/v1/eval/**。默认关闭。
     */
    private boolean enabled = false;

    /**
     * 是否允许在 {@code mode=EVAL_DEBUG} 时返回明文命中 id 列表（仍须通过 X-Eval-Token）。默认关闭。
     */
    private boolean debugEnabled = false;

    /**
     * 逗号分隔的 token 哈希（hex）。允许多值用于轮换窗口。
     */
    private String tokenHash = "";

    /**
     * 明文 debug 字段（{@code meta.retrieval_hit_ids[]}）的客户端 IP 允许列表；空列表表示不按网段限制。
     */
    private List<String> allowCidrs = new ArrayList<>();

    /**
     * 是否在解析客户端 IP 时信任 {@code X-Forwarded-For}（仅建议在反向代理统一改写/剥离该头时开启）。
     */
    private boolean trustForwardedHeaders = false;

    /**
     * 与 vagent-eval {@code eval.membership.top-n} 对齐的默认前 N 条（请求未带 {@code X-Eval-Membership-Top-N} 时使用）。
     * 实际写入 {@code sources}/{@code retrieval_hits} 时尚受评测控制器内候选上限（≤50）约束。
     */
    private int membershipTopN = 8;

    /**
     * 余弦距离门控（与 {@code KbChunkMapper} 中 {@code <=>} 一致）：**越小越相似**。
     * 若<strong>已排序</strong>检索结果的首条距离字段 <strong>大于</strong>该值，则视为低置信并走 {@code clarify}（与 P0 {@code rag/low_conf} 对齐）。
     * {@code null} 或未配置则关闭，仅靠空命中与过短 query。
     *
     * <p><b>迁移：</b>优先使用 {@code vagent.rag.gate.low-confidence-cosine-distance-threshold}；本键仍绑定并作为回退。</p>
     */
    private Double lowConfidenceCosineDistanceThreshold;

    /**
     * 命中非空时：若用户 query 包含任一条子串（原样子串匹配），则视为低置信并走 {@code clarify}（可选，默认空列表关闭）。
     * 用于对齐 dataset 中指代不明类问句；生产勿随意填长列表以免误伤。
     *
     * <p><b>迁移：</b>优先使用 {@code vagent.rag.gate.low-confidence-query-substrings}；本键仍绑定并作为回退。</p>
     */
    private List<String> lowConfidenceQuerySubstrings = new ArrayList<>();

    /**
     * P0+ B 线：是否启用 {@link EvalChatSafetyGate}（检索前短路拒答/澄清）。默认开启；单测可关。
     */
    private boolean safetyRulesEnabled = true;

    /**
     * 为 true 时：在检索后<strong>未门控短路</strong>且仍为 {@code answer} 路径时，{@code POST /api/v1/eval/chat} 会调用主链路同款
     * {@link com.vagent.llm.LlmClient} 生成 {@code answer}（与占位 {@code "OK"} 相对）。默认 false，避免 CI/默认 eval 依赖外网与成本。
     */
    private boolean fullAnswerEnabled = false;

    /**
     * {@link #fullAnswerEnabled} 为 true 时，单次 LLM 流式聚合的最长等待（毫秒）。超时则 {@code error_code=TIMEOUT}，answer 保留已生成前缀（可为空）。
     */
    private long fullAnswerTimeoutMs = 120_000L;

    /**
     * 为 true 时：对 {@code tool_policy=stub} 且 {@code expected_behavior=tool} 的请求走进程内桩工具（不依赖 MCP），
     * 使 vagent-eval 不再因 {@code capabilities.tools.supported=false} 判 {@code SKIPPED_UNSUPPORTED}。默认开启；无 MCP 的 CI 可保持工具题可跑。
     */
    private boolean stubToolsEnabled = true;

    /**
     * 桩工具（{@code tool_policy=stub}）单次执行上限（毫秒），超时则 {@code tool.succeeded=false}、{@code tool.outcome=timeout}，
     * 根级 {@code error_code=TOOL_TIMEOUT}。
     *
     * <p>{@code tool_policy=real} 的 MCP 调用与主链路相同：由 {@code vagent.mcp.tool-call-timeout} 控制 {@code tools/call} HTTP 超时，
     * 不使用本键。</p>
     */
    private long stubToolTimeoutMs = 5_000L;

    /**
     * 简易熔断：同一桩工具名连续失败（超时/异常）达到该次数后，在 {@link #stubToolCircuitOpenSeconds} 内直接拒绝调用。
     */
    private int stubToolCircuitFailureThreshold = 5;

    /** 熔断打开持续时间（秒）。 */
    private int stubToolCircuitOpenSeconds = 30;

    /**
     * 为 true 时：{@code tool_policy=stub} 进程内桩在返回成功前，对结构化 payload 校验 JSON Schema；失败则
     * {@code tool.succeeded=false}、{@code tool.outcome=error}。默认开启；排障可临时关闭。
     */
    private boolean stubToolJsonSchemaValidationEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash != null ? tokenHash : "";
    }

    public List<String> getAllowCidrs() {
        return allowCidrs;
    }

    public void setAllowCidrs(List<String> allowCidrs) {
        this.allowCidrs = allowCidrs != null ? allowCidrs : new ArrayList<>();
    }

    public boolean isTrustForwardedHeaders() {
        return trustForwardedHeaders;
    }

    public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public int getMembershipTopN() {
        return membershipTopN > 0 ? membershipTopN : 8;
    }

    public void setMembershipTopN(int membershipTopN) {
        this.membershipTopN = membershipTopN;
    }

    /** @deprecated 配置迁移至 {@code vagent.rag.gate.low-confidence-cosine-distance-threshold}；经 {@link com.vagent.rag.gate.RagPostRetrieveGateSettings} 回退。 */
    @Deprecated
    public Double getLowConfidenceCosineDistanceThreshold() {
        return lowConfidenceCosineDistanceThreshold;
    }

    /** @deprecated 见 {@link #getLowConfidenceCosineDistanceThreshold()}。 */
    @Deprecated
    public void setLowConfidenceCosineDistanceThreshold(Double lowConfidenceCosineDistanceThreshold) {
        this.lowConfidenceCosineDistanceThreshold = lowConfidenceCosineDistanceThreshold;
    }

    /** @deprecated 配置迁移至 {@code vagent.rag.gate.low-confidence-query-substrings}；经 {@link com.vagent.rag.gate.RagPostRetrieveGateSettings} 回退。 */
    @Deprecated
    public List<String> getLowConfidenceQuerySubstrings() {
        return lowConfidenceQuerySubstrings;
    }

    /** @deprecated 见 {@link #getLowConfidenceQuerySubstrings()}。 */
    @Deprecated
    public void setLowConfidenceQuerySubstrings(List<String> lowConfidenceQuerySubstrings) {
        this.lowConfidenceQuerySubstrings =
                lowConfidenceQuerySubstrings != null ? lowConfidenceQuerySubstrings : new ArrayList<>();
    }

    public boolean isSafetyRulesEnabled() {
        return safetyRulesEnabled;
    }

    public void setSafetyRulesEnabled(boolean safetyRulesEnabled) {
        this.safetyRulesEnabled = safetyRulesEnabled;
    }

    public boolean isFullAnswerEnabled() {
        return fullAnswerEnabled;
    }

    public void setFullAnswerEnabled(boolean fullAnswerEnabled) {
        this.fullAnswerEnabled = fullAnswerEnabled;
    }

    public long getFullAnswerTimeoutMs() {
        long t = fullAnswerTimeoutMs;
        if (t < 1_000L) {
            return 1_000L;
        }
        if (t > 600_000L) {
            return 600_000L;
        }
        return t;
    }

    public void setFullAnswerTimeoutMs(long fullAnswerTimeoutMs) {
        this.fullAnswerTimeoutMs = fullAnswerTimeoutMs;
    }

    public boolean isStubToolsEnabled() {
        return stubToolsEnabled;
    }

    public void setStubToolsEnabled(boolean stubToolsEnabled) {
        this.stubToolsEnabled = stubToolsEnabled;
    }

    public long getStubToolTimeoutMs() {
        long t = stubToolTimeoutMs;
        if (t < 200L) {
            return 200L;
        }
        if (t > 60_000L) {
            return 60_000L;
        }
        return t;
    }

    public void setStubToolTimeoutMs(long stubToolTimeoutMs) {
        this.stubToolTimeoutMs = stubToolTimeoutMs;
    }

    public int getStubToolCircuitFailureThreshold() {
        int v = stubToolCircuitFailureThreshold;
        return v >= 1 ? v : 5;
    }

    public void setStubToolCircuitFailureThreshold(int stubToolCircuitFailureThreshold) {
        this.stubToolCircuitFailureThreshold = stubToolCircuitFailureThreshold;
    }

    public int getStubToolCircuitOpenSeconds() {
        int s = stubToolCircuitOpenSeconds;
        return s >= 1 ? s : 30;
    }

    public void setStubToolCircuitOpenSeconds(int stubToolCircuitOpenSeconds) {
        this.stubToolCircuitOpenSeconds = stubToolCircuitOpenSeconds;
    }

    public boolean isStubToolJsonSchemaValidationEnabled() {
        return stubToolJsonSchemaValidationEnabled;
    }

    public void setStubToolJsonSchemaValidationEnabled(boolean stubToolJsonSchemaValidationEnabled) {
        this.stubToolJsonSchemaValidationEnabled = stubToolJsonSchemaValidationEnabled;
    }
}

