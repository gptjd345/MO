package com.todo.dto;

public class AuthResponse {
    private String accessToken;
    private Long userId;
    private String email;
    private String plan;

    public AuthResponse(String accessToken, Long userId, String email, String plan) {
        this.accessToken = accessToken;
        this.userId = userId;
        this.email = email;
        this.plan = plan;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
}
