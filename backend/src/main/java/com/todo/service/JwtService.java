package com.todo.service;

import com.todo.dto.TokenPair;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class JwtService {

    private final StringRedisTemplate redisTemplate;
    private final SecretKey signingKey;

    private static final long ACCESS_TOKEN_MS  = 15 * 60 * 1000L;        // 15분
    private static final long REFRESH_TOKEN_MS = 7 * 24 * 60 * 60 * 1000L; // 7일
    private static final String REFRESH_PREFIX = "refresh:";

    public JwtService(StringRedisTemplate redisTemplate,
                      @Value("${jwt.secret}") String secret) {
        this.redisTemplate = redisTemplate;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public TokenPair generateTokenPair(Long userId, String email) {
        String accessToken  = buildJwt(userId, email, ACCESS_TOKEN_MS);
        String refreshToken = buildRefreshToken(userId);
        return new TokenPair(accessToken, refreshToken);
    }

    // Access token — stateless, Redis 저장 없음
    private String buildJwt(Long userId, String email, long expirationMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    // Refresh token — opaque token, Redis에 저장
    private String buildRefreshToken(Long userId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                REFRESH_PREFIX + token,
                userId.toString(),
                REFRESH_TOKEN_MS,
                TimeUnit.MILLISECONDS
        );
        return token;
    }

    // Access token 검증 — 서명 + 만료만 확인 (stateless)
    public Long validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    // Refresh token 검증 — Redis 조회
    public Long validateRefreshToken(String token) {
        String stored = redisTemplate.opsForValue().get(REFRESH_PREFIX + token);
        if (stored == null) return null;
        return Long.parseLong(stored);
    }

    public void invalidateRefreshToken(String token) {
        redisTemplate.delete(REFRESH_PREFIX + token);
    }
}
