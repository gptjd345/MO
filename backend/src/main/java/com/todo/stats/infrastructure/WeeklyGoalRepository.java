package com.todo.stats.infrastructure;

import com.todo.stats.domain.WeeklyGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WeeklyGoalRepository extends JpaRepository<WeeklyGoal, Long> {

    Optional<WeeklyGoal> findByUserIdAndYearAndWeekNumber(Long userId, int year, int weekNumber);

    @Query("SELECT g FROM WeeklyGoal g WHERE g.userId = :userId ORDER BY g.year DESC, g.weekNumber DESC")
    List<WeeklyGoal> findRecentByUserId(@Param("userId") Long userId,
                                        org.springframework.data.domain.Pageable pageable);
}