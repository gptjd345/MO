package com.todo.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.todo.repository.UserRepository;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STRIDE — Tampering / Spoofing
 *
 * JWT 서명 검증과 만료 검증이 실제로 동작하는지 확인한다.
 * validateAccessToken()이 null을 반환하면 JwtAuthFilter는 SecurityContext를 설정하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    // JWT secret은 HMAC-SHA256 기준 최소 256bit(32바이트) 이상이어야 한다
    private static final String SECRET = "test-secret-key-must-be-32chars!!";
    private static final String OTHER_SECRET = "other-secret-key-must-be-32chars!";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private UserRepository userRepository;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(redisTemplate, userRepository, SECRET);
    }

    @Test
    @DisplayName("[Tampering] 다른 키로 서명된 토큰은 null을 반환한다")
    void shouldReturnNull_whenTokenSignedWithDifferentKey() {
        // given — 공격자가 자신의 키로 서명한 토큰 (payload 변조 포함)
        SecretKey attackerKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
        String tampered = Jwts.builder()
                .subject("999")               // 임의의 userId
                .claim("tv", 0)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(attackerKey)
                .compact();

        // when
        Claims result = jwtService.validateAccessToken(tampered);

        // then — 서버 키와 서명이 다르므로 검증 실패
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("[Spoofing] 만료된 토큰은 null을 반환한다")
    void shouldReturnNull_whenTokenIsExpired() {
        // given — 이미 만료된 토큰 (expiration이 과거)
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .subject("1")
                .claim("tv", 0)
                .issuedAt(new Date(System.currentTimeMillis() - 60_000))
                .expiration(new Date(System.currentTimeMillis() - 1))  // 이미 만료
                .signWith(key)
                .compact();

        // when
        Claims result = jwtService.validateAccessToken(expired);

        // then — 만료된 토큰은 검증 실패
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("유효한 토큰은 Claims를 반환한다")
    void shouldReturnClaims_whenTokenIsValid() {
        // given
        String token = jwtService.generateAccessToken(1L, "user@test.com", 0);

        // when
        Claims result = jwtService.validateAccessToken(token);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getSubject()).isEqualTo("1");
    }
}