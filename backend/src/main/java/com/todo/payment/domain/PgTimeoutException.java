package com.todo.payment.domain;

public class PgTimeoutException extends RuntimeException {
    public PgTimeoutException(String message) {
        super(message);
    }
}
