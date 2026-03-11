package com.todo.payment.domain;

public record PgRequest(String idempotencyKey, Long amount, String description) {}
