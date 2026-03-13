package com.todo.service;

import com.todo.dto.LoginRequest;
import com.todo.dto.RegisterRequest;
import com.todo.dto.TokenPair;
import com.todo.entity.RefreshToken;
import com.todo.entity.User;
import com.todo.repository.RefreshTokenRepository;
import com.todo.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final long REFRESH_TOKEN_DAYS = 7L;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenPair register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user = userRepository.save(user);

        return issueTokenPair(user);
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        return issueTokenPair(user);
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        Claims claims = jwtService.validateRefreshToken(refreshToken);
        if (claims == null) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        String jti = claims.getId();
        if (!refreshTokenRepository.existsByJtiAndRevokedAtIsNull(jti)) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        Long userId = Long.parseLong(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        refreshTokenRepository.revokeByJti(jti, LocalDateTime.now());
        return issueTokenPair(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        String jti = jwtService.extractJti(refreshToken);
        if (jti != null) {
            refreshTokenRepository.revokeByJti(jti, LocalDateTime.now());
        }
    }

    private TokenPair issueTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getTokenVersion());
        String newRefreshToken = jwtService.generateRefreshToken(user.getId());

        String jti = jwtService.extractJti(newRefreshToken);
        RefreshToken entity = new RefreshToken();
        entity.setUserId(user.getId());
        entity.setJti(jti);
        entity.setExpiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_DAYS));
        refreshTokenRepository.save(entity);

        jwtService.cacheTokenVersion(user.getId(), user.getTokenVersion());

        return new TokenPair(accessToken, newRefreshToken);
    }
}
