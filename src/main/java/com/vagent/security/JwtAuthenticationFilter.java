package com.vagent.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vagent.user.User;
import com.vagent.user.UserIdFormats;
import com.vagent.user.UserMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 从请求头解析 Bearer JWT，并写入 {@link SecurityContextHolder}。
 * <p>
 * <b>作用：</b> 对受保护路径，若带有合法 {@code Authorization: Bearer &lt;token&gt;}，则把 {@link VagentUserPrincipal} 设为当前认证用户；
 * 未带 Token 的请求继续交给后续过滤器，由「需认证」规则触发 401。
 * <p>
 * <b>为何跳过 /api/v1/auth/** 与 /actuator/**：</b> 注册与登录不能要求已登录；Actuator 在保持匿名可访问健康检查（与 SecurityConfig 白名单一致）。
 * <p>
 * <b>非法 Token：</b> 直接返回 401 JSON，避免把无效凭证当成匿名用户进入业务。
 * <p>
 * <b>清库后旧 Token：</b>可选（见 {@link JwtProperties#isRemapSubjectByUsernameWhenUserMissing()}）：{@code sub} 无对应行时用 {@code uname} 回查；
 * 生产默认关闭，要求重新登录更安全。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final JwtProperties jwtProperties;

    public JwtAuthenticationFilter(JwtService jwtService, UserMapper userMapper, JwtProperties jwtProperties) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.jwtProperties = jwtProperties;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/auth/") || path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(AUTH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String compact = header.substring(AUTH_PREFIX.length()).trim();
        try {
            VagentUserPrincipal parsed = jwtService.parseAccessToken(compact);
            VagentUserPrincipal principal = resolvePrincipalAgainstDatabase(parsed);
            if (principal == null) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(
                        "{\"error\":\"USER_NOT_FOUND\",\"message\":\"令牌中的用户已不存在，请重新登录\"}");
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"INVALID_TOKEN\",\"message\":\"令牌无效或已过期\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 以 JWT 解析结果查库；{@code sub} 无对应行时尝试用 {@code uname}（清库重建后 ID 会变，签名仍有效）。
     */
    private VagentUserPrincipal resolvePrincipalAgainstDatabase(VagentUserPrincipal parsed) {
        String subjectKey = UserIdFormats.canonical(parsed.getUserId());
        User user = userMapper.selectById(subjectKey);
        if (user == null
                && jwtProperties.isRemapSubjectByUsernameWhenUserMissing()
                && parsed.getUsername() != null) {
            String name = parsed.getUsername().trim();
            if (!name.isEmpty()) {
                user = userMapper.selectOne(Wrappers.lambdaQuery(User.class).eq(User::getUsername, name));
            }
        }
        if (user == null) {
            return null;
        }
        String rawId = user.getId() != null ? user.getId().trim() : "";
        if (rawId.isEmpty()) {
            return null;
        }
        UUID userId = JwtService.parseUserIdFromSubject(rawId);
        String username = user.getUsername() != null ? user.getUsername().trim() : parsed.getUsername();
        return new VagentUserPrincipal(userId, username);
    }
}
