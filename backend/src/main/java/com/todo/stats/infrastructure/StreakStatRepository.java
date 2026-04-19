package com.todo.stats.infrastructure;

import com.todo.stats.domain.StreakStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StreakStatRepository extends JpaRepository<StreakStat, Long> {

    Optional<StreakStat> findByUserId(Long userId);

    @Query("SELECT s FROM StreakStat s WHERE s.isFreezed = true")
    List<StreakStat> findAllFreezed();

    @Query("SELECT s FROM StreakStat s WHERE s.isFreezed = true AND s.freezeYear = :freezeYear AND s.freezeWeek = :freezeWeek")
    List<StreakStat> findByFreezedTrueAndFreezeYearAndFreezeWeek(@Param("freezeYear") Integer freezeYear,
                                                                  @Param("freezeWeek") Integer freezeWeek);
}