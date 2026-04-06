package com.vagent.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * U4：为每个 HTTP 请求设置 {@link MDC} 键 {@value #MDC_TRACE_ID}，并回写响应头 {@value #RESPONSE_TRACE_HEADER} 便于前端关联。
 * <p>
 * 若请求已带 {@value #REQUEST_TRACE_HEADER} / {@value #ALT_REQUEST_TRACE_HEADER}，则沿用（经空白裁剪）；否则生成 UUID。
 */
@Component
public class TraceIdMdcFilter extends OncePerRequestFilter {

    public static final String MDC_TRACE_ID = "traceId";

    /** 优先读取的入站头（与常见网关一致） */
    public static final String REQUEST_TRACE_HEADER = "X-Request-Id";

    public static final String ALT_REQUEST_TRACE_HEADER = "X-Trace-Id";

    /** 出站头：与入站 {@link #ALT_REQUEST_TRACE_HEADER} 同名，便于客户端读取 */
    public static final String RESPONSE_TRACE_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader(RESPONSE_TRACE_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String fromHeader = request.getHeader(REQUEST_TRACE_HEADER);
        if (!StringUtils.hasText(fromHeader)) {
            fromHeader = request.getHeader(ALT_REQUEST_TRACE_HEADER);
        }
        if (StringUtils.hasText(fromHeader)) {
            return fromHeader.trim();
        }
        return UUID.randomUUID().toString();
    }
}
