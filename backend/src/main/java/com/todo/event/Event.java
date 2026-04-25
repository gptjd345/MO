package com.todo.event;

import java.time.LocalDate;

public interface Event {
    String getEventType();
    LocalDate getOccurredAt();
}