package com.todo.service;

import com.todo.dto.ChangePasswordRequest;
import com.todo.entity.User;
import com.todo.exception.CustomException;
import com.todo.exception.ErrorCode;
import com.todo.repository.RefreshTokenRepository;
import com.todo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    // ─── changePassword ───────────────────────────────────────────────────────

    @Test
    @DisplayName("비밀번호 변경 — 현재 비밀번호가 틀리면 INVALID_CURRENT_PASSWORD")
    void changePassword_throwsInvalidCurrentPassword_whenMismatch() {
        User user = userWithPassword("encoded-pw");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pw", "encoded-pw")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(1L, request("wrong-pw", "new-password")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD);
    }

    @Test
    @DisplayName("비밀번호 변경 — 성공 시 token_version 증가, 캐시 무효화, 모든 refresh token 폐기")
    void changePassword_incrementsTokenVersion_andRevokesAllRefreshTokens() {
        User user = userWithPassword("encoded-pw");
        user.setTokenVersion(0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-pw", "encoded-pw")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-encoded-pw");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.changePassword(1L, request("current-pw", "new-password"));

        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(jwtService).evictTokenVersionCache(1L);
        verify(refreshTokenRepository).revokeAllByUserId(eq(1L), any(LocalDateTime.class));
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private User userWithPassword(String encodedPassword) {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setPassword(encodedPassword);
        user.setNickname("tester");
        return user;
    }

    private ChangePasswordRequest request(String current, String newPw) {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword(current);
        req.setNewPassword(newPw);
        return req;
    }
}