package com.todo.repository;

import com.todo.entity.Todo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TodoRepository extends JpaRepository<Todo, Long>, JpaSpecificationExecutor<Todo> {

    List<Todo> findByUserIdOrderByIdDesc(Long userId);
    @Query("SELECT t FROM Todo t WHERE t.userId = :userId AND t.completed = :completed " +
           "ORDER BY CASE t.priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END ASC, t.id DESC")
    Page<Todo> findByUserIdAndCompletedOrderByPriority(
            @Param("userId") Long userId, @Param("completed") boolean completed, Pageable pageable);

    @Query("SELECT t FROM Todo t WHERE t.userId = :userId AND t.completed = :completed " +
           "AND (LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(COALESCE(t.content, '')) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY CASE t.priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END ASC, t.id DESC")
    Page<Todo> findByUserIdAndCompletedAndSearchOrderByPriority(
            @Param("userId") Long userId, @Param("completed") boolean completed,
            @Param("search") String search, Pageable pageable);
}