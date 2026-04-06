package com.todo.stats.batch;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.PushGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BatchMetricsPusher {

    private static final Logger log = LoggerFactory.getLogger(BatchMetricsPusher.class);

    private final PushGateway pushGateway;

    public BatchMetricsPusher(@Value("${pushgateway.url:pushgateway:9091}") String pushGatewayUrl) {
        this.pushGateway = new PushGateway(pushGatewayUrl);
    }

    public void push(String jobName, long durationMs, boolean success) {
        CollectorRegistry registry = new CollectorRegistry();

        Gauge.build("batch_job_duration_seconds", "Batch job duration in seconds")
                .labelNames("job").register(registry)
                .labels(jobName).set(durationMs / 1000.0);

        Gauge.build("batch_job_last_run_timestamp_seconds", "Batch job last run Unix timestamp")
                .labelNames("job").register(registry)
                .labels(jobName).set(System.currentTimeMillis() / 1000.0);

        Gauge.build("batch_job_success", "1 if last batch job succeeded, 0 otherwise")
                .labelNames("job").register(registry)
                .labels(jobName).set(success ? 1.0 : 0.0);

        try {
            pushGateway.pushAdd(registry, jobName);
            log.info("Pushed batch metrics to Pushgateway: job={}, duration={}ms, success={}", jobName, durationMs, success);
        } catch (Exception e) {
            log.warn("Failed to push batch metrics to Pushgateway: {}", e.getMessage());
        }
    }
}