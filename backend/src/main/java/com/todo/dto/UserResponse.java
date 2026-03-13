package com.todo.dto;

public class UserResponse {
    private Long id;
    private String email;
    private String nickname;
    private String plan;
    private int score;

    public UserResponse(Long id, String email, String nickname, String plan, int score) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
        this.plan = plan;
        this.score = score;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
}
