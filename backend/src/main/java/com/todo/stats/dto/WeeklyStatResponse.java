package com.todo.stats.dto;

import com.todo.stats.domain.WeeklyStat;

public class WeeklyStatResponse {

    private final int year;
    private final int weekNumber;
    private final int completedCount;
    private final int scheduledCount;
    private final double completionRate;
    private final boolean goalAchieved;

    public WeeklyStatResponse(WeeklyStat stat) {
        this.year = stat.getYear();
        this.weekNumber = stat.getWeekNumber();
        this.completedCount = stat.getCompletedCount();
        this.scheduledCount = stat.getScheduledCount();
        this.completionRate = stat.getCompletionRate();
        this.goalAchieved = stat.isGoalAchieved();
    }

    /** weekly_stats 행이 없는 주 — 목표 미달성으로 처리 */
    public WeeklyStatResponse(int year, int weekNumber) {
        this.year = year;
        this.weekNumber = weekNumber;
        this.completedCount = 0;
        this.scheduledCount = 0;
        this.completionRate = 0.0;
        this.goalAchieved = false;
    }

    public int getYear() { return year; }
    public int getWeekNumber() { return weekNumber; }
    public int getCompletedCount() { return completedCount; }
    public int getScheduledCount() { return scheduledCount; }
    public double getCompletionRate() { return completionRate; }
    public boolean isGoalAchieved() { return goalAchieved; }
}