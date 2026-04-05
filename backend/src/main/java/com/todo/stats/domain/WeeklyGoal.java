package com.todo.stats.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "weekly_goals",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "year", "week_number"}))
public class WeeklyGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int year;

    @Column(name = "week_number", nullable = false)
    private int weekNumber;

    @Column(name = "goal_count", nullable = false)
    private int goalCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public WeeklyGoal() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public int getWeekNumber() { return weekNumber; }
    public void setWeekNumber(int weekNumber) { this.weekNumber = weekNumber; }
    public int getGoalCount() { return goalCount; }
    public void setGoalCount(int goalCount) { this.goalCount = goalCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}