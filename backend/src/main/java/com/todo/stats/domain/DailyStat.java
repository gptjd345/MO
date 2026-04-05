package com.todo.stats.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_stats",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "stat_date"}))
public class DailyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "completed_count", nullable = false)
    private int completedCount = 0;

    @Column(name = "scheduled_count", nullable = false)
    private int scheduledCount = 0;

    @Column(name = "created_count", nullable = false)
    private int createdCount = 0;

    @Column(name = "completion_rate", nullable = false)
    private double completionRate = 0.0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public DailyStat() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }
    public int getCompletedCount() { return completedCount; }
    public void setCompletedCount(int completedCount) { this.completedCount = completedCount; }
    public int getScheduledCount() { return scheduledCount; }
    public void setScheduledCount(int scheduledCount) { this.scheduledCount = scheduledCount; }
    public int getCreatedCount() { return createdCount; }
    public void setCreatedCount(int createdCount) { this.createdCount = createdCount; }
    public double getCompletionRate() { return completionRate; }
    public void setCompletionRate(double completionRate) { this.completionRate = completionRate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}