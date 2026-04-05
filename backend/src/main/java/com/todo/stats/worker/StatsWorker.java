package com.todo.stats.worker;

import com.todo.stats.infrastructure.StatsStreamPublisher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StatsWorker {

    private static final Logger log = LoggerFactory.getLogger(StatsWorker.class);
    private static final int BATCH_SIZE = 10;

    private final StatsStreamPublisher statsStreamPublisher;
    private final StatsProcessor statsProcessor;
    private final StringRedisTemplate redisTemplate;

    public StatsWorker(StatsStreamPublisher statsStreamPublisher,
                       StatsProcessor statsProcessor,
                       StringRedisTemplate redisTemplate) {
        this.statsStreamPublisher = statsStreamPublisher;
        this.statsProcessor = statsProcessor;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        statsStreamPublisher.initConsumerGroups();
    }

    @SuppressWarnings("unchecked")
    @Scheduled(fixedDelay = 100)
    public void pollDailyStats() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .read(Consumer.from(StatsStreamPublisher.STATS_GROUP, "stats-consumer"),
                            StreamReadOptions.empty().count(BATCH_SIZE),
                            StreamOffset.create(StatsStreamPublisher.STREAM_KEY, ReadOffset.lastConsumed()));

            if (records == null || records.isEmpty()) return;

            for (MapRecord<String, Object, Object> record : records) {
                statsProcessor.processDailyStats(record);
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("NOGROUP")) {
                statsStreamPublisher.initConsumerGroups();
            }
            log.warn("Stats stream poll failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Scheduled(fixedDelay = 100)
    public void pollWeeklyStats() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .read(Consumer.from(StatsStreamPublisher.WEEKLY_GROUP, "weekly-consumer"),
                            StreamReadOptions.empty().count(BATCH_SIZE),
                            StreamOffset.create(StatsStreamPublisher.STREAM_KEY, ReadOffset.lastConsumed()));

            if (records == null || records.isEmpty()) return;

            for (MapRecord<String, Object, Object> record : records) {
                statsProcessor.processWeeklyStats(record);
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("NOGROUP")) {
                statsStreamPublisher.initConsumerGroups();
            }
            log.warn("Weekly stream poll failed: {}", e.getMessage());
        }
    }
}