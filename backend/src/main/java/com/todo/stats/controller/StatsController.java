package com.todo.stats.controller;

import com.todo.entity.User;
import com.todo.stats.dto.CalendarDayResponse;
import com.todo.stats.dto.StreakResponse;
import com.todo.stats.dto.WeeklyStatResponse;
import com.todo.stats.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/calendar")
    public ResponseEntity<List<CalendarDayResponse>> getCalendar(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @AuthenticationPrincipal User user) {

        int y = year != null ? year : LocalDate.now().getYear();
        int m = month != null ? month : LocalDate.now().getMonthValue();

        return ResponseEntity.ok(statsService.getMonthlyCalendar(user.getId(), y, m));
    }

    @GetMapping("/streak")
    public ResponseEntity<StreakResponse> getStreak(@AuthenticationPrincipal User user) {
        int completed = statsService.getCurrentWeekCompleted(user.getId());
        int goal = statsService.getCurrentWeekGoal(user.getId());
        return ResponseEntity.ok(new StreakResponse(
                statsService.getStreak(user.getId()),
                completed >= goal,
                completed,
                goal));
    }

    @GetMapping("/yearly")
    public ResponseEntity<List<CalendarDayResponse>> getYearlyCalendar(
            @RequestParam(required = false) Integer year,
            @AuthenticationPrincipal User user) {

        int y = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(statsService.getYearlyCalendar(user.getId(), y));
    }

    @GetMapping("/daily")
    public ResponseEntity<List<CalendarDayResponse>> getDaily(
            @RequestParam(defaultValue = "6") int days,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(statsService.getRecentDailyStats(user.getId(), days));
    }

    @GetMapping("/weekly")
    public ResponseEntity<List<WeeklyStatResponse>> getWeekly(
            @RequestParam(defaultValue = "8") int weeks,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(statsService.getCalendarWeeklyStats(user.getId(), weeks));
    }
}