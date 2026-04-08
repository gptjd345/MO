package com.todo.stats.service;

import com.todo.repository.TodoRepository;
import com.todo.stats.domain.StreakStat;
import com.todo.stats.domain.WeeklyStat;
import com.todo.stats.dto.CalendarDayResponse;
import com.todo.stats.infrastructure.StreakStatRepository;
import com.todo.stats.infrastructure.TodoEventRepository;
import com.todo.stats.infrastructure.WeeklyGoalRepository;
import com.todo.stats.infrastructure.WeeklyStatRepository;
import com.todo.stats.dto.WeeklyStatResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.*;

@Service
public class StatsService {

    private final TodoRepository todoRepository;
    private final WeeklyStatRepository weeklyStatRepository;
    private final StreakStatRepository streakStatRepository;
    private final TodoEventRepository todoEventRepository;
    private final WeeklyGoalRepository weeklyGoalRepository;

    public StatsService(TodoRepository todoRepository,
                        WeeklyStatRepository weeklyStatRepository,
                        StreakStatRepository streakStatRepository,
                        TodoEventRepository todoEventRepository,
                        WeeklyGoalRepository weeklyGoalRepository) {
        this.todoRepository = todoRepository;
        this.weeklyStatRepository = weeklyStatRepository;
        this.streakStatRepository = streakStatRepository;
        this.todoEventRepository = todoEventRepository;
        this.weeklyGoalRepository = weeklyGoalRepository;
    }

    public List<CalendarDayResponse> getMonthlyCalendar(Long userId, int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        return toCalendarDayResponses(userId, from, to);
    }

    public List<CalendarDayResponse> getYearlyCalendar(Long userId, int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        return toCalendarDayResponses(userId, from, to);
    }

    private List<CalendarDayResponse> toCalendarDayResponses(Long userId, LocalDate from, LocalDate to) {
        return todoRepository.countCompletedByDateBetween(userId, from, to).stream()
                .map(row -> new CalendarDayResponse((LocalDate) row[0], ((Long) row[1]).intValue()))
                .toList();
    }

    public StreakStat getStreak(Long userId) {
        return streakStatRepository.findByUserId(userId)
                .orElseGet(() -> {
                    StreakStat s = new StreakStat();
                    s.setUserId(userId);
                    return s;
                });
    }

    public int getCurrentWeekCompleted(Long userId) {
        LocalDate now = LocalDate.now();
        LocalDate weekStart = now.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = now.with(DayOfWeek.SUNDAY);
        return (int) todoRepository.countByUserIdAndCompletedTrueAndCompletedAtBetween(userId, weekStart, weekEnd);
    }

    public int getCurrentWeekGoal(Long userId) {
        LocalDate now = LocalDate.now();
        int year = now.get(IsoFields.WEEK_BASED_YEAR);
        int week = now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return weeklyGoalRepository.findByUserIdAndYearAndWeekNumber(userId, year, week)
                .map(g -> g.getGoalCount())
                .orElse(3);
    }

    public boolean isCurrentWeekGoalAchieved(Long userId) {
        return getCurrentWeekCompleted(userId) >= getCurrentWeekGoal(userId);
    }

    public List<CalendarDayResponse> getRecentDailyStats(Long userId, int days) {
        LocalDate from = LocalDate.now().minusDays(days);
        LocalDate to = LocalDate.now().minusDays(1);
        return toCalendarDayResponses(userId, from, to);
    }

    public List<WeeklyStat> getRecentWeeklyStats(Long userId, int weeks) {
        return weeklyStatRepository.findRecentByUserId(userId, PageRequest.of(0, weeks));
    }

    /**
     * 오늘 기준으로 정확히 {@code weeks}개의 캘린더 주 슬롯을 생성하고,
     * weekly_stats 데이터가 없는 주는 목표 미달성(goalAchieved=false)으로 채워 반환한다.
     * DB에 행이 있는 주만 반환하는 findRecentByUserId와 달리,
     * 분모를 항상 weeks로 고정할 수 있어 "최근 N주 목표달성률" 계산에 사용한다.
     */
    public List<WeeklyStatResponse> getCalendarWeeklyStats(Long userId, int weeks) {
        LocalDate today = LocalDate.now();
        List<WeeklyStatResponse> result = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            LocalDate weekDate = today.minusWeeks(i);
            int y = weekDate.get(IsoFields.WEEK_BASED_YEAR);
            int w = weekDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            WeeklyStatResponse slot = weeklyStatRepository
                    .findByUserIdAndYearAndWeekNumber(userId, y, w)
                    .map(WeeklyStatResponse::new)
                    .orElse(new WeeklyStatResponse(y, w));
            result.add(slot);
        }
        return result;
    }

    public Map<String, Object> getWeeklySummary(Long userId, int weeks) {
        List<WeeklyStat> stats = getRecentWeeklyStats(userId, weeks);
        LocalDate now = LocalDate.now();
        int currentYear = now.get(IsoFields.WEEK_BASED_YEAR);
        int currentWeek = now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weeks", stats);
        result.put("currentYear", currentYear);
        result.put("currentWeek", currentWeek);
        return result;
    }
}