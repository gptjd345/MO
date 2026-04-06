package com.todo.controller;

import com.todo.entity.Todo;
import com.todo.ranking.worker.RankingRebuildJob;
import com.todo.repository.TodoRepository;
import com.todo.stats.batch.DailyStatsBatchJob;
import com.todo.stats.domain.TodoEvent;
import com.todo.stats.infrastructure.TodoEventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/dev")
public class DevController {

    private final DailyStatsBatchJob dailyStatsBatchJob;
    private final RankingRebuildJob rankingRebuildJob;
    private final TodoEventRepository todoEventRepository;
    private final TodoRepository todoRepository;

    public DevController(DailyStatsBatchJob dailyStatsBatchJob,
                         RankingRebuildJob rankingRebuildJob,
                         TodoEventRepository todoEventRepository,
                         TodoRepository todoRepository) {
        this.dailyStatsBatchJob = dailyStatsBatchJob;
        this.rankingRebuildJob = rankingRebuildJob;
        this.todoEventRepository = todoEventRepository;
        this.todoRepository = todoRepository;
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
     * 완료된 todos 기반으로 todo_events 생성 (누락된 경우에만)
     */
    @PostMapping("/batch/backfillTodoEvents")
    public ResponseEntity<String> backfillTodoEvents() {
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
        return ResponseEntity.ok("todo_events 생성: " + created + "건 (전체 완료 todos: " + completedTodos.size() + "건)");
    }

    /***
     * 전체 todo_events를 기준으로 통계 재 계산
     */
    @PostMapping("/batch/recalculateStats")
    public ResponseEntity<String> runRecalculateStats() {
        List<TodoEvent> allEvents = todoEventRepository.findAll();

        // daily_stats 재계산
        Set<String> dailyPairs = allEvents.stream()
                .map(e -> e.getUserId() + ":" + e.getEventDate())
                .collect(Collectors.toSet());
        for (String pair : dailyPairs) {
            String[] parts = pair.split(":");
            dailyStatsBatchJob.recomputeDailyStat(Long.parseLong(parts[0]), LocalDate.parse(parts[1]));
        }

        // weekly_stats 재계산
        Set<String> weeklyTriples = allEvents.stream()
                .map(e -> {
                    LocalDate d = e.getEventDate();
                    int year = d.get(IsoFields.WEEK_BASED_YEAR);
                    int week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                    return e.getUserId() + ":" + year + ":" + week;
                })
                .collect(Collectors.toSet());
        for (String triple : weeklyTriples) {
            String[] parts = triple.split(":");
            dailyStatsBatchJob.recomputeWeeklyStat(Long.parseLong(parts[0]),
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }

        // streak_stats 재계산
        Set<Long> userIds = allEvents.stream().map(TodoEvent::getUserId).collect(Collectors.toSet());
        for (Long userId : userIds) {
            dailyStatsBatchJob.rebuildStreakFromHistory(userId);
        }

        rankingRebuildJob.rebuild();
        return ResponseEntity.ok(String.format(
                "전체 stats recalculate 완료 — daily: %d건, weekly: %d건, streak: %d명, ranking 재구성",
                dailyPairs.size(), weeklyTriples.size(), userIds.size()));
    }


}