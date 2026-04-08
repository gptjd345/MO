package com.todo.stats.dto;

import com.todo.stats.domain.StreakStat;

public class StreakResponse {

    private final int currentStreak;
    private final int longestStreak;
    private final boolean isFreezed;
    private final Integer lastWeekAchieved;
    private final Integer lastYearAchieved;
    private final int currentWeekCompleted;
    private final int currentWeekGoal;

    public StreakResponse(StreakStat stat, boolean currentWeekActive, int currentWeekCompleted, int currentWeekGoal) {
        this.currentStreak = stat.getCurrentStreak() + (currentWeekActive ? 1 : 0);
        this.longestStreak = Math.max(stat.getLongestStreak(), this.currentStreak);
        this.isFreezed = stat.isFreezed();
        this.lastWeekAchieved = stat.getLastWeekAchieved();
        this.lastYearAchieved = stat.getLastYearAchieved();
        this.currentWeekCompleted = currentWeekCompleted;
        this.currentWeekGoal = currentWeekGoal;
    }

    public int getCurrentStreak() { return currentStreak; }
    public int getLongestStreak() { return longestStreak; }
    public boolean isFreezed() { return isFreezed; }
    public Integer getLastWeekAchieved() { return lastWeekAchieved; }
    public Integer getLastYearAchieved() { return lastYearAchieved; }
    public int getCurrentWeekCompleted() { return currentWeekCompleted; }
    public int getCurrentWeekGoal() { return currentWeekGoal; }
}