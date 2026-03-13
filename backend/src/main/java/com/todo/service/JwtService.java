package com.todo.service;

import com.todo.entity.User;
import com.todo.repository.UserRepository;
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

@Service
public class JwtService {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final SecretKey signingKey;

    private static final long ACCESS_TOKEN_MS  = 15 * 60 * 1000L;
    private static final long REFRESH_TOKEN_MS = 7 * 24 * 60 * 60 * 1000L;
    private static final String TOKEN_VERSION_PREFIX = "tv:";

    public JwtService(StringRedisTemplate redisTemplate,
                      UserRepository userRepository,
                      @Value("${jwt.secret}") String secret) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String email, int tokenVersion) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("tv", tokenVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ACCESS_TOKEN_MS))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_TOKEN_MS))
                .signWith(signingKey)
                .compact();
    }

    // access token: 서명 + 만료 검증 후 Claims 반환
    public Claims validateAccessToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    // refresh token: 서명 + 만료 검증만 (JTI DB 확인은 AuthService에서)
    public Claims validateRefreshToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public String extractJti(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getId();
        } catch (Exception e) {
            return null;
        }
    }

    // Redis 조회 → 없으면 DB fallback
    public int getTokenVersion(Long userId) {
        String key = TOKEN_VERSION_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) return Integer.parseInt(cached);

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return -1;

        int version = user.getTokenVersion();
        redisTemplate.opsForValue().set(key, String.valueOf(version));
        return version;
    }

    public void cacheTokenVersion(Long userId, int version) {
        redisTemplate.opsForValue().set(TOKEN_VERSION_PREFIX + userId, String.valueOf(version));
    }

    public void evictTokenVersionCache(Long userId) {
        redisTemplate.delete(TOKEN_VERSION_PREFIX + userId);
    }
}
