package com.todo.payment.domain;

public class PgSystemException extends RuntimeException {
    public PgSystemException(String message) {
        super(message);
    }
}
