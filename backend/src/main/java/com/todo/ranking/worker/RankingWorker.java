package com.todo.ranking.worker;

import com.todo.ranking.infrastructure.RedisStreamConsumer;
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
public class RankingWorker {

    private static final Logger log = LoggerFactory.getLogger(RankingWorker.class);
    private static final int BATCH_SIZE = 10;

    private final RedisStreamConsumer redisStreamConsumer;
    private final RankingProcessor rankingProcessor;
    private final StringRedisTemplate redisTemplate;

    public RankingWorker(RedisStreamConsumer redisStreamConsumer,
                         RankingProcessor rankingProcessor,
                         StringRedisTemplate redisTemplate) {
        this.redisStreamConsumer = redisStreamConsumer;
        this.rankingProcessor = rankingProcessor;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        redisStreamConsumer.initConsumerGroup();
    }

    @SuppressWarnings("unchecked")
    @Scheduled(fixedDelay = 100)
    public void poll() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .read(Consumer.from(redisStreamConsumer.getGroupName(), "ranking-consumer"),
                            StreamReadOptions.empty().count(BATCH_SIZE),
                            StreamOffset.create(redisStreamConsumer.getStreamKey(), ReadOffset.lastConsumed()));

            if (records == null || records.isEmpty()) return;

            for (MapRecord<String, Object, Object> record : records) {
                rankingProcessor.process(record);
            }
        } catch (Exception e) {
            log.warn("Ranking stream poll failed: {}", e.getMessage());
        }
    }
}
