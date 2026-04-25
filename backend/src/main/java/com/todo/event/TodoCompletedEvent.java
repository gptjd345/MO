package com.todo.event;

import java.time.LocalDate;

public class TodoCompletedEvent implements Event {

    private final Long userId;
    private final Long todoId;
    private final LocalDate occurredAt;

    public TodoCompletedEvent(Long userId, Long todoId, LocalDate occurredAt) {
        this.userId = userId;
        this.todoId = todoId;
        this.occurredAt = occurredAt;
    }

    @Override
    public String getEventType() { return "COMPLETED"; }

    @Override
    public LocalDate getOccurredAt() { return occurredAt; }

    public Long getUserId() { return userId; }
    public Long getTodoId() { return todoId; }
}