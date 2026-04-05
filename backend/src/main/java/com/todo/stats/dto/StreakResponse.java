package com.todo.stats.dto;

import com.todo.stats.domain.StreakStat;

public class StreakResponse {

    private final int currentStreak;
    private final int longestStreak;
    private final boolean isFreezed;
    private final Integer lastWeekAchieved;
    private final Integer lastYearAchieved;

    public StreakResponse(StreakStat stat) {
        this.currentStreak = stat.getCurrentStreak();
        this.longestStreak = stat.getLongestStreak();
        this.isFreezed = stat.isFreezed();
        this.lastWeekAchieved = stat.getLastWeekAchieved();
        this.lastYearAchieved = stat.getLastYearAchieved();
    }

    public int getCurrentStreak() { return currentStreak; }
    public int getLongestStreak() { return longestStreak; }
    public boolean isFreezed() { return isFreezed; }
    public Integer getLastWeekAchieved() { return lastWeekAchieved; }
    public Integer getLastYearAchieved() { return lastYearAchieved; }
}