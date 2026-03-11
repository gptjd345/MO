package com.todo.payment.domain;

public interface PgClient {
    PgResult requestPayment(PgRequest request);
}
