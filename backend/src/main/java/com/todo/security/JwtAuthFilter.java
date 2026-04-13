package com.todo.security;

import com.todo.entity.User;
import com.todo.repository.UserRepository;
import com.todo.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                Claims claims = jwtService.validateAccessToken(token);

                if (claims != null) {
                    Long userId = Long.parseLong(claims.getSubject());
                    Integer tv = claims.get("tv", Integer.class);
                    int currentVersion = jwtService.getTokenVersion(userId);

                    if (tv != null && tv == currentVersion) {
                        User user = userRepository.findById(userId).orElse(null);
                        if (user != null) {
                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 인증 처리 중 예외 발생 시 인증 없이 계속 진행
            // 보호된 엔드포인트는 Spring Security가 401로 처리
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
