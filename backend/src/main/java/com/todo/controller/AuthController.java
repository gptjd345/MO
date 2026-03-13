package com.todo.controller;

import com.todo.dto.*;
import com.todo.entity.User;
import com.todo.repository.UserRepository;
import com.todo.service.AuthService;
import com.todo.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final int REFRESH_MAX_AGE   = 7 * 24 * 60 * 60; // 7일(초)

    private final AuthService authService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, JwtService jwtService, UserRepository userRepository) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletResponse response) {
        try {
            TokenPair tokens = authService.register(request);
            setRefreshCookie(response, tokens.refreshToken());
            User user = userRepository.findByEmail(request.getEmail()).orElseThrow();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AuthResponse(tokens.accessToken(), user.getId(), user.getEmail(), user.getNickname(), user.getPlan(), user.getScore()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletResponse response) {
        try {
            TokenPair tokens = authService.login(request);
            setRefreshCookie(response, tokens.refreshToken());
            User user = userRepository.findByEmail(request.getEmail()).orElseThrow();
            return ResponseEntity.ok(
                    new AuthResponse(tokens.accessToken(), user.getId(), user.getEmail(), user.getNickname(), user.getPlan(), user.getScore()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Refresh token not found"));
        }
        try {
            TokenPair tokens = authService.refresh(refreshToken);
            setRefreshCookie(response, tokens.refreshToken());
            return ResponseEntity.ok(Map.of("accessToken", tokens.accessToken()));
        } catch (RuntimeException e) {
            clearRefreshCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        clearRefreshCookie(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getPlan(), user.getScore()));
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(REFRESH_MAX_AGE);
        // cookie.setSecure(true); // 프로덕션 HTTPS 환경에서 활성화
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> REFRESH_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
