package com.todo.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TodoEventListener {

    private static final Logger log = LoggerFactory.getLogger(TodoEventListener.class);

    private final EventPublisher eventPublisher;

    public TodoEventListener(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTodoCompleted(TodoCompletedEvent event) {
        try {
            eventPublisher.publish(event);
        } catch (Exception e) {
            log.error("Redis publish failed for COMPLETED todoId={}: {}", event.getTodoId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTodoCanceled(TodoCanceledEvent event) {
        try {
            eventPublisher.publish(event);
        } catch (Exception e) {
            log.error("Redis publish failed for CANCELED todoId={}: {}", event.getTodoId(), e.getMessage());
        }
    }
}