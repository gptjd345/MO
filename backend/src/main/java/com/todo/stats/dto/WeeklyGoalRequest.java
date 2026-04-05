package com.todo.stats.dto;

public class WeeklyGoalRequest {

    private int year;
    private int weekNumber;
    private int goalCount;

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public int getWeekNumber() { return weekNumber; }
    public void setWeekNumber(int weekNumber) { this.weekNumber = weekNumber; }
    public int getGoalCount() { return goalCount; }
    public void setGoalCount(int goalCount) { this.goalCount = goalCount; }
}