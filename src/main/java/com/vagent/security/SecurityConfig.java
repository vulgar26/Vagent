package com.vagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vagent.eval.EvalApiProperties;
import com.vagent.observability.TraceIdMdcFilter;
import com.vagent.user.UserMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 总配置：无状态会话 + JWT 过滤器 + 白名单 + CORS。
 * <p>
 * <b>作用：</b> 定义哪些 URL 匿名可访问、哪些必须登录；密码哈希算法；把 {@link JwtAuthenticationFilter} 插入过滤器链。
 * <p>
 * <b>为何关闭 CSRF：</b> 本服务以 REST + JWT 为主，无浏览器 Cookie 会话，CSRF 不适用；若后续增加 Cookie 登录需再评估。
 * <p>
 * <b>为何 STATELESS：</b> 不在服务端存 HttpSession，水平扩展与 SSE 场景更简单。
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, EvalApiProperties.class})
public class SecurityConfig {

    private final TraceIdMdcFilter traceIdMdcFilter;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;
    private final UserMapper userMapper;

    public SecurityConfig(
            TraceIdMdcFilter traceIdMdcFilter,
            ObjectMapper objectMapper,
            JwtService jwtService,
            JwtProperties jwtProperties,
            CorsProperties corsProperties,
            UserMapper userMapper) {
        this.traceIdMdcFilter = traceIdMdcFilter;
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.corsProperties = corsProperties;
        this.userMapper = userMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 只在 Spring Security 过滤器链中使用，避免作为 Servlet Filter 自动注册导致 order 报错
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtService, userMapper, jwtProperties);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        // eval 专用接口不走 JWT；由其自身的 X-Eval-Token + enabled 开关控制
                        .requestMatchers("/api/v1/eval/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(objectMapper.writeValueAsString(
                            java.util.Map.of("error", "UNAUTHORIZED", "message", "需要登录或令牌无效")));
                }))
                // TraceId 需要尽早设置，但 JwtAuthenticationFilter 属于自定义 Filter，无法作为“有序参考 Filter”使用
                .addFilterBefore(traceIdMdcFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 浏览器跨域：来源模式来自 {@link CorsProperties}（生产见 {@code application-prod.yml}）。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> patterns = corsProperties.getAllowedOriginPatterns();
        if (patterns == null || patterns.isEmpty()) {
            config.addAllowedOriginPattern("*");
        } else {
            for (String p : patterns) {
                if (p != null && !p.isBlank()) {
                    config.addAllowedOriginPattern(p.trim());
                }
            }
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", TraceIdMdcFilter.RESPONSE_TRACE_HEADER));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
