package com.todo.service;

import com.todo.dto.TodoRequest;
import com.todo.dto.TodoUpdateRequest;
import com.todo.dto.TodoUpdateResponse;
import com.todo.entity.Todo;
import com.todo.entity.User;
import com.todo.repository.TodoRepository;
import com.todo.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TodoService {

    private final TodoRepository todoRepository;
    private final UserRepository userRepository;

    public TodoService(TodoRepository todoRepository, UserRepository userRepository) {
        this.todoRepository = todoRepository;
        this.userRepository = userRepository;
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

        if (request.getCompleted() != null) {
            boolean wasCompleted = todo.isCompleted();
            todo.setCompleted(request.getCompleted());

            // false → true 전환이고 아직 점수를 받지 않은 경우만 점수 부여
            if (!wasCompleted && request.getCompleted() && !todo.isScored()) {
                pointsEarned = calculatePoints(todo);
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found"));
                user.setScore(user.getScore() + pointsEarned);
                userRepository.save(user);
                todo.setScored(true);
            }
        }

        return new TodoUpdateResponse(todoRepository.save(todo), pointsEarned);
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
