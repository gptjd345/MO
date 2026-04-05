package com.todo.stats.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "weekly_stats",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "year", "week_number"}))
public class WeeklyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int year;

    @Column(name = "week_number", nullable = false)
    private int weekNumber;

    @Column(name = "completed_count", nullable = false)
    private int completedCount = 0;

    @Column(name = "scheduled_count", nullable = false)
    private int scheduledCount = 0;

    @Column(name = "created_count", nullable = false)
    private int createdCount = 0;

    @Column(name = "completion_rate", nullable = false)
    private double completionRate = 0.0;

    @Column(name = "goal_achieved", nullable = false)
    private boolean goalAchieved = false;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public WeeklyStat() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public int getWeekNumber() { return weekNumber; }
    public void setWeekNumber(int weekNumber) { this.weekNumber = weekNumber; }
    public int getCompletedCount() { return completedCount; }
    public void setCompletedCount(int completedCount) { this.completedCount = completedCount; }
    public int getScheduledCount() { return scheduledCount; }
    public void setScheduledCount(int scheduledCount) { this.scheduledCount = scheduledCount; }
    public int getCreatedCount() { return createdCount; }
    public void setCreatedCount(int createdCount) { this.createdCount = createdCount; }
    public double getCompletionRate() { return completionRate; }
    public void setCompletionRate(double completionRate) { this.completionRate = completionRate; }
    public boolean isGoalAchieved() { return goalAchieved; }
    public void setGoalAchieved(boolean goalAchieved) { this.goalAchieved = goalAchieved; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}