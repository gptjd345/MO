package com.todo.stats.batch;

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
import java.time.temporal.ChronoUnit;
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
    private final WeeklyStatRepository weeklyStatRepository;
    private final StreakStatRepository streakStatRepository;
    private final WeeklyGoalRepository weeklyGoalRepository;
    private final BatchMetricsPusher metricsPusher;

    @PersistenceContext
    private EntityManager entityManager;

    public DailyStatsBatchJob(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               JobLauncher jobLauncher,
                               TodoEventRepository todoEventRepository,
                               WeeklyStatRepository weeklyStatRepository,
                               StreakStatRepository streakStatRepository,
                               WeeklyGoalRepository weeklyGoalRepository,
                               BatchMetricsPusher metricsPusher) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.jobLauncher = jobLauncher;
        this.todoEventRepository = todoEventRepository;
        this.weeklyStatRepository = weeklyStatRepository;
        this.streakStatRepository = streakStatRepository;
        this.weeklyGoalRepository = weeklyGoalRepository;
        this.metricsPusher = metricsPusher;
    }

    @Scheduled(cron = "${batch.stats.cron:0 0 1 * * *}")
    public void runJob() {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLocalDateTime("runAt", LocalDateTime.now())
                    .toJobParameters();
            jobLauncher.run(statsJob(), params);
            success = true;
        } catch (Exception e) {
            log.error("DailyStatsBatchJob failed: {}", e.getMessage(), e);
        } finally {
            metricsPusher.push("dailyStatsJob", System.currentTimeMillis() - start, success);
        }
    }

    @Bean
    public Job statsJob() {
        return new JobBuilder("dailyStatsJob", jobRepository)
                .start(weeklyStatsStep())
                .next(streakStatsStep())
                .build();
    }

    // ─── Step 1: weekly_stats 재집계 ─────────────────────────────────────────

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
        LocalDate weekStart = LocalDate.now()
                .with(IsoFields.WEEK_BASED_YEAR, year)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, weekNumber)
                .with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        // todos 테이블에서 직접 해당 주 완료 건수 집계
        Long completedCount = (Long) entityManager.createQuery(
                "SELECT COUNT(t) FROM Todo t WHERE t.userId = :userId " +
                "AND t.completedAt BETWEEN :weekStart AND :weekEnd")
                .setParameter("userId", userId)
                .setParameter("weekStart", weekStart)
                .setParameter("weekEnd", weekEnd)
                .getSingleResult();

        int totalCompleted = completedCount.intValue();

        boolean achieved = weeklyGoalRepository
                .findByUserIdAndYearAndWeekNumber(userId, year, weekNumber)
                .map(goal -> totalCompleted >= goal.getGoalCount())
                .orElse(totalCompleted >= 3);

        WeeklyStat stat = weeklyStatRepository.findByUserIdAndYearAndWeekNumber(userId, year, weekNumber)
                .orElseGet(() -> {
                    WeeklyStat s = new WeeklyStat();
                    s.setUserId(userId);
                    s.setYear(year);
                    s.setWeekNumber(weekNumber);
                    return s;
                });

        stat.setCompletedCount(totalCompleted);
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

                    // freeze 중인 유저 처리 (2주 전 freeze 시작 후 아직 freeze 상태 → streak 리셋)
                    // 저번주에 달성했으면 achieved 처리 구간에서 이미 freeze 해제됨
                    // → 2주 전에 freeze 시작됐는데 아직 freeze 상태 = 저번주도 미달성 보장
                    LocalDate twoWeeksAgo = LocalDate.now().minusWeeks(2);
                    int twoWeeksAgoYear = twoWeeksAgo.get(IsoFields.WEEK_BASED_YEAR);
                    int twoWeeksAgoWeek = twoWeeksAgo.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

                    List<StreakStat> toReset = streakStatRepository
                            .findByFreezedTrueAndFreezeYearAndFreezeWeek(twoWeeksAgoYear, twoWeeksAgoWeek);

                    for (StreakStat streak : toReset) {
                        streak.setCurrentStreak(0);
                        streak.setFreezed(false);
                        streak.setFreezeWeek(null);
                        streak.setFreezeYear(null);
                        streak.setLastWeekAchieved(null);
                        streak.setLastYearAchieved(null);
                        streak.setUpdatedAt(LocalDateTime.now());
                        streakStatRepository.save(streak);
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

                        // rebuildStreakFromHistory가 이미 이 주를 반영한 경우 이중 카운팅 방지
                        if (Integer.valueOf(lastWeek).equals(streak.getLastWeekAchieved())
                                && Integer.valueOf(lastYear).equals(streak.getLastYearAchieved())) {
                            continue;
                        }

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

                    // 저번주 goalAchieved = false이고 currentStreak > 0인 유저 → freeze 부여
                    // 활동 여부와 무관하게 목표 달성 여부만으로 판단
                    List<WeeklyStat> missedStats = weeklyStatRepository
                            .findByYearAndWeekNumberAndGoalAchievedFalse(lastYear, lastWeek);

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

                    log.info("streak_stats updated: achieved={}, streakReset={}, freezeSet={}",
                            achievedStats.size(), toReset.size(), missedStats.size());
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

        WeeklyStat prev = null;

        for (WeeklyStat week : weeklyStats) {
            if (prev != null) {
                long gap = weeksBetween(prev.getYear(), prev.getWeekNumber(), week.getYear(), week.getWeekNumber());
                if (gap >= 3 || (gap >= 2 && freezed)) {
                    // 빈 주 2개 이상이거나, 이미 freeze 중에 빈 주 1개 추가 → 리셋
                    currentStreak = 0;
                    freezed = false;
                    freezeYear = null;
                    freezeWeek = null;
                    lastWeekAchieved = null;
                    lastYearAchieved = null;
                } else if (gap == 2 && currentStreak > 0) {
                    // 빈 주 1개 → freeze 유예 시작
                    freezed = true;
                    LocalDate emptyWeek = weekMonday(prev.getYear(), prev.getWeekNumber()).plusWeeks(1);
                    freezeYear = emptyWeek.get(IsoFields.WEEK_BASED_YEAR);
                    freezeWeek = emptyWeek.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                }
            }

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

            prev = week;
        }

        // 마지막 기록 이후 현재까지의 공백 체크
        if (prev != null) {
            LocalDate now = LocalDate.now();
            int currentYear = now.get(IsoFields.WEEK_BASED_YEAR);
            int currentWeek = now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            long gapToNow = weeksBetween(prev.getYear(), prev.getWeekNumber(), currentYear, currentWeek);
            if (gapToNow >= 3 || (gapToNow >= 2 && freezed)) {
                currentStreak = 0;
                freezed = false;
                freezeYear = null;
                freezeWeek = null;
                lastWeekAchieved = null;
                lastYearAchieved = null;
            } else if (gapToNow == 2 && currentStreak > 0) {
                freezed = true;
                LocalDate emptyWeek = weekMonday(prev.getYear(), prev.getWeekNumber()).plusWeeks(1);
                freezeYear = emptyWeek.get(IsoFields.WEEK_BASED_YEAR);
                freezeWeek = emptyWeek.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
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

    private LocalDate weekMonday(int year, int week) {
        return LocalDate.now()
                .with(IsoFields.WEEK_BASED_YEAR, year)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                .with(DayOfWeek.MONDAY);
    }

    private long weeksBetween(int year1, int week1, int year2, int week2) {
        return ChronoUnit.WEEKS.between(weekMonday(year1, week1), weekMonday(year2, week2));
    }
}