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
}

