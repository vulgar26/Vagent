package com.vagent.eval;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 评测接口安全边界（SSOT）：是否启用 + token 哈希。
 *
 * <p>约定（P0）：disabled 时对外返回 404（隐藏存在性）；enabled 时才校验 {@code X-Eval-Token}。</p>
 *
 * <p>tokenHash 存放的是“明文 token 的 SHA-256 十六进制小写串”（或逗号分隔多值用于轮换）。</p>
 */
@ConfigurationProperties(prefix = "vagent.eval.api")
public class EvalApiProperties {

    /**
     * 是否启用 /api/v1/eval/**。默认关闭。
     */
    private boolean enabled = false;

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

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash != null ? tokenHash : "";
    }
}

