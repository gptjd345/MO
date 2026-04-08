package com.todo.stats.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class StatsStreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(StatsStreamPublisher.class);
    public static final String STREAM_KEY = "stats:events";
    public static final String WEEKLY_GROUP = "weekly-group";

    private final StringRedisTemplate redisTemplate;

    public StatsStreamPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publishCompleted(Long userId, Long todoId, LocalDate completedAt) {
        publish("COMPLETED", userId, todoId, completedAt);
    }

    public void publishUncompleted(Long userId, Long todoId, LocalDate completedAt) {
        publish("UNCOMPLETED", userId, todoId, completedAt);
    }

    private void publish(String eventType, Long userId, Long todoId, LocalDate eventDate) {
        redisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .ofMap(Map.of(
                                "eventType", eventType,
                                "userId", userId.toString(),
                                "todoId", todoId.toString(),
                                "eventDate", eventDate.toString()
                        ))
                        .withStreamKey(STREAM_KEY)
        );
    }

    public void initConsumerGroups() {
        ensureStreamExists();
        initGroup(WEEKLY_GROUP);
    }

    // 스트림이 없으면 consumer group 생성 자체가 실패하므로 먼저 스트림을 보장
    private void ensureStreamExists() {
        try {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(STREAM_KEY))) {
                redisTemplate.opsForStream().add(
                        StreamRecords.newRecord()
                                .ofMap(Map.of("type", "INIT"))
                                .withStreamKey(STREAM_KEY)
                );
            }
        } catch (Exception e) {
            log.warn("Stream ensure failed [{}]: {}", STREAM_KEY, e.getMessage());
        }
    }

    private void initGroup(String groupName) {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), groupName);
        } catch (Exception e) {
            if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                log.warn("Consumer group init [{}]: {}", groupName, e.getMessage());
            }
        }
    }
}