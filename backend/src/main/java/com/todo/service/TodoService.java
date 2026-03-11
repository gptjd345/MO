package com.todo.service;

import com.todo.dto.TodoRequest;
import com.todo.dto.TodoUpdateRequest;
import com.todo.entity.Todo;
import com.todo.repository.TodoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TodoService {

    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
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

    public Todo updateTodo(Long id, Long userId, TodoUpdateRequest request) {
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Todo not found"));

        if (!todo.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        if (request.getTitle() != null) todo.setTitle(request.getTitle());
        if (request.getContent() != null) todo.setContent(request.getContent());
        if (request.getCompleted() != null) todo.setCompleted(request.getCompleted());
        if (request.getPriority() != null) todo.setPriority(request.getPriority());

        if (request.getStartDate() != null) {
            if (request.getStartDate().isEmpty()) {
                todo.setStartDate(null);
            } else {
                todo.setStartDate(LocalDate.parse(request.getStartDate()));
            }
        }
        if (request.getEndDate() != null) {
            if (request.getEndDate().isEmpty()) {
                todo.setEndDate(null);
            } else {
                todo.setEndDate(LocalDate.parse(request.getEndDate()));
            }
        }

        return todoRepository.save(todo);
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
