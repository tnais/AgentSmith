package org.gianoziaorientale.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.gianoziaorientale.ingestor.IngestionElasticsearchProperties;
import org.gianoziaorientale.rag.RagProperties;
import org.junit.jupiter.api.Test;

class AgentSmithRagIndexTest {

    @Test
    void acceptsOnlyAgentSmithAliasesWithTheRequiredPrefix() {
        assertEquals(
                "agentsmith-rag-v1-active",
                AgentSmithRagIndex.validateAlias("agentsmith-rag-v1-active"));

        assertThrows(IllegalArgumentException.class,
                () -> AgentSmithRagIndex.validateAlias("other-app-rag-v1-active"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentSmithRagIndex.validateAlias("agentsmith-rag-v1-UpperCase"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentSmithRagIndex.validateAlias("agentsmith-rag-v1-unsafe*"));
    }

    @Test
    void requiresHttpsAndRejectsCredentialBearingOrAmbiguousEndpoints() {
        assertEquals("https", AgentSmithRagIndex.validateHttpsEndpoint(
                "https://elasticsearch.example:9200").getScheme());

        assertThrows(IllegalArgumentException.class,
                () -> AgentSmithRagIndex.validateHttpsEndpoint("http://elasticsearch.example:9200"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentSmithRagIndex.validateHttpsEndpoint("https://user:password@elasticsearch.example"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentSmithRagIndex.validateHttpsEndpoint("https://elasticsearch.example?index=other"));
    }

    @Test
    void requiresEndpointAndReadOnlyCredentialOnlyWhenRagIsEnabled() {
        RagProperties properties = new RagProperties();
        properties.afterPropertiesSet();

        properties.setEnabled(true);
        assertThrows(IllegalArgumentException.class, properties::afterPropertiesSet);

        properties.setElasticsearchUrl("https://elasticsearch.example:9200");
        properties.setAnswerModelId("gemini:approved-model");
        properties.setEmbeddingModel("configured-embedding-model");
        properties.setEmbeddingDimensions(768);
        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);

        properties.setElasticsearchReadApiKey("runtime-read-key");
        properties.afterPropertiesSet();
    }

    @Test
    void ingestionRequiresItsSeparateWriteCredentialAndTheSameScopedAlias() {
        IngestionElasticsearchProperties properties = new IngestionElasticsearchProperties();
        properties.setUrl("https://elasticsearch.example:9200");

        assertThrows(IllegalStateException.class, properties::validateForIngestion);
        properties.setWriteApiKey("cli-write-key");
        properties.validateForIngestion();

        properties.setIndexAlias("other-app-rag-v1");
        assertThrows(IllegalArgumentException.class, properties::validateForIngestion);
    }
}
