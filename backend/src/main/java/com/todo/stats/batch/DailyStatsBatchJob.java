package com.todo.stats.batch;

import com.todo.stats.domain.DailyStat;
import com.todo.stats.domain.StreakStat;
import com.todo.stats.domain.TodoEvent;
import com.todo.stats.domain.WeeklyStat;
import com.todo.stats.infrastructure.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

@Configuration
public class DailyStatsBatchJob {

    private static final Logger log = LoggerFactory.getLogger(DailyStatsBatchJob.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobLauncher jobLauncher;
    private final TodoEventRepository todoEventRepository;
    private final DailyStatRepository dailyStatRepository;
    private final WeeklyStatRepository weeklyStatRepository;
    private final StreakStatRepository streakStatRepository;
    private final WeeklyGoalRepository weeklyGoalRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public DailyStatsBatchJob(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               JobLauncher jobLauncher,
                               TodoEventRepository todoEventRepository,
                               DailyStatRepository dailyStatRepository,
                               WeeklyStatRepository weeklyStatRepository,
                               StreakStatRepository streakStatRepository,
                               WeeklyGoalRepository weeklyGoalRepository) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.jobLauncher = jobLauncher;
        this.todoEventRepository = todoEventRepository;
        this.dailyStatRepository = dailyStatRepository;
        this.weeklyStatRepository = weeklyStatRepository;
        this.streakStatRepository = streakStatRepository;
        this.weeklyGoalRepository = weeklyGoalRepository;
    }

