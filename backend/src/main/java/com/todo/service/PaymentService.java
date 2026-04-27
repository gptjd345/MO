package com.todo.service;

import com.todo.dto.PaymentResponse;
import com.todo.entity.Payment;
import com.todo.entity.User;
import com.todo.payment.domain.PgClient;
import com.todo.payment.domain.PgRequest;
import com.todo.payment.domain.PgResult;
import com.todo.payment.domain.PgTimeoutException;
import com.todo.payment.domain.PgSystemException;
import com.todo.exception.CustomException;
import com.todo.exception.ErrorCode;
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

    public void validateNotProPlan(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if ("PRO".equals(user.getPlan())) {
            throw new CustomException(ErrorCode.ALREADY_PRO_PLAN);
        }
    }

    public void validateProPlan(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!"PRO".equals(user.getPlan())) {
            throw new CustomException(ErrorCode.NOT_PRO_PLAN);
        }
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

        PgResult result;
        try {
            String scopedKey = userId + ":" + idempotencyKey;
            PgRequest pgRequest = new PgRequest(scopedKey, PRO_PLAN_AMOUNT, "TaskFlow Pro Plan");
            result = pgClient.requestPayment(pgRequest);
        } catch (PgTimeoutException e) {
            log.warn("PG timeout for key={}", idempotencyKey);
            payment.setStatus("FAIL");
            payment.setFailReason("PG_TIMEOUT");
            paymentRepository.save(payment);
            return toResponse(payment);
        } catch (PgSystemException e) {
            log.warn("PG system error for key={}", idempotencyKey);
            payment.setStatus("FAIL");
            payment.setFailReason("PG_SYSTEM_ERROR");
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

}
