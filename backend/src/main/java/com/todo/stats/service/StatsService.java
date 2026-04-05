package com.todo.stats.service;

import com.todo.stats.domain.DailyStat;
import com.todo.stats.domain.StreakStat;
import com.todo.stats.domain.WeeklyStat;
import com.todo.stats.infrastructure.DailyStatRepository;
import com.todo.stats.infrastructure.StreakStatRepository;
import com.todo.stats.infrastructure.WeeklyStatRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.*;

@Service
public class StatsService {

    private final DailyStatRepository dailyStatRepository;
    private final WeeklyStatRepository weeklyStatRepository;
    private final StreakStatRepository streakStatRepository;

    public StatsService(DailyStatRepository dailyStatRepository,
                        WeeklyStatRepository weeklyStatRepository,
                        StreakStatRepository streakStatRepository) {
        this.dailyStatRepository = dailyStatRepository;
        this.weeklyStatRepository = weeklyStatRepository;
        this.streakStatRepository = streakStatRepository;
    }

    public List<DailyStat> getMonthlyCalendar(Long userId, int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        return dailyStatRepository.findByUserIdAndDateRange(userId, from, to);
    }

    public List<DailyStat> getYearlyCalendar(Long userId, int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        return dailyStatRepository.findByUserIdAndDateRange(userId, from, to);
    }

    public StreakStat getStreak(Long userId) {
        return streakStatRepository.findByUserId(userId)
                .orElseGet(() -> {
                    StreakStat s = new StreakStat();
                    s.setUserId(userId);
                    return s;
                });
    }

    public List<WeeklyStat> getRecentWeeklyStats(Long userId, int weeks) {
        return weeklyStatRepository.findRecentByUserId(userId, PageRequest.of(0, weeks));
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