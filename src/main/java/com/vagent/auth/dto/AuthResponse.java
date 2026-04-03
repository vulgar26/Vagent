package com.vagent.auth.dto;

/**
 * 登录/注册成功后的令牌响应（客户端保存 token，后续请求带 Authorization）。
 */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String userId
) {
}
