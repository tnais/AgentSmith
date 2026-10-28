package org.gianoziaorientale.rag;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("ragReadiness")
public class RagConfigurationHealthIndicator implements HealthIndicator {

    private final RagProperties properties;

    public RagConfigurationHealthIndicator(RagProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.up().withDetail("enabled", false).build();
        }
        properties.validate();
        return Health.up().withDetail("enabled", true).withDetail("configured", true).build();
    }
}
