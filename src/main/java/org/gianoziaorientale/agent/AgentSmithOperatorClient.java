package org.gianoziaorientale.agent;

import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public interface AgentSmithOperatorClient {

    List<ModuliAiService.Modulo> availableModels();

    void saveAnswerModel(String modelId);

    static AgentSmithOperatorClient create(URI baseUri, String username, String password) {
        String credentials = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RestClient client = RestClient.builder()
                .baseUrl(baseUri.toString().replaceAll("/+$", ""))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .requestFactory(RestClientSupport.requestFactory())
                .build();
        return new AgentSmithOperatorClient() {
            @Override
            public List<ModuliAiService.Modulo> availableModels() {
                ModuliAiService.Modulo[] models = client.get()
                        .uri("/moduli")
                        .retrieve()
                        .body(ModuliAiService.Modulo[].class);
                if (models == null) {
                    throw new IllegalStateException("Agent Smith returned an empty model catalog response.");
                }
                return List.copyOf(Arrays.asList(models));
            }

            @Override
            public void saveAnswerModel(String modelId) {
                client.post()
                        .uri("/api/rag/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new RagModelConfigurationRequest(modelId))
                        .retrieve()
                        .toBodilessEntity();
            }
        };
    }

    record RagModelConfigurationRequest(String answerModelId) {}
}
