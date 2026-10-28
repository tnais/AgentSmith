package org.gianoziaorientale.rag;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import java.time.Duration;
import org.gianoziaorientale.agent.RestClientSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "agentsmith.rag", name = "enabled", havingValue = "true")
class RagConfiguration {

    @Bean
    EmbeddingModel ragEmbeddingModel(RagProperties properties, @Value("${gemini.api-key:}") String apiKey) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is required when Agent Smith RAG is enabled.");
        }
        return GoogleAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(properties.getEmbeddingModel())
                .outputDimensionality(properties.getEmbeddingDimensions())
                .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_QUERY)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    RestClient ragElasticsearchRestClient(RagProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getElasticsearchUrl().replaceAll("/+$", ""))
                .defaultHeader("Authorization", "ApiKey " + properties.getElasticsearchReadApiKey())
                .requestFactory(RestClientSupport.requestFactory())
                .build();
    }
}
