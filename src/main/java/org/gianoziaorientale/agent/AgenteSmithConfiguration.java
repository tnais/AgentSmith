package org.gianoziaorientale.agent;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import org.gianoziaorientale.rag.RagProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RagProperties.class)
public class AgenteSmithConfiguration {

    static final String DEFAULT_GEMINI_MODEL = "gemini-3.8-flash";

    @Bean
    public AgenteSmith agenteSmith(@Value("${gemini.api-key:}") String apiKey) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("La variabile d'ambiente GEMINI_API_KEY non è configurata");
        }
        return createAgent(apiKey, DEFAULT_GEMINI_MODEL);
    }

    static AgenteSmith createAgent(String apiKey, String modelName) {
        ChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.2)
                .timeout(Duration.ofMinutes(6))
                .build();
        return AiServices.builder(AgenteSmith.class)
                .chatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }
}
