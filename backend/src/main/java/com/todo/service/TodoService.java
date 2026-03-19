package com.todo.service;

import com.todo.dto.BatchCompleteRequest;
import com.todo.dto.TodoRequest;
import com.todo.dto.TodoUpdateRequest;
import com.todo.dto.TodoUpdateResponse;
import com.todo.entity.Todo;
import com.todo.entity.User;
import com.todo.ranking.infrastructure.RedisStreamConsumer;
import com.todo.repository.TodoRepository;
import com.todo.repository.UserRepository;
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

    public TodoService(TodoRepository todoRepository,
                       UserRepository userRepository,
                       RedisStreamConsumer redisStreamConsumer) {
        this.todoRepository = todoRepository;
        this.userRepository = userRepository;
        this.redisStreamConsumer = redisStreamConsumer;
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
                .orElseThrow(() -> new RuntimeException("Todo not found"));

        if (!todo.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
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
            todo.setCompleted(request.getCompleted());

            // false → true 전환이고 아직 점수를 받지 않은 경우만 점수 부여
            if (!wasCompleted && request.getCompleted() && !todo.isScored()) {
                pointsEarned = calculatePoints(todo);
                scoredUser = userRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found"));
                scoredUser.setScore(scoredUser.getScore() + pointsEarned);
                scoredUser.setRankingVersion(scoredUser.getRankingVersion() + 1);
                userRepository.save(scoredUser);
                todo.setScored(true);
            }
        }

        TodoUpdateResponse response = new TodoUpdateResponse(todoRepository.save(todo), pointsEarned);

        if (pointsEarned > 0 && scoredUser != null) {
            try {
                redisStreamConsumer.publish(userId, scoredUser.getScore(), scoredUser.getRankingVersion());
            } catch (Exception e) {
                log.warn("Ranking event publish failed for userId={}: {}", userId, e.getMessage());
            }
        }

        return response;
    }

    @Transactional
    public int batchComplete(Long userId, BatchCompleteRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) return 0;

        List<Todo> todos = todoRepository.findAllById(request.getIds());
        int totalPoints = 0;

        for (Todo todo : todos) {
            if (!todo.getUserId().equals(userId)) continue;
            if (todo.isCompleted() || todo.isScored()) continue;

            int points = calculatePoints(todo);
            todo.setCompleted(true);
            todo.setScored(true);
            todoRepository.save(todo);
            totalPoints += points;
        }

        if (totalPoints > 0) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setScore(user.getScore() + totalPoints);
            user.setRankingVersion(user.getRankingVersion() + 1);
            userRepository.save(user);

            try {
                redisStreamConsumer.publish(userId, user.getScore(), user.getRankingVersion());
            } catch (Exception e) {
                log.warn("Ranking event publish failed for userId={}: {}", userId, e.getMessage());
            }
        }

        return totalPoints;
    }

    private int calculatePoints(Todo todo) {
        if (todo.getEndDate() == null) return 10;
        return LocalDate.now().isAfter(todo.getEndDate()) ? 5 : 10;
    }

    public void deleteTodo(Long id, Long userId) {
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Todo not found"));

        if (!todo.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        todoRepository.delete(todo);
    }
}
