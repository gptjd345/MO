package com.todo.service;

import com.todo.dto.BatchCompleteRequest;
import com.todo.dto.PagedTodoResponse;
import com.todo.dto.TodoRequest;
import com.todo.dto.TodoUpdateRequest;
import com.todo.entity.Todo;
import com.todo.entity.User;
import com.todo.exception.CustomException;
import com.todo.exception.ErrorCode;
import com.todo.repository.TodoRepository;
import com.todo.repository.UserRepository;
import com.todo.event.EventPublisher;
import com.todo.event.TodoCanceledEvent;
import com.todo.event.TodoCompletedEvent;
import com.todo.stats.domain.TodoEvent;
import com.todo.stats.infrastructure.TodoEventRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class TodoService {

    private static final Logger log = LoggerFactory.getLogger(TodoService.class);

    private final TodoRepository todoRepository;
    private final UserRepository userRepository;
    private final TodoEventRepository todoEventRepository;
    private final EventPublisher eventPublisher;

    public TodoService(TodoRepository todoRepository,
                       UserRepository userRepository,
                       TodoEventRepository todoEventRepository,
                       EventPublisher eventPublisher) {
        this.todoRepository = todoRepository;
        this.userRepository = userRepository;
        this.todoEventRepository = todoEventRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<Todo> getTodos(Long userId) {
        return todoRepository.findByUserIdOrderByIdDesc(userId);
    }

    public PagedTodoResponse getTodosPaged(Long userId, boolean completed, int page, int size, String sort, String search) {
        boolean hasSearch = search != null && !search.isBlank();
        Pageable pageable = PageRequest.of(page, size);
        Page<Todo> result;

        if ("priority".equals(sort)) {
            result = hasSearch
                    ? todoRepository.findByUserIdAndCompletedAndSearchOrderByPriority(userId, completed, search, pageable)
                    : todoRepository.findByUserIdAndCompletedOrderByPriority(userId, completed, pageable);
        } else {
            Sort springSort = switch (sort) {
                case "oldest" -> Sort.by("id").ascending();
                case "name" -> Sort.by("title").ascending();
                case "deadline" -> Sort.by(Sort.Order.asc("endDate").nullsLast());
                default -> Sort.by("id").descending();
            };
            Specification<Todo> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("userId"), userId));
                predicates.add(cb.equal(root.get("completed"), completed));
                if (hasSearch) {
                    String like = "%" + search.toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("title")), like),
                            cb.like(cb.lower(cb.coalesce(root.get("content"), "")), like)
                    ));
                }
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            result = todoRepository.findAll(spec, PageRequest.of(page, size, springSort));
        }

        return new PagedTodoResponse(result.getContent(), result.getTotalElements(), result.getTotalPages());
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
    public Todo updateTodo(Long id, Long userId, TodoUpdateRequest request) {
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

        return todoRepository.save(todo);
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
            if (todo.isCompleted()) continue;

            int points = calculatePoints(todo);
            todo.setCompleted(true);
            todo.setCompletedAt(today);
            todoRepository.save(todo);
            totalPoints += points;
            newlyCompletedIds.add(todo.getId());

            recordTodoEvent(userId, todo.getId(), "COMPLETED", today);
        }

        if (totalPoints > 0) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            user.setScore(user.getScore() + totalPoints);
            userRepository.save(user);

            // 단일 이벤트를 todo:events 스트림에 발행
            // weekly-group(주간통계)과 ranking-group(랭킹)이 동일 이벤트를 독립적으로 소비
            for (Long todoId : newlyCompletedIds) {
                try {
                    eventPublisher.publish(new TodoCompletedEvent(userId, todoId, today));
                } catch (Exception e) {
                    log.warn("Event publish failed for todoId={}: {}", todoId, e.getMessage());
                }
            }
        }

        return totalPoints;
    }

    @Transactional
    public int batchUndo(Long userId, BatchCompleteRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) return 0;

        List<Todo> todos = todoRepository.findAllById(request.getIds());
        int totalPointsDeducted = 0;

        List<long[]> undonePairs = new java.util.ArrayList<>(); // [todoId, completedAt]

        for (Todo todo : todos) {
            if (!todo.getUserId().equals(userId)) continue;
            if (!todo.isCompleted()) continue;

            LocalDate originalCompletedAt = todo.getCompletedAt();
            int pointsToDeduct = calculateEarnedPoints(originalCompletedAt, todo.getEndDate());
            todo.setCompleted(false);
            todo.setCompletedAt(null);
            todoRepository.save(todo);
            totalPointsDeducted += pointsToDeduct;

            if (originalCompletedAt != null) {
                recordTodoEvent(userId, todo.getId(), "CANCELLED", originalCompletedAt);
                undonePairs.add(new long[]{todo.getId(), originalCompletedAt.toEpochDay()});
            }
        }

        if (totalPointsDeducted > 0) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            user.setScore(Math.max(0, user.getScore() - totalPointsDeducted));
            userRepository.save(user);

            for (long[] pair : undonePairs) {
                try {
                    eventPublisher.publish(new TodoCanceledEvent(userId, pair[0], LocalDate.ofEpochDay(pair[1])));
                } catch (Exception e) {
                    log.warn("Event publish failed for todoId={}: {}", pair[0], e.getMessage());
                }
            }
        }

        return totalPointsDeducted;
    }

    // 트랜잭션 내에서 호출할 것. 예외 발생 시 상위 트랜잭션이 롤백되어야 함.
    private void recordTodoEvent(Long userId, Long todoId, String eventType, LocalDate eventDate) {
        TodoEvent event = new TodoEvent();
        event.setUserId(userId);
        event.setTodoId(todoId);
        event.setEventType(eventType);
        event.setEventDate(eventDate);
        todoEventRepository.save(event);
    }

    private int calculatePoints(Todo todo) {
        if (todo.getEndDate() == null) return 10;
        return LocalDate.now().isAfter(todo.getEndDate()) ? 5 : 10;
    }

    private int calculateEarnedPoints(LocalDate completedAt, LocalDate endDate) {
        if (endDate == null || completedAt == null) return 10;
        return completedAt.isAfter(endDate) ? 5 : 10;
    }

    public int findCompletedPage(Long userId, LocalDate completedAt, int pageSize) {
        Long maxId = todoRepository.findMaxIdByCompletedAt(userId, completedAt);
        if (maxId == null) return 0;
        long count = todoRepository.countCompletedWithIdGreaterThan(userId, maxId);
        return (int) (count / pageSize);
    }

    public void deleteTodo(Long id, Long userId) {
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.TODO_NOT_FOUND));

        if (!todo.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (todo.isCompleted()) {
            throw new CustomException(ErrorCode.TODO_ALREADY_COMPLETED);
        }

        todoRepository.delete(todo);
    }
}