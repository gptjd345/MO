package com.todo.ranking.worker;

import com.todo.ranking.infrastructure.RedisRankingRepository;
import com.todo.ranking.infrastructure.RedisStreamConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RankingProcessor {

    private static final Logger log = LoggerFactory.getLogger(RankingProcessor.class);

    private final RedisRankingRepository rankingRepository;
    private final RedisStreamConsumer redisStreamConsumer;
    private final StringRedisTemplate redisTemplate;

    public RankingProcessor(RedisRankingRepository rankingRepository,
                            RedisStreamConsumer redisStreamConsumer,
                            StringRedisTemplate redisTemplate) {
        this.rankingRepository = rankingRepository;
        this.redisStreamConsumer = redisStreamConsumer;
        this.redisTemplate = redisTemplate;
    }

    public void process(MapRecord<String, Object, Object> message) {
        try {
            Map<Object, Object> body = message.getValue();
            Long userId = Long.parseLong(body.get("userId").toString());
            int score = Integer.parseInt(body.get("score").toString());
            int rankingVersion = Integer.parseInt(body.get("rankingVersion").toString());

            int currentVersion = rankingRepository.getVersion(userId);

            if (rankingVersion > currentVersion) {
                rankingRepository.updateScore(userId, score);
                rankingRepository.updateVersion(userId, rankingVersion);
                log.debug("Ranking updated: userId={}, score={}, version={}", userId, score, rankingVersion);
            } else {
                log.debug("Stale event ignored: userId={}, eventVersion={}, currentVersion={}",
                        userId, rankingVersion, currentVersion);
            }
        } catch (Exception e) {
            log.error("Failed to process ranking event messageId={}", message.getId(), e);
        } finally {
            redisTemplate.opsForStream().acknowledge(
                    message.getStream(), redisStreamConsumer.getGroupName(), message.getId());
        }
    }
}
