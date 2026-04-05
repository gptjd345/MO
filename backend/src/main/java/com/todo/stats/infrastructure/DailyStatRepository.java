package com.todo.stats.infrastructure;

import com.todo.stats.domain.DailyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyStatRepository extends JpaRepository<DailyStat, Long> {

    Optional<DailyStat> findByUserIdAndStatDate(Long userId, LocalDate statDate);

    @Query("SELECT d FROM DailyStat d WHERE d.userId = :userId " +
           "AND d.statDate >= :from AND d.statDate <= :to ORDER BY d.statDate ASC")
    List<DailyStat> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}