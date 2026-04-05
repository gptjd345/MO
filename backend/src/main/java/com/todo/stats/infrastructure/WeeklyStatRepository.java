package com.todo.stats.infrastructure;

import com.todo.stats.domain.WeeklyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WeeklyStatRepository extends JpaRepository<WeeklyStat, Long> {

    Optional<WeeklyStat> findByUserIdAndYearAndWeekNumber(Long userId, int year, int weekNumber);

    @Query("SELECT w FROM WeeklyStat w WHERE w.userId = :userId ORDER BY w.year DESC, w.weekNumber DESC")
    List<WeeklyStat> findRecentByUserId(@Param("userId") Long userId,
                                        org.springframework.data.domain.Pageable pageable);

    @Query("SELECT w FROM WeeklyStat w WHERE w.userId = :userId " +
           "AND (w.year > :year OR (w.year = :year AND w.weekNumber >= :weekNumber)) " +
           "ORDER BY w.year ASC, w.weekNumber ASC")
    List<WeeklyStat> findFromWeek(@Param("userId") Long userId,
                                  @Param("year") int year,
                                  @Param("weekNumber") int weekNumber);
}