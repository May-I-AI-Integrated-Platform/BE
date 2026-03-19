package ai.Mayi.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PerformanceMetrics {

    private final MeterRegistry meterRegistry;

    public PerformanceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordError(String endpoint, String errorType) {
        meterRegistry.counter("api.errors",
                Tags.of("endpoint", endpoint, "error.type", errorType)
        ).increment();
    }

    public void recordResponseTime(String endpoint, Duration duration) {
        meterRegistry.timer("api.response.time",
                Tags.of("endpoint", endpoint)
        ).record(duration);
    }
}
