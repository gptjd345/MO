package com.todo.service;

import com.todo.dto.AuthResponse;
import com.todo.dto.LoginRequest;
import com.todo.dto.RegisterRequest;
import com.todo.dto.TokenPair;
import com.todo.entity.User;
import com.todo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public TokenPair register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user = userRepository.save(user);

        return jwtService.generateTokenPair(user.getId(), user.getEmail());
    }

    public TokenPair login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        return jwtService.generateTokenPair(user.getId(), user.getEmail());
    }

    public TokenPair refresh(String refreshToken) {
        Long userId = jwtService.validateRefreshToken(refreshToken);
        if (userId == null) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        jwtService.invalidateRefreshToken(refreshToken);
        return jwtService.generateTokenPair(user.getId(), user.getEmail());
    }

    public void logout(String refreshToken) {
        jwtService.invalidateRefreshToken(refreshToken);
    }
}
