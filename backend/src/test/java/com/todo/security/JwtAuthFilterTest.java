package com.todo.security;

import com.todo.repository.UserRepository;
import com.todo.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STRIDE — Tampering / Spoofing / Elevation of Privilege
 *
 * 서명이 유효하지 않거나 만료된 토큰, 또는 token_version이 무효화된 토큰으로
 * 접근할 때 SecurityContext에 인증 정보가 설정되지 않는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("[Tampering/Spoofing] 서명 검증 실패 시 인증이 설정되지 않는다")
    void shouldNotSetAuthentication_whenSignatureVerificationFails() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.or.expired.token");
        when(jwtService.validateAccessToken(any())).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("[Token Version] 비밀번호 변경 후 구버전 토큰(tv=0)은 인증이 설정되지 않는다")
    void shouldNotSetAuthentication_whenTokenVersionIsOutdated() throws Exception {
        // given — tv=0인 토큰이지만 현재 사용자의 token_version은 1 (비밀번호 변경 후)
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("tv", Integer.class)).thenReturn(0);
        when(request.getHeader("Authorization")).thenReturn("Bearer some.valid.token");
        when(jwtService.validateAccessToken(any())).thenReturn(claims);
        when(jwtService.getTokenVersion(1L)).thenReturn(1);

        filter.doFilterInternal(request, response, filterChain);

        // then — tv 불일치로 인증 설정 안 됨
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}