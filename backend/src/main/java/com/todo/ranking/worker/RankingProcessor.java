package com.todo.ranking.worker;

import com.todo.ranking.infrastructure.RedisRankingRepository;
import com.todo.ranking.infrastructure.RedisStreamConsumer;
import com.todo.repository.UserRepository;
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
    private final UserRepository userRepository;

    public RankingProcessor(RedisRankingRepository rankingRepository,
                            RedisStreamConsumer redisStreamConsumer,
                            StringRedisTemplate redisTemplate,
                            UserRepository userRepository) {
        this.rankingRepository = rankingRepository;
        this.redisStreamConsumer = redisStreamConsumer;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
    }

    public void process(MapRecord<String, Object, Object> message) {
        try {
            Map<Object, Object> body = message.getValue();
            Long userId = Long.parseLong(body.get("userId").toString());

            int score = userRepository.findById(userId)
                    .map(u -> u.getScore())
                    .orElse(0);

            rankingRepository.updateScore(userId, score);
            log.debug("Ranking updated: userId={}, score={}", userId, score);
        } catch (Exception e) {
            log.error("Failed to process ranking event messageId={}", message.getId(), e);
        } finally {
            redisTemplate.opsForStream().acknowledge(
                    message.getStream(), redisStreamConsumer.getGroupName(), message.getId());
        }
    }
}
