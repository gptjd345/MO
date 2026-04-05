package com.todo.stats.infrastructure;

import com.todo.stats.domain.StreakStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StreakStatRepository extends JpaRepository<StreakStat, Long> {

    Optional<StreakStat> findByUserId(Long userId);

    @Query("SELECT s FROM StreakStat s WHERE s.isFreezed = true")
    List<StreakStat> findAllFreezed();
}