package com.todo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String plan = "FREE";

    @Column(nullable = false)
    private int score = 0;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "ranking_version", nullable = false)
    private int rankingVersion = 0;

    public User() {}

    public User(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public int getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(int tokenVersion) { this.tokenVersion = tokenVersion; }
    public int getRankingVersion() { return rankingVersion; }
    public void setRankingVersion(int rankingVersion) { this.rankingVersion = rankingVersion; }
}
