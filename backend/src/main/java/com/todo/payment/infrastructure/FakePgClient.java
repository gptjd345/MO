package com.todo.payment.infrastructure;

import com.todo.payment.domain.PgClient;
import com.todo.payment.domain.PgRequest;
import com.todo.payment.domain.PgResult;
import com.todo.payment.domain.PgTimeoutException;
import com.todo.payment.domain.PgSystemException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FakePgClient implements PgClient {

    private final Map<String, PgResult> idempotencyStore = new ConcurrentHashMap<>();
    private final Random random = new Random();

    @Override
    public PgResult requestPayment(PgRequest request) {
        String key = request.idempotencyKey();

        if (idempotencyStore.containsKey(key)) {
            return idempotencyStore.get(key);
        }

        sleepRandom(50, 500);

        if (random.nextDouble() < 0.1) {
            throw new PgTimeoutException("PG Timeout");
        }

        PgResult result;
        double outcome = random.nextDouble();

        if (outcome < 0.7) {
            result = PgResult.success(UUID.randomUUID().toString());
        } else if (outcome < 0.85) {
            result = PgResult.fail("INSUFFICIENT_FUNDS");
        } else if (outcome < 0.90) {
            result = PgResult.fail("DUPLICATE_REQUEST");
        } else {
            throw new PgSystemException("PG Internal Error");
        }

        idempotencyStore.put(key, result);
        return result;
    }

    private void sleepRandom(int minMs, int maxMs) {
        try {
            int delay = random.nextInt(maxMs - minMs + 1) + minMs;
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
