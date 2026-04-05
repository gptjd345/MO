package com.todo.controller;

import com.todo.dto.BatchCompleteRequest;
import com.todo.dto.PagedTodoResponse;
import com.todo.dto.TodoRequest;
import com.todo.dto.TodoUpdateRequest;
import com.todo.entity.Todo;
import com.todo.entity.User;
import com.todo.service.TodoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public ResponseEntity<PagedTodoResponse> list(
            @RequestParam boolean completed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(todoService.getTodosPaged(user.getId(), completed, page, size, sort, search));
    }

    @PostMapping
    public ResponseEntity<Todo> create(@AuthenticationPrincipal User user,
                                       @Valid @RequestBody TodoRequest request) {
        Todo todo = todoService.createTodo(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(todo);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Todo> update(@AuthenticationPrincipal User user,
                                       @PathVariable Long id,
                                       @RequestBody TodoUpdateRequest request) {
        return ResponseEntity.ok(todoService.updateTodo(id, user.getId(), request));
    }

    @PatchMapping("/batch-complete")
    public ResponseEntity<Map<String, Integer>> batchComplete(@AuthenticationPrincipal User user,
                                                              @RequestBody BatchCompleteRequest request) {
        int totalPoints = todoService.batchComplete(user.getId(), request);
        return ResponseEntity.ok(Map.of("totalPointsEarned", totalPoints));
    }

    @PatchMapping("/batch-undo")
    public ResponseEntity<Map<String, Integer>> batchUndo(@AuthenticationPrincipal User user,
                                                          @RequestBody BatchCompleteRequest request) {
        int totalPointsDeducted = todoService.batchUndo(user.getId(), request);
        return ResponseEntity.ok(Map.of("totalPointsDeducted", totalPointsDeducted));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user,
                                       @PathVariable Long id) {
        todoService.deleteTodo(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
