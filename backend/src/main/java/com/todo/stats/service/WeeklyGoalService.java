package com.todo.stats.service;

import com.todo.exception.CustomException;
import com.todo.exception.ErrorCode;
import com.todo.stats.domain.WeeklyGoal;
import com.todo.stats.domain.WeeklyStat;
import com.todo.stats.infrastructure.DailyStatRepository;
import com.todo.stats.infrastructure.WeeklyGoalRepository;
import com.todo.stats.infrastructure.WeeklyStatRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.OptionalDouble;

@Service
public class WeeklyGoalService {

    private final WeeklyGoalRepository weeklyGoalRepository;
    private final WeeklyStatRepository weeklyStatRepository;

    public WeeklyGoalService(WeeklyGoalRepository weeklyGoalRepository,
                              WeeklyStatRepository weeklyStatRepository) {
        this.weeklyGoalRepository = weeklyGoalRepository;
        this.weeklyStatRepository = weeklyStatRepository;
    }

    public WeeklyGoal getGoal(Long userId, int year, int weekNumber) {
        return weeklyGoalRepository.findByUserIdAndYearAndWeekNumber(userId, year, weekNumber)
                .orElse(null);
    }

    @Transactional
    public WeeklyGoal setGoal(Long userId, int year, int weekNumber, int goalCount) {
        validateSettingWindow(year, weekNumber);

        WeeklyGoal goal = weeklyGoalRepository
                .findByUserIdAndYearAndWeekNumber(userId, year, weekNumber)
                .orElseGet(() -> {
                    WeeklyGoal g = new WeeklyGoal();
                    g.setUserId(userId);
                    g.setYear(year);
                    g.setWeekNumber(weekNumber);
                    return g;
                });

        goal.setGoalCount(goalCount);
        return weeklyGoalRepository.save(goal);
    }

    /**
     * 목표 설정 가능 시간:
     * - 해당 주 월요일 (= 목표 주가 현재 주인 경우)
     * - 전 주 일요일 (= 목표 주가 다음 주인 경우)
     * 화요일 이후 해당 주 목표 설정 불가
     */
    private void validateSettingWindow(int targetYear, int targetWeekNumber) {
        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        int currentYear = today.get(IsoFields.WEEK_BASED_YEAR);
        int currentWeek = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        LocalDate nextWeekMonday = today.with(DayOfWeek.MONDAY).plusWeeks(1);
        int nextYear = nextWeekMonday.get(IsoFields.WEEK_BASED_YEAR);
        int nextWeek = nextWeekMonday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        boolean isCurrentWeek = targetYear == currentYear && targetWeekNumber == currentWeek;
        boolean isNextWeek = targetYear == nextYear && targetWeekNumber == nextWeek;

        if (isCurrentWeek) {
            // 현재 주 목표: 월요일에만 설정 가능
            if (dayOfWeek != DayOfWeek.MONDAY) {
                throw new CustomException(ErrorCode.GOAL_SETTING_LOCKED);
            }
        } else if (isNextWeek) {
            // 다음 주 목표: 일요일에만 미리 설정 가능
            if (dayOfWeek != DayOfWeek.SUNDAY) {
                throw new CustomException(ErrorCode.GOAL_SETTING_LOCKED);
            }
        } else {
            throw new CustomException(ErrorCode.GOAL_SETTING_LOCKED);
        }
    }

    /**
     * 최근 3주 평균 완료 수 계산 (목표 추천용)
     */
    public int getRecommendedGoal(Long userId) {
        List<WeeklyStat> recent = weeklyStatRepository.findRecentByUserId(userId, PageRequest.of(0, 3));
        if (recent.isEmpty()) return 3;

        OptionalDouble avg = recent.stream()
                .mapToInt(WeeklyStat::getCompletedCount)
                .average();
        return (int) Math.round(avg.orElse(3.0));
    }
}