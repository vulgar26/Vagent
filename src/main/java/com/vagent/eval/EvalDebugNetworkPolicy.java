package com.vagent.eval;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * EVAL_DEBUG 明文 {@code meta.retrieval_hit_ids[]} 的网络纵深：可选 CIDR allowlist。
 *
 * <p>{@code allow-cidrs} 为空时不限制 IP；非空时客户端须落入任一 CIDR（与 {@code debug-enabled}、{@code mode=EVAL_DEBUG}、token 校验正交）。</p>
 */
@Component
public class EvalDebugNetworkPolicy implements InitializingBean {

    private final EvalApiProperties props;
    private List<IpAddressMatcher> allowMatchers = List.of();

    public EvalDebugNetworkPolicy(EvalApiProperties props) {
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        List<IpAddressMatcher> built = new ArrayList<>();
        for (String cidr : props.getAllowCidrs()) {
            if (cidr == null || cidr.isBlank()) {
                continue;
            }
            try {
                built.add(new IpAddressMatcher(cidr.trim()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid vagent.eval.api.allow-cidrs entry: " + cidr, e);
            }
        }
        this.allowMatchers = List.copyOf(built);
    }

    /**
     * 是否允许向响应写入明文命中 id（网络维度）；调用方仍须保证 debugEnabled + EVAL_DEBUG + token 已通过。
     */
    public boolean allowsPlaintextRetrievalHitIds(HttpServletRequest request) {
        if (allowMatchers.isEmpty()) {
            return true;
        }
        String ip = resolveClientIp(request);
        for (IpAddressMatcher matcher : allowMatchers) {
            if (matcher.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (props.isTrustForwardedHeaders()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                for (String part : xff.split(",")) {
                    String p = part.trim();
                    if (!p.isEmpty()) {
                        return p;
                    }
                }
            }
        }
        return request.getRemoteAddr();
    }
}
