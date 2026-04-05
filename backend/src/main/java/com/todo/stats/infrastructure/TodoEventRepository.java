package com.todo.stats.infrastructure;

import com.todo.stats.domain.TodoEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TodoEventRepository extends JpaRepository<TodoEvent, Long> {

    @Query("SELECT DISTINCT e.userId FROM TodoEvent e " +
           "WHERE e.createdAt >= :from AND e.createdAt < :to")
    List<Long> findDistinctUserIdsByCreatedAtBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT e FROM TodoEvent e " +
           "WHERE e.createdAt >= :from AND e.createdAt < :to")
    List<TodoEvent> findByCreatedAtBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    boolean existsByTodoIdAndEventType(Long todoId, String eventType);

    boolean existsByUserIdAndEventTypeAndEventDateBetween(
            Long userId, String eventType, LocalDate from, LocalDate to);
}