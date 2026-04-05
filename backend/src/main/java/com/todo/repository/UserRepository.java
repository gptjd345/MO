package com.todo.repository;

import com.todo.entity.User;
import com.todo.ranking.domain.RankingDtos.UserScoreRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByNickname(String nickname);

    @Query("SELECT new com.todo.ranking.domain.RankingDtos$UserScoreRow(u.id, u.score) FROM User u WHERE u.score > 0")
    List<UserScoreRow> findIdAndScoreOfActiveUsers();
}
