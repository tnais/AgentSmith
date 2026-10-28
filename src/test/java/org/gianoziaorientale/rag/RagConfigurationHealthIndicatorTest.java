package org.gianoziaorientale.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.gianoziaorientale.agent.RestClientSupport;
import org.junit.jupiter.api.Test;

class RagConfigurationHealthIndicatorTest {

    @Test
    void reportsConfiguredReadinessWithoutExposingConnectionSecrets() {
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        properties.setAnswerModelId("gemini:approved-model");
        properties.setEmbeddingModel("embedding-model");
        properties.setEmbeddingDimensions(768);
        properties.setElasticsearchUrl("https://elasticsearch.example");
        properties.setElasticsearchReadApiKey("a-sensitive-key");

        var health = new RagConfigurationHealthIndicator(properties).health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals(true, health.getDetails().get("configured"));
        assertTrue(!health.getDetails().toString().contains("a-sensitive-key"));
        assertTrue(!health.getDetails().toString().contains("elasticsearch.example"));
    }

    @Test
    void externalRestClientsHaveBoundedConnectAndReadTimeouts() {
        assertEquals(Duration.ofSeconds(5), RestClientSupport.CONNECT_TIMEOUT);
        assertEquals(Duration.ofSeconds(30), RestClientSupport.READ_TIMEOUT);
        assertTrue(RestClientSupport.requestFactory() != null);
    }
}
