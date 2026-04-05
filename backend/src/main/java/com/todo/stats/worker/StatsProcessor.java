package com.todo.stats.worker;

import com.todo.stats.domain.DailyStat;
import com.todo.stats.domain.WeeklyStat;
import com.todo.stats.infrastructure.DailyStatRepository;
import com.todo.stats.infrastructure.StatsStreamPublisher;
import com.todo.stats.infrastructure.WeeklyGoalRepository;
import com.todo.stats.infrastructure.WeeklyStatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Map;

@Component
public class StatsProcessor {

    private static final Logger log = LoggerFactory.getLogger(StatsProcessor.class);

    private final DailyStatRepository dailyStatRepository;
    private final WeeklyStatRepository weeklyStatRepository;
    private final WeeklyGoalRepository weeklyGoalRepository;
    private final StringRedisTemplate redisTemplate;

    public StatsProcessor(DailyStatRepository dailyStatRepository,
                          WeeklyStatRepository weeklyStatRepository,
                          WeeklyGoalRepository weeklyGoalRepository,
                          StringRedisTemplate redisTemplate) {
        this.dailyStatRepository = dailyStatRepository;
        this.weeklyStatRepository = weeklyStatRepository;
        this.weeklyGoalRepository = weeklyGoalRepository;
        this.redisTemplate = redisTemplate;
    }

    public void processDailyStats(MapRecord<String, Object, Object> message) {
        try {
            Map<Object, Object> body = message.getValue();
            Object rawType = body.get("eventType");
            if (rawType == null || !"COMPLETED".equals(rawType.toString())) return;

            Long userId = Long.parseLong(body.get("userId").toString());
            LocalDate eventDate = LocalDate.parse(body.get("eventDate").toString());

            DailyStat stat = dailyStatRepository.findByUserIdAndStatDate(userId, eventDate)
                    .orElseGet(() -> {
                        DailyStat s = new DailyStat();
                        s.setUserId(userId);
                        s.setStatDate(eventDate);
                        return s;
                    });

            stat.setCompletedCount(stat.getCompletedCount() + 1);
            stat.setScheduledCount(stat.getScheduledCount() + 1);
            if (stat.getScheduledCount() > 0) {
                stat.setCompletionRate((double) stat.getCompletedCount() / stat.getScheduledCount());
            }
            dailyStatRepository.save(stat);

        } catch (Exception e) {
            log.error("Failed to process daily stats messageId={}", message.getId(), e);
        } finally {
            redisTemplate.opsForStream().acknowledge(
                    message.getStream(), StatsStreamPublisher.STATS_GROUP, message.getId());
        }
    }

    public void processWeeklyStats(MapRecord<String, Object, Object> message) {
        try {
            Map<Object, Object> body = message.getValue();
            Object rawType = body.get("eventType");
            if (rawType == null || !"COMPLETED".equals(rawType.toString())) return;

            Long userId = Long.parseLong(body.get("userId").toString());
            LocalDate eventDate = LocalDate.parse(body.get("eventDate").toString());

            int year = eventDate.get(IsoFields.WEEK_BASED_YEAR);
            int weekNumber = eventDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

            WeeklyStat stat = weeklyStatRepository.findByUserIdAndYearAndWeekNumber(userId, year, weekNumber)
                    .orElseGet(() -> {
                        WeeklyStat s = new WeeklyStat();
                        s.setUserId(userId);
                        s.setYear(year);
                        s.setWeekNumber(weekNumber);
                        return s;
                    });

            stat.setCompletedCount(stat.getCompletedCount() + 1);
            stat.setScheduledCount(stat.getScheduledCount() + 1);
            if (stat.getScheduledCount() > 0) {
                stat.setCompletionRate((double) stat.getCompletedCount() / stat.getScheduledCount());
            }

            // goal_achieved 체크
            weeklyGoalRepository.findByUserIdAndYearAndWeekNumber(userId, year, weekNumber)
                    .ifPresent(goal -> stat.setGoalAchieved(stat.getCompletedCount() >= goal.getGoalCount()));

            stat.setUpdatedAt(java.time.LocalDateTime.now());
            weeklyStatRepository.save(stat);

        } catch (Exception e) {
            log.error("Failed to process weekly stats messageId={}", message.getId(), e);
        } finally {
            redisTemplate.opsForStream().acknowledge(
                    message.getStream(), StatsStreamPublisher.WEEKLY_GROUP, message.getId());
        }
    }
}