package com.todo.controller;

import com.todo.entity.Todo;
import com.todo.ranking.worker.RankingRebuildJob;
import com.todo.repository.TodoRepository;
import com.todo.stats.batch.DailyStatsBatchJob;
import com.todo.stats.domain.TodoEvent;
import com.todo.stats.infrastructure.TodoEventRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Profile("dev")
@RestController
@RequestMapping("/dev")
public class DevController {

    private final DailyStatsBatchJob dailyStatsBatchJob;
    private final RankingRebuildJob rankingRebuildJob;

    public DevController(DailyStatsBatchJob dailyStatsBatchJob,
                         RankingRebuildJob rankingRebuildJob) {
        this.dailyStatsBatchJob = dailyStatsBatchJob;
        this.rankingRebuildJob = rankingRebuildJob;

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
    @GetMapping("/batch/backfill")
    public ResponseEntity<String> runBackfill() {
        dailyStatsBatchJob.runBackfill();
        rankingRebuildJob.rebuild();
        return ResponseEntity.ok("전체 stats backfill + ranking 완료");
    }


}