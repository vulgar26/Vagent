package com.vagent.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 从请求头解析 Bearer JWT，并写入 {@link SecurityContextHolder}。
 * <p>
 * <b>作用：</b> 对受保护路径，若带有合法 {@code Authorization: Bearer &lt;token&gt;}，则把 {@link VagentUserPrincipal} 设为当前认证用户；
 * 未带 Token 的请求继续交给后续过滤器，由「需认证」规则触发 401。
 * <p>
 * <b>为何跳过 /api/v1/auth/** 与 /actuator/**：</b> 注册与登录不能要求已登录；Actuator 在保持匿名可访问健康检查（与 SecurityConfig 白名单一致）。
 * <p>
 * <b>非法 Token：</b> 直接返回 401 JSON，避免把无效凭证当成匿名用户进入业务。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
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
            VagentUserPrincipal principal = jwtService.parseAccessToken(compact);
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
}
