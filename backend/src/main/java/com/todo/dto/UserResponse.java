package com.todo.dto;

public class UserResponse {
    private Long id;
    private String email;
    private String plan;

    public UserResponse(Long id, String email, String plan) {
        this.id = id;
        this.email = email;
        this.plan = plan;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
}
