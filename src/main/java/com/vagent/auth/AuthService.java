package com.vagent.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vagent.api.DuplicateUsernameException;
import com.vagent.auth.dto.AuthResponse;
import com.vagent.auth.dto.LoginRequest;
import com.vagent.auth.dto.RegisterRequest;
import com.vagent.security.JwtProperties;
import com.vagent.security.JwtService;
import com.vagent.user.User;
import com.vagent.user.UserMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 注册与登录业务：写用户表、校验密码、签发 JWT。
 * <p>
 * <b>持久化：</b> 经 {@link UserMapper}（MyBatis-Plus），数据落在 PostgreSQL。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        long exists = userMapper.selectCount(Wrappers.lambdaQuery(User.class).eq(User::getUsername, username));
        if (exists > 0) {
            throw new DuplicateUsernameException(username);
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        userMapper.insert(user);
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String username = request.getUsername().trim();
        User user = userMapper.selectOne(Wrappers.lambdaQuery(User.class).eq(User::getUsername, username));
        if (user == null) {
            throw new BadCredentialsException("invalid");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("invalid");
        }
        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtService.createAccessToken(user);
        return new AuthResponse(
                token,
                "Bearer",
                jwtProperties.getExpirationSeconds(),
                user.getId());
    }
}
