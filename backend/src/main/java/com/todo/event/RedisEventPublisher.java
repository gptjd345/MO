package com.todo.event;

import com.todo.messaging.StreamNames;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RedisEventPublisher implements EventPublisher {

    private final StringRedisTemplate redisTemplate;

    public RedisEventPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void publish(Event event) {
        redisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .ofMap(toPayload(event))
                        .withStreamKey(StreamNames.TODO)
        );
    }

    private Map<String, String> toPayload(Event event) {
        if (event instanceof TodoCompletedEvent e) {
            return Map.of(
                    "eventType", e.getEventType(),
                    "userId",    e.getUserId().toString(),
                    "todoId",    e.getTodoId().toString(),
                    "eventDate", e.getOccurredAt().toString()
            );
        }
        if (event instanceof TodoCanceledEvent e) {
            return Map.of(
                    "eventType", e.getEventType(),
                    "userId",    e.getUserId().toString(),
                    "todoId",    e.getTodoId().toString(),
                    "eventDate", e.getOccurredAt().toString()
            );
        }
        throw new IllegalArgumentException("Unsupported event: " + event.getClass().getSimpleName());
    }
}