package com.todo.messaging;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

@Configuration
public class RedisStreamConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamConfig.class);

    private final StringRedisTemplate redisTemplate;

    public RedisStreamConfig(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void ensureStreamExists() {
        try {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(StreamNames.TODO))) {
                redisTemplate.opsForStream().add(
                        StreamRecords.newRecord()
                                .ofMap(Map.of("type", "INIT"))
                                .withStreamKey(StreamNames.TODO)
                );
            }
        } catch (Exception e) {
            log.warn("Stream ensure failed [{}]: {}", StreamNames.TODO, e.getMessage());
        }
    }
}