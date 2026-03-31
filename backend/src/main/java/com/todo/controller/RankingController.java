package com.todo.controller;

import com.todo.dto.RankingResponse;
import com.todo.entity.User;
import com.todo.ranking.domain.Ranking;
import com.todo.ranking.domain.RankingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/around")
    public ResponseEntity<RankingResponse> getRankingAround(@AuthenticationPrincipal User user) {
        List<Ranking> rankings = rankingService.getRankingAround(user.getId());
        return ResponseEntity.ok(new RankingResponse(rankings));
    }
}
