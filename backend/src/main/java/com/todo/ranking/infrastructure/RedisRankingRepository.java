package com.todo.ranking.infrastructure;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisRankingRepository {

    private static final String RANKING_KEY = "ranking";

    private final StringRedisTemplate redisTemplate;

    public RedisRankingRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateScore(Long userId, int score) {
        redisTemplate.opsForZSet().add(RANKING_KEY, userId.toString(), score);
    }

    public Double getScore(Long userId) {
        return redisTemplate.opsForZSet().score(RANKING_KEY, userId.toString());
    }

    public Long getReverseRank(Long userId) {
        return redisTemplate.opsForZSet().reverseRank(RANKING_KEY, userId.toString());
    }

    // 점수가 userScore보다 높은 상위 N명 (높은 점수 순)
    public Set<ZSetOperations.TypedTuple<String>> getAbove(double score, int limit) {
        return redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(RANKING_KEY, score + 0.5, Double.MAX_VALUE, 0, limit);
    }

    // 점수가 userScore보다 낮은 하위 N명 (가까운 순)
    public Set<ZSetOperations.TypedTuple<String>> getBelow(double score, int limit) {
        return redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(RANKING_KEY, 0, score - 0.5, 0, limit);
    }

    // rebuild: ranking:new 에 데이터 채우기
    public void buildNew(Map<Long, Integer> userScores) {
        String newRankingKey = RANKING_KEY + ":new";
        redisTemplate.delete(newRankingKey);
        userScores.forEach((userId, score) ->
                redisTemplate.opsForZSet().add(newRankingKey, userId.toString(), score));
    }

    // swap: new → current, current → old / TTL 설정
    public void swapAndExpire() {
        String oldRankingKey = RANKING_KEY + ":old";
        String newRankingKey = RANKING_KEY + ":new";

        if (Boolean.TRUE.equals(redisTemplate.hasKey(RANKING_KEY))) {
            redisTemplate.rename(RANKING_KEY, oldRankingKey);
            redisTemplate.expire(oldRankingKey, 1, TimeUnit.HOURS);
        }
        redisTemplate.rename(newRankingKey, RANKING_KEY);
    }
}
