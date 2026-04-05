package com.todo.service;

import com.todo.dto.BatchCompleteRequest;
import com.todo.dto.TodoRequest;
import com.todo.dto.TodoUpdateRequest;
import com.todo.dto.TodoUpdateResponse;
import com.todo.entity.Todo;
import com.todo.entity.User;
import com.todo.exception.CustomException;
import com.todo.exception.ErrorCode;
import com.todo.ranking.infrastructure.RedisStreamConsumer;
import com.todo.repository.TodoRepository;
import com.todo.repository.UserRepository;
import com.todo.stats.domain.TodoEvent;
import com.todo.stats.infrastructure.StatsStreamPublisher;
import com.todo.stats.infrastructure.TodoEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TodoService {

    private static final Logger log = LoggerFactory.getLogger(TodoService.class);

    private final TodoRepository todoRepository;
    private final UserRepository userRepository;
    private final RedisStreamConsumer redisStreamConsumer;
    private final TodoEventRepository todoEventRepository;
    private final StatsStreamPublisher statsStreamPublisher;

    public TodoService(TodoRepository todoRepository,
                       UserRepository userRepository,
                       RedisStreamConsumer redisStreamConsumer,
                       TodoEventRepository todoEventRepository,
                       StatsStreamPublisher statsStreamPublisher) {
        this.todoRepository = todoRepository;
        this.userRepository = userRepository;
        this.redisStreamConsumer = redisStreamConsumer;
        this.todoEventRepository = todoEventRepository;
        this.statsStreamPublisher = statsStreamPublisher;
    }

    public List<Todo> getTodos(Long userId) {
        return todoRepository.findByUserIdOrderByIdDesc(userId);
    }

    public Todo createTodo(Long userId, TodoRequest request) {
        Todo todo = new Todo();
        todo.setTitle(request.getTitle());
        todo.setContent(request.getContent());
        todo.setUserId(userId);
        todo.setPriority(request.getPriority() != null ? request.getPriority() : "MEDIUM");

        if (request.getStartDate() != null && !request.getStartDate().isEmpty()) {
            todo.setStartDate(LocalDate.parse(request.getStartDate()));
        }
        if (request.getEndDate() != null && !request.getEndDate().isEmpty()) {
            todo.setEndDate(LocalDate.parse(request.getEndDate()));
        }

        return todoRepository.save(todo);
    }

    @Transactional
    public TodoUpdateResponse updateTodo(Long id, Long userId, TodoUpdateRequest request) {
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.TODO_NOT_FOUND));

        if (!todo.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (request.getTitle() != null) todo.setTitle(request.getTitle());
        if (request.getContent() != null) todo.setContent(request.getContent());
        if (request.getPriority() != null) todo.setPriority(request.getPriority());

        if (request.getStartDate() != null) {
            todo.setStartDate(request.getStartDate().isEmpty() ? null : LocalDate.parse(request.getStartDate()));
        }
        if (request.getEndDate() != null) {
            todo.setEndDate(request.getEndDate().isEmpty() ? null : LocalDate.parse(request.getEndDate()));
        }

        int pointsEarned = 0;
        User scoredUser = null;

        if (request.getCompleted() != null) {
            boolean wasCompleted = todo.isCompleted();
            boolean nowCompleted = request.getCompleted();
            todo.setCompleted(nowCompleted);

            if (!wasCompleted && nowCompleted) {
                // false → true: 완료 처리
                LocalDate completedAt = LocalDate.now();
                todo.setCompletedAt(completedAt);

                if (!todo.isScored()) {
                    pointsEarned = calculatePoints(todo);
                    scoredUser = userRepository.findById(userId)
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                    scoredUser.setScore(scoredUser.getScore() + pointsEarned);
                    scoredUser.setRankingVersion(scoredUser.getRankingVersion() + 1);
                    userRepository.save(scoredUser);
                    todo.setScored(true);
                }

                recordTodoEvent(userId, todo.getId(), "COMPLETED", completedAt);

            } else if (wasCompleted && !nowCompleted) {
                // true → false: 완료 취소 (소급 처리는 배치가 담당)
                LocalDate originalCompletedAt = todo.getCompletedAt();
                todo.setCompletedAt(null);

                if (originalCompletedAt != null) {
                    recordTodoEvent(userId, todo.getId(), "CANCELLED", originalCompletedAt);
                }
            }
        }

        Todo saved = todoRepository.save(todo);

        if (pointsEarned > 0 && scoredUser != null) {
            try {
                redisStreamConsumer.publish(userId, scoredUser.getScore(), scoredUser.getRankingVersion());
            } catch (Exception e) {
                log.warn("Ranking event publish failed for userId={}: {}", userId, e.getMessage());
            }
        }

        if (request.getCompleted() != null && request.getCompleted() && saved.getCompletedAt() != null) {
            try {
                statsStreamPublisher.publishCompleted(userId, saved.getId(), saved.getCompletedAt());
            } catch (Exception e) {
                log.warn("Stats event publish failed for userId={}: {}", userId, e.getMessage());
            }
        }

        return new TodoUpdateResponse(saved, pointsEarned);
    }

    @Transactional
    public int batchComplete(Long userId, BatchCompleteRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) return 0;

        List<Todo> todos = todoRepository.findAllById(request.getIds());
        int totalPoints = 0;
        LocalDate today = LocalDate.now();
        List<Long> newlyCompletedIds = new java.util.ArrayList<>();

        for (Todo todo : todos) {
            if (!todo.getUserId().equals(userId)) continue;
            if (todo.isCompleted() || todo.isScored()) continue;

            int points = calculatePoints(todo);
            todo.setCompleted(true);
            todo.setCompletedAt(today);
            todo.setScored(true);
            todoRepository.save(todo);
            totalPoints += points;
            newlyCompletedIds.add(todo.getId());

            recordTodoEvent(userId, todo.getId(), "COMPLETED", today);
        }

        if (totalPoints > 0) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            user.setScore(user.getScore() + totalPoints);
            user.setRankingVersion(user.getRankingVersion() + 1);
            userRepository.save(user);

            try {
                redisStreamConsumer.publish(userId, user.getScore(), user.getRankingVersion());
            } catch (Exception e) {
                log.warn("Ranking event publish failed for userId={}: {}", userId, e.getMessage());
            }
        }

        // 이번 배치에서 새로 완료된 건만 stats 이벤트 발행
        for (Long todoId : newlyCompletedIds) {
            try {
                statsStreamPublisher.publishCompleted(userId, todoId, today);
            } catch (Exception e) {
                log.warn("Stats event publish failed for todoId={}: {}", todoId, e.getMessage());
            }
        }

        return totalPoints;
    }

    private void recordTodoEvent(Long userId, Long todoId, String eventType, LocalDate eventDate) {
        try {
            TodoEvent event = new TodoEvent();
            event.setUserId(userId);
            event.setTodoId(todoId);
            event.setEventType(eventType);
            event.setEventDate(eventDate);
            todoEventRepository.save(event);
        } catch (Exception e) {
            log.warn("TodoEvent record failed todoId={} type={}: {}", todoId, eventType, e.getMessage());
        }
    }

    private int calculatePoints(Todo todo) {
        if (todo.getEndDate() == null) return 10;
        return LocalDate.now().isAfter(todo.getEndDate()) ? 5 : 10;
    }

    public void deleteTodo(Long id, Long userId) {
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.TODO_NOT_FOUND));

        if (!todo.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        todoRepository.delete(todo);
    }
}