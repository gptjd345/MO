package com.todo.service;

import com.todo.dto.PaymentResponse;
import com.todo.entity.Payment;
import com.todo.entity.User;
import com.todo.payment.domain.PgClient;
import com.todo.payment.domain.PgRequest;
import com.todo.payment.domain.PgResult;
import com.todo.payment.domain.PgTimeoutException;
import com.todo.payment.domain.PgSystemException;
import com.todo.repository.PaymentRepository;
import com.todo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Long PRO_PLAN_AMOUNT = 9900L;
    private static final int MAX_RETRIES = 3;

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PgClient pgClient;

    public PaymentService(PaymentRepository paymentRepository,
                          UserRepository userRepository,
                          PgClient pgClient) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.pgClient = pgClient;
    }

    @Transactional
    public PaymentResponse processPayment(Long userId, String idempotencyKey) {
        Optional<Payment> existingPayment = paymentRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            if ("SUCCESS".equals(payment.getStatus()) || "FAIL".equals(payment.getStatus())) {
                return toResponse(payment);
            }
        }

        Payment payment = existingPayment.orElseGet(() -> {
            Payment p = new Payment(userId, PRO_PLAN_AMOUNT, idempotencyKey);
            p.setStatus("PENDING");
            return paymentRepository.save(p);
        });

        if ("INIT".equals(payment.getStatus())) {
            payment.setStatus("PENDING");
            paymentRepository.save(payment);
        }

        PgResult result = null;
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String scopedKey = userId + ":" + idempotencyKey;
                PgRequest pgRequest = new PgRequest(scopedKey, PRO_PLAN_AMOUNT, "TaskFlow Pro Plan");
                result = pgClient.requestPayment(pgRequest);
                lastException = null;
                break;
            } catch (PgTimeoutException e) {
                log.warn("PG timeout on attempt {}/{} for key={}", attempt, MAX_RETRIES, idempotencyKey);
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    sleepBackoff(attempt);
                }
            } catch (PgSystemException e) {
                log.warn("PG system error on attempt {}/{} for key={}", attempt, MAX_RETRIES, idempotencyKey);
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    sleepBackoff(attempt);
                }
            }
        }

        if (lastException != null) {
            payment.setStatus("FAIL");
            payment.setFailReason("SYSTEM_ERROR_AFTER_RETRIES");
            paymentRepository.save(payment);
            return toResponse(payment);
        }

        if (result.success()) {
            payment.setStatus("SUCCESS");
            payment.setPgTransactionId(result.pgTransactionId());
            paymentRepository.save(payment);

            activateProPlan(userId);

            return toResponse(payment);
        } else {
            payment.setStatus("FAIL");
            payment.setFailReason(result.errorCode());
            paymentRepository.save(payment);
            return toResponse(payment);
        }
    }

    private void activateProPlan(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && !"PRO".equals(user.getPlan())) {
            user.setPlan("PRO");
            userRepository.save(user);
            log.info("User {} upgraded to PRO plan", userId);
        }
    }

    public List<PaymentResponse> getPaymentHistory(Long userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getStatus(),
                payment.getPgTransactionId(),
                payment.getFailReason(),
                payment.getAmount()
        );
    }

    private void sleepBackoff(int attempt) {
        try {
            long delay = (long) Math.pow(2, attempt) * 100;
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
