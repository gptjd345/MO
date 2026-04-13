package com.todo.security;

import com.todo.repository.UserRepository;
import com.todo.service.JwtService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STRIDE — Tampering / Spoofing
 *
 * 서명이 유효하지 않거나 만료된 토큰으로 접근할 때
 * SecurityContext에 인증 정보가 설정되지 않는지 검증한다.
 *
 * token_version 기반 무효화(비밀번호 변경, 회원 탈퇴 등)는
 * 해당 기능 구현 후 테스트를 추가한다.
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
        // given — JwtService가 null을 반환 (서명 불일치 또는 만료)
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.or.expired.token");
        when(jwtService.validateAccessToken(any())).thenReturn(null);

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}