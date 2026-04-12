package com.todo.ranking.infrastructure;

import com.todo.publisher.TodoEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamConsumer.class);

    private final StringRedisTemplate redisTemplate;

    public RedisStreamConsumer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * RankingWorker 초기화 시 ranking-group이 존재하지 않는 경우를 대비한 보조 초기화.
     * 주 초기화는 StatsWorker → TodoEventPublisher.initConsumerGroups()에서 처리한다.
     */
    public void initConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(
                    TodoEventPublisher.STREAM_KEY,
                    ReadOffset.from("0"),
                    TodoEventPublisher.RANKING_GROUP);
        } catch (Exception e) {
            if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                log.warn("Ranking consumer group init: {}", e.getMessage());
            }
        }
    }

    public String getStreamKey() {
        return TodoEventPublisher.STREAM_KEY;
    }

    public String getGroupName() {
        return TodoEventPublisher.RANKING_GROUP;
    }
}