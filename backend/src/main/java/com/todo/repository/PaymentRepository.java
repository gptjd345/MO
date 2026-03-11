package com.todo.repository;

import com.todo.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);
}
