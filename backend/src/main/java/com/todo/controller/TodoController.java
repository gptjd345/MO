package com.todo.controller;

import com.todo.dto.TodoRequest;
import com.todo.dto.TodoUpdateRequest;
import com.todo.dto.TodoUpdateResponse;
import com.todo.entity.Todo;
import com.todo.entity.User;
import com.todo.service.TodoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public ResponseEntity<List<Todo>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(todoService.getTodos(user.getId()));
    }

    @PostMapping
    public ResponseEntity<Todo> create(@AuthenticationPrincipal User user,
                                       @Valid @RequestBody TodoRequest request) {
        Todo todo = todoService.createTodo(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(todo);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@AuthenticationPrincipal User user,
                                    @PathVariable Long id,
                                    @RequestBody TodoUpdateRequest request) {
        try {
            TodoUpdateResponse response = todoService.updateTodo(id, user.getId(), request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@AuthenticationPrincipal User user,
                                    @PathVariable Long id) {
        try {
            todoService.deleteTodo(id, user.getId());
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
