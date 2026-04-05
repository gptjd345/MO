package com.todo.stats.controller;

import com.todo.entity.User;
import com.todo.stats.domain.WeeklyGoal;
import com.todo.stats.dto.WeeklyGoalRequest;
import com.todo.stats.service.WeeklyGoalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Map;

@RestController
@RequestMapping("/api/goals")
public class WeeklyGoalController {

    private final WeeklyGoalService weeklyGoalService;

    public WeeklyGoalController(WeeklyGoalService weeklyGoalService) {
        this.weeklyGoalService = weeklyGoalService;
    }

    @GetMapping("/weekly")
    public ResponseEntity<?> getWeeklyGoal(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer weekNumber,
            @AuthenticationPrincipal User user) {

        LocalDate now = LocalDate.now();
        int targetYear = year != null ? year : now.get(IsoFields.WEEK_BASED_YEAR);
        int targetWeek = weekNumber != null ? weekNumber : now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        WeeklyGoal goal = weeklyGoalService.getGoal(user.getId(), targetYear, targetWeek);
        int recommended = weeklyGoalService.getRecommendedGoal(user.getId());

        return ResponseEntity.ok(Map.of(
                "goal", goal != null ? goal.getGoalCount() : 0,
                "hasGoal", goal != null,
                "recommended", recommended,
                "year", targetYear,
                "weekNumber", targetWeek
        ));
    }

    @PostMapping("/weekly")
    public ResponseEntity<?> setWeeklyGoal(
            @RequestBody WeeklyGoalRequest req,
            @AuthenticationPrincipal User user) {

        WeeklyGoal saved = weeklyGoalService.setGoal(
                user.getId(), req.getYear(), req.getWeekNumber(), req.getGoalCount());

        return ResponseEntity.ok(Map.of(
                "year", saved.getYear(),
                "weekNumber", saved.getWeekNumber(),
                "goalCount", saved.getGoalCount()
        ));
    }
}