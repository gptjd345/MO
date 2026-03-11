package com.todo.controller;

import com.todo.dto.PaymentRequest;
import com.todo.dto.PaymentResponse;
import com.todo.entity.User;
import com.todo.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@AuthenticationPrincipal User user,
                                        @Valid @RequestBody PaymentRequest request) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        if ("PRO".equals(user.getPlan())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Already subscribed to Pro plan"));
        }

        PaymentResponse response = paymentService.processPayment(user.getId(), request.getIdempotencyKey());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        List<PaymentResponse> history = paymentService.getPaymentHistory(user.getId());
        return ResponseEntity.ok(history);
    }
}
