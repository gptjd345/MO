package com.todo.stats.dto;

import com.todo.stats.domain.DailyStat;

import java.time.LocalDate;

public class CalendarDayResponse {

    private final LocalDate date;
    private final int completedCount;
    private final int scheduledCount;
    private final double completionRate;

    public CalendarDayResponse(DailyStat stat) {
        this.date = stat.getStatDate();
        this.completedCount = stat.getCompletedCount();
        this.scheduledCount = stat.getScheduledCount();
        this.completionRate = stat.getCompletionRate();
    }

    public LocalDate getDate() { return date; }
    public int getCompletedCount() { return completedCount; }
    public int getScheduledCount() { return scheduledCount; }
    public double getCompletionRate() { return completionRate; }
}