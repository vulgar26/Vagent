package com.vagent.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 相关配置绑定（签发密钥、有效期）。
 * <p>
 * <b>作用：</b> 从 {@code vagent.security.jwt.*} 读取配置，避免把密钥写死在代码里；生产环境应通过环境变量覆盖。
 * <p>
 * <b>为何需要 JWT：</b>  提供「注册/登录 + 会话 API」，HTTP 无状态场景下用 Bearer Token 识别用户，便于后续 SSE、多端调用；
 * 与 {@code userId}、多租户隔离在数据层通过「当前用户」关联。
 * <p>
 * <b>安全提示：</b> {@link #secret} 用于 HS256，长度需满足 jjwt 要求；切勿把生产密钥提交到公开仓库。
 */
@ConfigurationProperties(prefix = "vagent.security.jwt")
public class JwtProperties {

    /**
     * HMAC 密钥材料（UTF-8 字节长度需满足算法要求，开发环境默认在 yml 中占位）。
     */
    private String secret = "";

    /**
     * Access Token 有效期（秒）。
     */
    private long expirationSeconds = 86_400;

    /**
     * 当 {@code sub} 对应用户行不存在时，是否允许用 JWT 中的 {@code uname} 再查库并绑定<strong>当前</strong>该用户名的账号。
     * <p>
     * <b>默认 {@code false}（推荐生产）：</b>清库、删用户、数据迁移后应要求客户端重新登录，避免「旧 Token + 用户名被他人抢先注册」等边界下误绑账号。
     * <p>
     * <b>{@code true}：</b>仅建议本地/演示开启，缓解清库后客户端仍带旧 Bearer 的联调成本。
     */
    private boolean remapSubjectByUsernameWhenUserMissing = false;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    public void setExpirationSeconds(long expirationSeconds) {
        this.expirationSeconds = expirationSeconds;
    }

    public boolean isRemapSubjectByUsernameWhenUserMissing() {
        return remapSubjectByUsernameWhenUserMissing;
    }

    public void setRemapSubjectByUsernameWhenUserMissing(boolean remapSubjectByUsernameWhenUserMissing) {
        this.remapSubjectByUsernameWhenUserMissing = remapSubjectByUsernameWhenUserMissing;
    }
}
