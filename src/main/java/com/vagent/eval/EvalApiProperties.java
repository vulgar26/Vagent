package com.vagent.eval;

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
}

