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
    private final TodoRepository todoRepository;
    private final TodoEventRepository todoEventRepository;

    public DevController(DailyStatsBatchJob dailyStatsBatchJob,
                         RankingRebuildJob rankingRebuildJob,
                         TodoRepository todoRepository,
                         TodoEventRepository todoEventRepository) {
        this.dailyStatsBatchJob = dailyStatsBatchJob;
        this.rankingRebuildJob = rankingRebuildJob;
        this.todoRepository = todoRepository;
        this.todoEventRepository = todoEventRepository;
    }

    @PostMapping("/batch/stats")
    public ResponseEntity<String> runStatsBatch() {
        dailyStatsBatchJob.runJob();
        rankingRebuildJob.rebuild();
        return ResponseEntity.ok("stats + ranking batch 실행 완료");
    }

    @GetMapping("/batch/backfill")
    public ResponseEntity<String> runBackfill() {
        dailyStatsBatchJob.runBackfill();
        rankingRebuildJob.rebuild();
        return ResponseEntity.ok("전체 stats backfill + ranking 완료");
    }

    @GetMapping("/batch/seed-events")
    public ResponseEntity<String> seedTodoEvents() {
        List<Todo> completedTodos = todoRepository.findAll().stream()
                .filter(t -> t.isCompleted() && t.getCompletedAt() != null)
                .toList();

        int created = 0;
        for (Todo todo : completedTodos) {
            if (!todoEventRepository.existsByTodoIdAndEventType(todo.getId(), "COMPLETED")) {
                TodoEvent event = new TodoEvent();
                event.setUserId(todo.getUserId());
                event.setTodoId(todo.getId());
                event.setEventType("COMPLETED");
                event.setEventDate(todo.getCompletedAt());
                todoEventRepository.save(event);
                created++;
            }
        }

        dailyStatsBatchJob.runBackfill();
        rankingRebuildJob.rebuild();

        return ResponseEntity.ok("todo_events 생성: " + created + "건, stats + ranking 갱신 완료");
    }
}