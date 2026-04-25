package com.todo.stats.worker;

import com.todo.messaging.ConsumerGroupInitializer;
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
public class StatsWorker {

    private static final Logger log = LoggerFactory.getLogger(StatsWorker.class);
    private static final int BATCH_SIZE = 10;
    private static final Duration IDLE_THRESHOLD = Duration.ofSeconds(30);
    private static final long MAX_DELIVERY_COUNT = 3;

    private final StatsProcessor statsProcessor;
    private final StringRedisTemplate redisTemplate;
    private final ConsumerGroupInitializer consumerGroupInitializer;

    public StatsWorker(StatsProcessor statsProcessor,
                       StringRedisTemplate redisTemplate,
                       ConsumerGroupInitializer consumerGroupInitializer) {
        this.statsProcessor = statsProcessor;
        this.redisTemplate = redisTemplate;
        this.consumerGroupInitializer = consumerGroupInitializer;
    }

    @SuppressWarnings("unchecked")
    @Scheduled(fixedDelay = 100)
    public void pollWeeklyStats() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .read(Consumer.from(ConsumerGroups.STATS, "stats-consumer"),
                            StreamReadOptions.empty().count(BATCH_SIZE),
                            StreamOffset.create(StreamNames.TODO, ReadOffset.lastConsumed()));

            if (records == null || records.isEmpty()) return;
            for (MapRecord<String, Object, Object> record : records) {
                statsProcessor.processWeeklyStats(record);
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("NOGROUP")) {
                consumerGroupInitializer.init();
            }
            log.warn("Weekly stream poll failed: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void retryPendingWeeklyStats() {
        retryPending(ConsumerGroups.STATS, "stats-consumer",
                statsProcessor::processWeeklyStats);
    }

    @SuppressWarnings("unchecked")
    private void retryPending(String group, String consumerName,
                              java.util.function.Consumer<MapRecord<String, Object, Object>> processor) {
        try {
            PendingMessages pending = redisTemplate.opsForStream().pending(
                    StreamNames.TODO, group,
                    Range.unbounded(), BATCH_SIZE);

            if (pending == null || !pending.iterator().hasNext()) return;

            for (PendingMessage msg : pending) {
                if (msg.getElapsedTimeSinceLastDelivery().compareTo(IDLE_THRESHOLD) < 0) continue;

                if (msg.getTotalDeliveryCount() >= MAX_DELIVERY_COUNT) {
                    log.error("Poison pill abandoned: group={} messageId={} deliveries={}",
                            group, msg.getId(), msg.getTotalDeliveryCount());
                    redisTemplate.opsForStream().acknowledge(StreamNames.TODO, group, msg.getId());
                    continue;
                }

                List<MapRecord<String, Object, Object>> claimed =
                        (List<MapRecord<String, Object, Object>>) (List<?>) redisTemplate.opsForStream()
                                .claim(StreamNames.TODO, group, consumerName,
                                        IDLE_THRESHOLD, msg.getId());

                if (claimed != null) {
                    log.info("Reclaiming PEL message: group={} messageId={} deliveries={}",
                            group, msg.getId(), msg.getTotalDeliveryCount());
                    for (MapRecord<String, Object, Object> record : claimed) {
                        processor.accept(record);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("PEL retry failed for group={}: {}", group, e.getMessage());
        }
    }
}
