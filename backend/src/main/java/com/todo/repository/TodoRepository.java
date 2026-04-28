package com.todo.repository;

import com.todo.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    List<Todo> findByUserIdOrderByIdDesc(Long userId);

    long countByUserIdAndCompletedTrueAndCompletedAtBetween(Long userId, LocalDate from, LocalDate to);

    @Query("SELECT t.completedAt as date, COUNT(t) as count FROM Todo t WHERE t.userId = :userId AND t.completed = true AND t.completedAt BETWEEN :from AND :to GROUP BY t.completedAt")
    List<Object[]> countCompletedByDateBetween(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT MAX(t.id) FROM Todo t WHERE t.userId = :userId AND t.completed = true AND t.completedAt = :completedAt")
    Long findMaxIdByCompletedAt(@Param("userId") Long userId, @Param("completedAt") LocalDate completedAt);

    @Query("SELECT COUNT(t) FROM Todo t WHERE t.userId = :userId AND t.completed = true AND t.id > :maxId")
    long countCompletedWithIdGreaterThan(@Param("userId") Long userId, @Param("maxId") Long maxId);
}