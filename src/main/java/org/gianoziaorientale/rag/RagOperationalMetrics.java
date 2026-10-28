package org.gianoziaorientale.rag;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class RagOperationalMetrics {

    private final MeterRegistry registry;

    public RagOperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> T time(String name, Supplier<T> operation) {
        return registry.timer(name).record(operation);
    }

    public void increment(String name, String tag, String value) {
        registry.counter(name, tag, value).increment();
    }

    public void increment(String name, String tag, String value, double amount) {
        if (amount > 0) {
            registry.counter(name, tag, value).increment(amount);
        }
    }

    public void increment(String name) {
        registry.counter(name).increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stop(Timer.Sample sample, String name) {
        sample.stop(registry.timer(name));
    }
}
