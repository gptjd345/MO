package com.todo.controller;

import com.todo.ranking.worker.RankingRebuildJob;
import com.todo.stats.batch.DailyStatsBatchJob;
import com.todo.stats.domain.TodoEvent;
import com.todo.stats.infrastructure.TodoEventRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Profile("dev")
@RestController
@RequestMapping("/dev")
public class DevController {

    private final DailyStatsBatchJob dailyStatsBatchJob;
    private final RankingRebuildJob rankingRebuildJob;
    private final TodoEventRepository todoEventRepository;

    public DevController(DailyStatsBatchJob dailyStatsBatchJob,
                         RankingRebuildJob rankingRebuildJob,
                         TodoEventRepository todoEventRepository) {
        this.dailyStatsBatchJob = dailyStatsBatchJob;
        this.rankingRebuildJob = rankingRebuildJob;
        this.todoEventRepository = todoEventRepository;
    }

    /***
     * 일별 돌았어야하는 배치 수동실행
     * @return
     */
    @PostMapping("/batch/stats")
    public ResponseEntity<String> runStatsBatch() {
        dailyStatsBatchJob.runJob();
        rankingRebuildJob.rebuild();
        return ResponseEntity.ok("stats + ranking batch 실행 완료");
    }

    /***
     * 전체 통계 재 계산
     * @return
     */
    @GetMapping("/batch/recalculateStats")
    public ResponseEntity<String> runRecalculateStats() {
        List<TodoEvent> allEvents = todoEventRepository.findAll();
        Set<String> pairs = allEvents.stream()
                .map(e -> e.getUserId() + ":" + e.getEventDate())
                .collect(Collectors.toSet());

        for (String pair : pairs) {
            String[] parts = pair.split(":");
            dailyStatsBatchJob.recomputeDailyStat(Long.parseLong(parts[0]), LocalDate.parse(parts[1]));
        }
        rankingRebuildJob.rebuild();
        return ResponseEntity.ok("전체 stats recalculate + ranking 완료 (" + pairs.size() + "건)");
    }


}