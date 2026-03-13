package com.todo.repository;

import com.todo.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // revoked_at이 null인 경우만 유효한 토큰으로 간주
    boolean existsByJtiAndRevokedAtIsNull(String jti);

    Optional<RefreshToken> findByJti(String jti);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.jti = :jti")
    void revokeByJti(@Param("jti") String jti, @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.userId = :userId AND r.revokedAt IS NULL")
    void revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);
}
