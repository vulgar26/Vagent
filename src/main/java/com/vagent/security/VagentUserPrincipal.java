package com.vagent.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 已登录用户在 Spring Security 上下文中的主体。
 * <p>
 * <b>作用：</b> 从 JWT 解析出 {@link #userId} 与 {@link #username}，供控制器与业务层做「当前用户」与数据隔离。
 * <p>
 * <b>为何实现 {@link UserDetails}：</b> 与 Spring Security 的 {@link org.springframework.security.core.context.SecurityContext} 模型一致，
 * 便于后续扩展角色、权限；当前仅固定 {@code ROLE_USER}。
 * <p>
 * <b>密码字段：</b> JWT 场景下不在 Principal 中携带密码，故 {@link #getPassword()} 返回 {@code null}。
 */
public class VagentUserPrincipal implements UserDetails {

    private final UUID userId;
    private final String username;

    public VagentUserPrincipal(UUID userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