    @Scheduled(cron = "${batch.stats.cron:0 0 1 * * *}")
    public void runJob() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLocalDateTime("runAt", LocalDateTime.now())
                    .toJobParameters();
            jobLauncher.run(statsJob(), params);
        } catch (Exception e) {
            log.error("DailyStatsBatchJob failed: {}", e.getMessage(), e);
        }
    }

    @Bean
    public Job statsJob() {
        return new JobBuilder("dailyStatsJob", jobRepository)
                .start(dailyStatsStep())
                .next(weeklyStatsStep())
                .next(streakStatsStep())
                .build();
    }

    // ─── Step 1: daily_stats 재집계 ───────────────────────────────────────────

    @Bean
    public Step dailyStatsStep() {
        return new StepBuilder("dailyStatsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    LocalDate yesterday = LocalDate.now().minusDays(1);
                    LocalDateTime from = yesterday.atStartOfDay();
                    LocalDateTime to = LocalDate.now().atStartOfDay();

                    List<TodoEvent> events = todoEventRepository.findByCreatedAtBetween(from, to);
                    if (events.isEmpty()) return RepeatStatus.FINISHED;

                    // 영향받는 (userId, statDate) 쌍 수집
                    Set<String> pairs = events.stream()
                            .map(e -> e.getUserId() + ":" + e.getEventDate())
                            .collect(Collectors.toSet());

                    for (String pair : pairs) {
                        String[] parts = pair.split(":");
                        Long userId = Long.parseLong(parts[0]);
                        LocalDate statDate = LocalDate.parse(parts[1]);
                        recomputeDailyStat(userId, statDate);
                    }

                    log.info("daily_stats recomputed for {} (userId, date) pairs", pairs.size());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    public void recomputeDailyStat(Long userId, LocalDate date) {
        // 분자: date에 완료한 todo 수
        Long completedCount = (Long) entityManager.createQuery(
                "SELECT COUNT(t) FROM Todo t WHERE t.userId = :userId AND t.completedAt = :date")
                .setParameter("userId", userId)
                .setParameter("date", date)
                .getSingleResult();

        // 분모: (마감일 = date이고 미완료) + (마감일 != date이고 date에 완료)
        Long scheduledCount = (Long) entityManager.createQuery(
                "SELECT COUNT(t) FROM Todo t WHERE t.userId = :userId AND (" +
                "  (t.endDate = :date AND t.completedAt IS NULL) OR " +
                "  (t.completedAt = :date AND (t.endDate IS NULL OR t.endDate != :date))" +
                ")")
                .setParameter("userId", userId)
                .setParameter("date", date)
                .getSingleResult();

        // 마감일 = date인데 date에 완료한 경우는 분모에 한 번만 들어가야 함 (위 쿼리에서 두 번 카운트되지 않음)
        // 단, 마감일 = date이고 date에 완료한 경우를 분모에 추가
        Long onTimeDoneCount = (Long) entityManager.createQuery(
                "SELECT COUNT(t) FROM Todo t WHERE t.userId = :userId " +
                "AND t.endDate = :date AND t.completedAt = :date")
                .setParameter("userId", userId)
                .setParameter("date", date)
                .getSingleResult();

        long totalScheduled = scheduledCount + onTimeDoneCount;
        double rate = totalScheduled > 0 ? (double) completedCount / totalScheduled : 0.0;

        DailyStat stat = dailyStatRepository.findByUserIdAndStatDate(userId, date)
                .orElseGet(() -> {
                    DailyStat s = new DailyStat();
                    s.setUserId(userId);
                    s.setStatDate(date);
                    return s;
                });

        stat.setCompletedCount(completedCount.intValue());
        stat.setScheduledCount((int) totalScheduled);
        stat.setCompletionRate(rate);
        dailyStatRepository.save(stat);
    }

    // ─── Step 2: weekly_stats 재집계 ─────────────────────────────────────────

    @Bean
    public Step weeklyStatsStep() {
        return new StepBuilder("weeklyStatsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    LocalDate yesterday = LocalDate.now().minusDays(1);
                    LocalDateTime from = yesterday.atStartOfDay();
                    LocalDateTime to = LocalDate.now().atStartOfDay();

                    List<TodoEvent> events = todoEventRepository.findByCreatedAtBetween(from, to);
                    if (events.isEmpty()) return RepeatStatus.FINISHED;

                    // 영향받는 (userId, year, weekNumber) 쌍 수집
                    Set<String> triples = events.stream()
                            .map(e -> {
                                LocalDate d = e.getEventDate();
                                int year = d.get(IsoFields.WEEK_BASED_YEAR);
                                int week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                                return e.getUserId() + ":" + year + ":" + week;
                            })
                            .collect(Collectors.toSet());

                    for (String triple : triples) {
                        String[] parts = triple.split(":");
                        Long userId = Long.parseLong(parts[0]);
                        int year = Integer.parseInt(parts[1]);
                        int weekNumber = Integer.parseInt(parts[2]);
                        recomputeWeeklyStat(userId, year, weekNumber);
                    }

                    log.info("weekly_stats recomputed for {} (userId, year, week) triples", triples.size());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    public void recomputeWeeklyStat(Long userId, int year, int weekNumber) {
        // 해당 주의 daily_stats 합산
        // ISO 주: 월요일 시작
        LocalDate weekStart = LocalDate.now()
                .with(IsoFields.WEEK_BASED_YEAR, year)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, weekNumber)
                .with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<DailyStat> dailyStats = dailyStatRepository.findByUserIdAndDateRange(userId, weekStart, weekEnd);

        int totalCompleted = dailyStats.stream().mapToInt(DailyStat::getCompletedCount).sum();
        int totalScheduled = dailyStats.stream().mapToInt(DailyStat::getScheduledCount).sum();
        double rate = totalScheduled > 0 ? (double) totalCompleted / totalScheduled : 0.0;

        WeeklyStat stat = weeklyStatRepository.findByUserIdAndYearAndWeekNumber(userId, year, weekNumber)
                .orElseGet(() -> {
                    WeeklyStat s = new WeeklyStat();
                    s.setUserId(userId);
                    s.setYear(year);
                    s.setWeekNumber(weekNumber);
                    return s;
                });

        stat.setCompletedCount(totalCompleted);
        stat.setScheduledCount(totalScheduled);
        stat.setCompletionRate(rate);

        // goal_achieved 체크
        boolean achieved = weeklyGoalRepository
                .findByUserIdAndYearAndWeekNumber(userId, year, weekNumber)
                .map(goal -> totalCompleted >= goal.getGoalCount())
                .orElse(false);
        stat.setGoalAchieved(achieved);
        stat.setUpdatedAt(LocalDateTime.now());
        weeklyStatRepository.save(stat);
    }

    // ─── Step 3: streak_stats 업데이트 (월요일만 실행) ───────────────────────

    @Bean
    public Step streakStatsStep() {
        return new StepBuilder("streakStatsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    if (LocalDate.now().getDayOfWeek() != DayOfWeek.MONDAY) {
                        return RepeatStatus.FINISHED;
                    }

                    // 직전 주 계산
                    LocalDate lastWeekDate = LocalDate.now().minusWeeks(1);
                    int lastYear = lastWeekDate.get(IsoFields.WEEK_BASED_YEAR);
                    int lastWeek = lastWeekDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

                    // freeze 중인 유저 처리 (2주 연속 미달성 → streak 리셋)
                    List<StreakStat> freezedStats = streakStatRepository.findAllFreezed();
                    for (StreakStat streak : freezedStats) {
                        if (streak.getFreezeYear() == null) continue;

                        boolean isSecondMiss = streak.getFreezeYear() == lastYear
                                && streak.getFreezeWeek() == lastWeek;
                        if (isSecondMiss) {
                            // 2주 연속 미달성 → streak 리셋
                            streak.setCurrentStreak(0);
                            streak.setFreezed(false);
                            streak.setFreezeWeek(null);
                            streak.setFreezeYear(null);
                            streak.setLastWeekAchieved(null);
                            streak.setLastYearAchieved(null);
                            streak.setUpdatedAt(LocalDateTime.now());
                            streakStatRepository.save(streak);
                        }
                        // freeze 1주차 → 유지 (아무것도 안 함)
                    }

                    // 직전 주에 goal_achieved한 유저 처리
                    List<WeeklyStat> achievedStats = weeklyStatRepository
                            .findAll().stream()
                            .filter(w -> w.getYear() == lastYear
                                    && w.getWeekNumber() == lastWeek
                                    && w.isGoalAchieved())
                            .toList();

                    for (WeeklyStat weekly : achievedStats) {
                        Long userId = weekly.getUserId();
                        StreakStat streak = streakStatRepository.findByUserId(userId)
                                .orElseGet(() -> {
                                    StreakStat s = new StreakStat();
                                    s.setUserId(userId);
                                    return s;
                                });

                        streak.setCurrentStreak(streak.getCurrentStreak() + 1);
                        if (streak.getCurrentStreak() > streak.getLongestStreak()) {
                            streak.setLongestStreak(streak.getCurrentStreak());
                        }
                        streak.setFreezed(false);
                        streak.setFreezeWeek(null);
                        streak.setFreezeYear(null);
                        streak.setLastWeekAchieved(lastWeek);
                        streak.setLastYearAchieved(lastYear);
                        streak.setUpdatedAt(LocalDateTime.now());
                        streakStatRepository.save(streak);
                    }

                    // 직전 주에 활동은 있었지만 goal_achieved 못한 유저 (freeze 시작)
                    List<Long> achievedUserIds = achievedStats.stream()
                            .map(WeeklyStat::getUserId).toList();

                    List<WeeklyStat> missedStats = weeklyStatRepository
                            .findAll().stream()
                            .filter(w -> w.getYear() == lastYear
                                    && w.getWeekNumber() == lastWeek
                                    && !w.isGoalAchieved()
                                    && w.getCompletedCount() > 0)
                            .filter(w -> !achievedUserIds.contains(w.getUserId()))
                            .toList();

                    for (WeeklyStat weekly : missedStats) {
                        Long userId = weekly.getUserId();
                        StreakStat streak = streakStatRepository.findByUserId(userId).orElse(null);
                        if (streak == null || streak.getCurrentStreak() == 0) continue;

                        if (!streak.isFreezed()) {
                            streak.setFreezed(true);
                            streak.setFreezeWeek(lastWeek);
                            streak.setFreezeYear(lastYear);
                            streak.setUpdatedAt(LocalDateTime.now());
                            streakStatRepository.save(streak);
                        }
                    }

                    log.info("streak_stats updated: achieved={}, freezeProcessed={}",
                            achievedStats.size(), freezedStats.size());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    public void rebuildStreakFromHistory(Long userId) {
        List<WeeklyStat> weeklyStats = weeklyStatRepository.findByUserIdOrderByYearAscWeekNumberAsc(userId);

        int currentStreak = 0;
        int longestStreak = 0;
        boolean freezed = false;
        Integer freezeYear = null;
        Integer freezeWeek = null;
        Integer lastWeekAchieved = null;
        Integer lastYearAchieved = null;

        for (WeeklyStat week : weeklyStats) {
            if (week.isGoalAchieved()) {
                currentStreak++;
                if (currentStreak > longestStreak) longestStreak = currentStreak;
                freezed = false;
                freezeYear = null;
                freezeWeek = null;
                lastWeekAchieved = week.getWeekNumber();
                lastYearAchieved = week.getYear();
            } else if (week.getCompletedCount() > 0) {
                if (!freezed) {
                    freezed = true;
                    freezeYear = week.getYear();
                    freezeWeek = week.getWeekNumber();
                } else {
                    currentStreak = 0;
                    freezed = false;
                    freezeYear = null;
                    freezeWeek = null;
                    lastWeekAchieved = null;
                    lastYearAchieved = null;
                }
            }
        }

        StreakStat stat = streakStatRepository.findByUserId(userId).orElseGet(() -> {
            StreakStat s = new StreakStat();
            s.setUserId(userId);
            return s;
        });
        stat.setCurrentStreak(currentStreak);
        stat.setLongestStreak(longestStreak);
        stat.setFreezed(freezed);
        stat.setFreezeYear(freezeYear);
        stat.setFreezeWeek(freezeWeek);
        stat.setLastWeekAchieved(lastWeekAchieved);
        stat.setLastYearAchieved(lastYearAchieved);
        stat.setUpdatedAt(LocalDateTime.now());
        streakStatRepository.save(stat);
        log.info("streak rebuilt for userId={}: currentStreak={}, longestStreak={}", userId, currentStreak, longestStreak);
    }
}