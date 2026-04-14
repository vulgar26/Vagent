package com.vagent.security;

import com.vagent.user.User;
import com.vagent.user.UserIdFormats;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 的创建与解析（仅服务内部使用，不对外暴露算法细节给客户端）。
 * <p>
 * <b>作用：</b> 登录成功后签发 Token；受保护接口由 {@link JwtAuthenticationFilter} 解析并还原 {@link VagentUserPrincipal}。
 * <p>
 * <b>为何单独成类：</b> 与 Spring Security 配置解耦，便于单测与日后换 RS256/网关签发等策略。
 */
@Component
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(validateSecretLength(properties.getSecret()));
    }

    private static byte[] validateSecretLength(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "vagent.security.jwt.secret 长度至少 32 字符（HS256 要求），请在 application.yml 或环境变量中配置");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 为已持久化的用户签发 Access Token。
     *
     * @param user 数据库中的用户实体
     * @return 紧凑序列化 JWT 字符串（客户端放在 Authorization: Bearer 后）
     */
    public String createAccessToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getExpirationSeconds());
        String subject =
                user != null && user.getId() != null ? UserIdFormats.canonical(user.getId()) : "";
        String uname = user != null && user.getUsername() != null ? user.getUsername().trim() : "";
        return Jwts.builder()
                .subject(subject)
                .claim("uname", uname)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    /**
     * 校验签名与有效期，并还原登录主体（失败时抛出 {@link JwtException}）。
     */
    public VagentUserPrincipal parseAccessToken(String compactJwt) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(compactJwt)
                .getPayload();
        UUID userId = parseUserIdFromSubject(claims.getSubject());
        String username = claims.get("uname", String.class);
        if (username != null) {
            username = username.trim();
        }
        return new VagentUserPrincipal(userId, username);
    }

    /**
     * MyBatis-Plus {@code IdType.ASSIGN_UUID} 可能生成无连字符的 32 位十六进制；{@link UUID#fromString} 需要标准带连字符形式。
     */
    static UUID parseUserIdFromSubject(String subject) {
        try {
            return UserIdFormats.parseUuid(subject);
        } catch (IllegalArgumentException e) {
            throw new JwtException("JWT subject 无效", e);
        }
    }
}
