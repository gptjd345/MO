package com.todo.dto;

public class AuthResponse {
    private String accessToken;
    private Long userId;
    private String email;
    private String nickname;
    private String plan;
    private int score;

    public AuthResponse(String accessToken, Long userId, String email, String nickname, String plan, int score) {
        this.accessToken = accessToken;
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.plan = plan;
        this.score = score;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
}
