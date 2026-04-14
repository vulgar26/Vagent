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
     */
    private Double lowConfidenceCosineDistanceThreshold;

    /**
     * 命中非空时：若用户 query 包含任一条子串（原样子串匹配），则视为低置信并走 {@code clarify}（可选，默认空列表关闭）。
     * 用于对齐 dataset 中指代不明类问句；生产勿随意填长列表以免误伤。
     */
    private List<String> lowConfidenceQuerySubstrings = new ArrayList<>();

    /**
     * P0+ B 线：是否启用 {@link EvalChatSafetyGate}（检索前短路拒答/澄清）。默认开启；单测可关。
     */
    private boolean safetyRulesEnabled = true;

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

    public Double getLowConfidenceCosineDistanceThreshold() {
        return lowConfidenceCosineDistanceThreshold;
    }

    public void setLowConfidenceCosineDistanceThreshold(Double lowConfidenceCosineDistanceThreshold) {
        this.lowConfidenceCosineDistanceThreshold = lowConfidenceCosineDistanceThreshold;
    }

    public List<String> getLowConfidenceQuerySubstrings() {
        return lowConfidenceQuerySubstrings;
    }

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
}

