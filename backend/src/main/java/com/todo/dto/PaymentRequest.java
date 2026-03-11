package com.todo.dto;

import jakarta.validation.constraints.NotBlank;

public class PaymentRequest {

    @NotBlank
    private String idempotencyKey;

    public PaymentRequest() {}

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
