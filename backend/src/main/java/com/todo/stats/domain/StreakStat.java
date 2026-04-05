package com.todo.stats.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "streak_stats")
public class StreakStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak = 0;

    @Column(name = "last_week_achieved")
    private Integer lastWeekAchieved;

    @Column(name = "last_year_achieved")
    private Integer lastYearAchieved;

    @Column(name = "is_freezed", nullable = false)
    private boolean isFreezed = false;

    @Column(name = "freeze_week")
    private Integer freezeWeek;

    @Column(name = "freeze_year")
    private Integer freezeYear;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public StreakStat() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }
    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }
    public Integer getLastWeekAchieved() { return lastWeekAchieved; }
    public void setLastWeekAchieved(Integer lastWeekAchieved) { this.lastWeekAchieved = lastWeekAchieved; }
    public Integer getLastYearAchieved() { return lastYearAchieved; }
    public void setLastYearAchieved(Integer lastYearAchieved) { this.lastYearAchieved = lastYearAchieved; }
    public boolean isFreezed() { return isFreezed; }
    public void setFreezed(boolean freezed) { this.isFreezed = freezed; }
    public Integer getFreezeWeek() { return freezeWeek; }
    public void setFreezeWeek(Integer freezeWeek) { this.freezeWeek = freezeWeek; }
    public Integer getFreezeYear() { return freezeYear; }
    public void setFreezeYear(Integer freezeYear) { this.freezeYear = freezeYear; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}