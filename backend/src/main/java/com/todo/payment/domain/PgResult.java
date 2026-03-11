package com.todo.payment.domain;

public record PgResult(boolean success, String pgTransactionId, String errorCode) {

    public static PgResult success(String pgTransactionId) {
        return new PgResult(true, pgTransactionId, null);
    }

    public static PgResult fail(String errorCode) {
        return new PgResult(false, null, errorCode);
    }
}
