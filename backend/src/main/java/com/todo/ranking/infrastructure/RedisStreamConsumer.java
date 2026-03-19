package com.todo.ranking.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RedisStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamConsumer.class);
    private static final String STREAM_KEY = "ranking:events";
    private static final String GROUP_NAME = "ranking-group";

    private final StringRedisTemplate redisTemplate;

    public RedisStreamConsumer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publish(Long userId, int score, int rankingVersion) {
        redisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .ofMap(Map.of(
                                "userId", userId.toString(),
                                "score", String.valueOf(score),
                                "rankingVersion", String.valueOf(rankingVersion)
                        ))
                        .withStreamKey(STREAM_KEY)
        );
    }

    public void initConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP_NAME);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                // 재시작 시 그룹이 이미 존재하는 정상 상황
            } else {
                log.warn("Consumer group init: {}", e.getMessage());
            }
        }
    }

    public String getStreamKey() {
        return STREAM_KEY;
    }

    public String getGroupName() {
        return GROUP_NAME;
    }
}
