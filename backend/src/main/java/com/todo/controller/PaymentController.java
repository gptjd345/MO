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

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<PaymentResponse> subscribe(@AuthenticationPrincipal User user,
                                                     @Valid @RequestBody PaymentRequest request) {
        paymentService.validateNotProPlan(user.getId());
        return ResponseEntity.ok(paymentService.processPayment(user.getId(), request.getIdempotencyKey()));
    }

    @GetMapping("/history")
    public ResponseEntity<List<PaymentResponse>> history(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(paymentService.getPaymentHistory(user.getId()));
    }
}
