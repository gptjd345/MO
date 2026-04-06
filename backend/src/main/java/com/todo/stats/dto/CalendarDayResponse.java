package com.todo.stats.dto;

import java.time.LocalDate;

public class CalendarDayResponse {

    private final LocalDate date;
    private final int completedCount;

    public CalendarDayResponse(LocalDate date, int completedCount) {
        this.date = date;
        this.completedCount = completedCount;
    }

    public LocalDate getDate() { return date; }
    public int getCompletedCount() { return completedCount; }
}