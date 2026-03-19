package com.todo.ranking.worker;

import com.todo.ranking.infrastructure.RedisRankingRepository;
import com.todo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RankingRebuildJob {

    private static final Logger log = LoggerFactory.getLogger(RankingRebuildJob.class);

    private final UserRepository userRepository;
    private final RedisRankingRepository rankingRepository;

    public RankingRebuildJob(UserRepository userRepository,
                             RedisRankingRepository rankingRepository) {
        this.userRepository = userRepository;
        this.rankingRepository = rankingRepository;
    }

    @Scheduled(cron = "0 10 1 * * *")
    public void rebuild() {
        log.info("Ranking rebuild started");
        try {
            Map<Long, Integer> userScores = userRepository.findAllByScoreGreaterThan(0)
                    .stream()
                    .collect(Collectors.toMap(u -> u.getId(), u -> u.getScore()));

            rankingRepository.buildNew(userScores);
            rankingRepository.swapAndExpire();

            log.info("Ranking rebuild completed: {} users", userScores.size());
        } catch (Exception e) {
            log.error("Ranking rebuild failed: {}", e.getMessage(), e);
        }
    }
}
