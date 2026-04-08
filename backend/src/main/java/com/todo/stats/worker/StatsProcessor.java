package com.todo.stats.worker;

import com.todo.stats.domain.WeeklyStat;
import com.todo.stats.infrastructure.StatsStreamPublisher;
import com.todo.stats.infrastructure.WeeklyGoalRepository;
import com.todo.stats.infrastructure.WeeklyStatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Map;

@Component
public class StatsProcessor {

    private static final Logger log = LoggerFactory.getLogger(StatsProcessor.class);

    private final WeeklyStatRepository weeklyStatRepository;
    private final WeeklyGoalRepository weeklyGoalRepository;
    private final StringRedisTemplate redisTemplate;

    public StatsProcessor(WeeklyStatRepository weeklyStatRepository,
                          WeeklyGoalRepository weeklyGoalRepository,
                          StringRedisTemplate redisTemplate) {
        this.weeklyStatRepository = weeklyStatRepository;
        this.weeklyGoalRepository = weeklyGoalRepository;
        this.redisTemplate = redisTemplate;
    }

    public void processWeeklyStats(MapRecord<String, Object, Object> message) {
        try {
            Map<Object, Object> body = message.getValue();
            String eventType = body.get("eventType") != null ? body.get("eventType").toString() : null;

            // 처리 대상이 아닌 이벤트(INIT 등)는 즉시 ACK
            if (!"COMPLETED".equals(eventType) && !"UNCOMPLETED".equals(eventType)) {
                ack(message.getStream(), StatsStreamPublisher.WEEKLY_GROUP, message.getId());
                return;
            }

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

            if ("COMPLETED".equals(eventType)) {
                stat.setCompletedCount(stat.getCompletedCount() + 1);
            } else {
                stat.setCompletedCount(Math.max(0, stat.getCompletedCount() - 1));
            }

            // goalAchieved 재평가
            int goal = weeklyGoalRepository.findByUserIdAndYearAndWeekNumber(userId, year, weekNumber)
                    .map(g -> g.getGoalCount())
                    .orElse(3);
            stat.setGoalAchieved(stat.getCompletedCount() >= goal);
            stat.setUpdatedAt(java.time.LocalDateTime.now());
            weeklyStatRepository.save(stat);

            // DB 저장 성공 후에만 ACK
            ack(message.getStream(), StatsStreamPublisher.WEEKLY_GROUP, message.getId());

        } catch (Exception e) {
            // 의도적으로 ACK 하지 않음 — PEL에 남아 retryPending()에서 재처리됨
            log.error("Failed to process weekly stats messageId={} — leaving in PEL for retry", message.getId(), e);
        }
    }

    private void ack(String stream, String group, RecordId id) {
        try {
            redisTemplate.opsForStream().acknowledge(stream, group, id);
        } catch (Exception e) {
            log.warn("ACK failed stream={} group={} messageId={}: {}", stream, group, id, e.getMessage());
        }
    }
}