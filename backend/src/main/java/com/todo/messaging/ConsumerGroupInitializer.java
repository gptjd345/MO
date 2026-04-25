package com.todo.messaging;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@DependsOn("redisStreamConfig")
public class ConsumerGroupInitializer {

    private static final Logger log = LoggerFactory.getLogger(ConsumerGroupInitializer.class);

    private final StringRedisTemplate redisTemplate;

    public ConsumerGroupInitializer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        createGroup(StreamNames.TODO, ConsumerGroups.STATS);
        createGroup(StreamNames.TODO, ConsumerGroups.RANKING);
    }

    private void createGroup(String stream, String group) {
        try {
            redisTemplate.opsForStream().createGroup(stream, ReadOffset.from("0"), group);
        } catch (Exception e) {
            if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                log.warn("Consumer group init failed [{}:{}]: {}", stream, group, e.getMessage());
            }
        }
    }
}