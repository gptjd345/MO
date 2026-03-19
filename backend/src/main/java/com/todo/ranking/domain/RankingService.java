package com.todo.ranking.domain;

import com.todo.entity.User;
import com.todo.ranking.infrastructure.RedisRankingRepository;
import com.todo.repository.UserRepository;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RankingService {

    private final RedisRankingRepository rankingRepository;
    private final UserRepository userRepository;

    public RankingService(RedisRankingRepository rankingRepository,
                          UserRepository userRepository) {
        this.rankingRepository = rankingRepository;
        this.userRepository = userRepository;
    }

    /**
     * 본인 기준 상위 3명 / 본인 / 하위 2명을 반환합니다.
     * 아직 랭킹에 등록되지 않은 경우(완료한 일정 없음) 빈 리스트를 반환합니다.
     */
    public List<Ranking> getRankingAround(Long userId) {
        Double userScore = rankingRepository.getScore(userId);
        if (userScore == null) return List.of();

        Long userRankZeroBased = rankingRepository.getReverseRank(userId);
        if (userRankZeroBased == null) return List.of();
        long userRank = userRankZeroBased + 1; // 1-indexed

        List<ZSetOperations.TypedTuple<String>> above =
                new ArrayList<>(rankingRepository.getAbove(userScore, 3));
        List<ZSetOperations.TypedTuple<String>> below =
                new ArrayList<>(rankingRepository.getBelow(userScore, 2));

        // 닉네임 일괄 조회
        Set<Long> allUserIds = new HashSet<>();
        above.forEach(t -> allUserIds.add(Long.parseLong(t.getValue())));
        below.forEach(t -> allUserIds.add(Long.parseLong(t.getValue())));
        allUserIds.add(userId);

        Map<Long, String> nicknameMap = userRepository.findAllById(allUserIds)
                .stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        List<Ranking> result = new ArrayList<>();

        // 상위 N명: above[0]이 가장 높은 점수 (highest first)
        int aboveCount = above.size();
        for (int i = 0; i < aboveCount; i++) {
            ZSetOperations.TypedTuple<String> tuple = above.get(i);
            Long uid = Long.parseLong(tuple.getValue());
            int score = tuple.getScore().intValue();
            long rank = userRank - (aboveCount - i);
            result.add(new Ranking(uid, nicknameMap.getOrDefault(uid, "Unknown"), score, rank, false));
        }

        // 본인
        result.add(new Ranking(userId,
                nicknameMap.getOrDefault(userId, "Unknown"),
                userScore.intValue(),
                userRank,
                true));

        // 하위 N명: below[0]이 가장 가까운 하위 (closest first)
        for (int i = 0; i < below.size(); i++) {
            ZSetOperations.TypedTuple<String> tuple = below.get(i);
            Long uid = Long.parseLong(tuple.getValue());
            int score = tuple.getScore().intValue();
            long rank = userRank + i + 1;
            result.add(new Ranking(uid, nicknameMap.getOrDefault(uid, "Unknown"), score, rank, false));
        }

        return result;
    }
}
