package com.todo.ranking.worker;

import com.todo.messaging.ConsumerGroups;
import com.todo.messaging.StreamNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class RankingWorker {

    private static final Logger log = LoggerFactory.getLogger(RankingWorker.class);
    private static final int BATCH_SIZE = 10;
    private static final Duration IDLE_THRESHOLD = Duration.ofSeconds(30);
    private static final long MAX_DELIVERY_COUNT = 3;

    private final RankingProcessor rankingProcessor;
    private final StringRedisTemplate redisTemplate;

    public RankingWorker(RankingProcessor rankingProcessor,
                         StringRedisTemplate redisTemplate) {
        this.rankingProcessor = rankingProcessor;
        this.redisTemplate = redisTemplate;
    }

    @SuppressWarnings("unchecked")
    @Scheduled(fixedDelay = 100)
    public void poll() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .read(Consumer.from(ConsumerGroups.RANKING, "ranking-consumer"),
                            StreamReadOptions.empty().count(BATCH_SIZE),
                            StreamOffset.create(StreamNames.TODO, ReadOffset.lastConsumed()));

            if (records == null || records.isEmpty()) return;

            for (MapRecord<String, Object, Object> record : records) {
                rankingProcessor.process(record);
            }
        } catch (Exception e) {
            log.warn("Ranking stream poll failed: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void retryPendingRanking() {
        try {
            PendingMessages pending = redisTemplate.opsForStream().pending(
                    StreamNames.TODO, ConsumerGroups.RANKING,
                    Range.unbounded(), BATCH_SIZE);

            if (pending == null || !pending.iterator().hasNext()) return;

            for (PendingMessage msg : pending) {
                if (msg.getElapsedTimeSinceLastDelivery().compareTo(IDLE_THRESHOLD) < 0) continue;

                if (msg.getTotalDeliveryCount() >= MAX_DELIVERY_COUNT) {
                    log.error("Poison pill abandoned: group={} messageId={} deliveries={}",
                            ConsumerGroups.RANKING, msg.getId(), msg.getTotalDeliveryCount());
                    redisTemplate.opsForStream().acknowledge(StreamNames.TODO, ConsumerGroups.RANKING, msg.getId());
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<MapRecord<String, Object, Object>> claimed =
                        (List<MapRecord<String, Object, Object>>) (List<?>) redisTemplate.opsForStream()
                                .claim(StreamNames.TODO, ConsumerGroups.RANKING, "ranking-consumer",
                                        IDLE_THRESHOLD, msg.getId());

                if (claimed != null) {
                    log.info("Reclaiming PEL message: group={} messageId={} deliveries={}",
                            ConsumerGroups.RANKING, msg.getId(), msg.getTotalDeliveryCount());
                    for (MapRecord<String, Object, Object> record : claimed) {
                        rankingProcessor.process(record);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("PEL retry failed for group={}: {}", ConsumerGroups.RANKING, e.getMessage());
        }
    }
}
