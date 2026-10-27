package org.gianoziaorientale.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import dev.langchain4j.service.Result;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ModuliAiService {

    Logger logger = LoggerFactory.getLogger(ModuliAiService.class);
    private static final String GEMINI_MODELS_PATH = "/v1beta/models";
    private static final String GEMINI_MODEL_PREFIX = "gemini:";
    private static final String GEMINI_ID = GEMINI_MODEL_PREFIX + AgenteSmithConfiguration.DEFAULT_GEMINI_MODEL;
    private static final String OPEN_WEBUI_PREFIX = "openwebui:";
    private static final Set<String> IRRELEVANT_MODEL_MARKERS = Set.of(
            "banana", "imagen", "veo", "embedding", "embed", "audio", "speech", "tts", "live", "robotics");

    private final String baseUrl;
    private final RestClient restClient;
    private final String geminiBaseUrl;
    private final RestClient geminiRestClient;
    private final String geminiApiKey;

    public ModuliAiService(
            @Value("${OPEN_WEB_UI_BASE_URL:https://openwebui.mygor.xyz}") String baseUrl,
            @Value("${OPEN_WEB_UI_API_KEY:}") String apiKey,
            @Value("${gemini.api-key:}") String geminiApiKey,
            @Value("${GEMINI_API_BASE_URL:https://generativelanguage.googleapis.com}") String geminiBaseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        RestClient.Builder builder = RestClient.builder();
        if (!apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        this.restClient = builder.build();
        this.geminiApiKey = geminiApiKey;
        this.geminiBaseUrl = geminiBaseUrl.replaceAll("/+$", "");
        this.geminiRestClient = RestClient.builder()
                .defaultHeader("x-goog-api-key", geminiApiKey)
                .build();
    }

    public List<Modulo> moduliDisponibili() {
        Map<String, Modulo> moduli = new LinkedHashMap<>();
        moduli.putAll(moduliGemini());

        JsonNode response = restClient.get()
                .uri(baseUrl + "/api/models")
                .retrieve()
                .body(JsonNode.class);
        JsonNode models = response == null ? null : response.isArray() ? response : response.path("data");
        if (models == null || !models.isArray()) {
            throw new IllegalStateException("Open WebUI ha restituito un elenco di modelli non valido");
        }

        for (JsonNode model : models) {
            String id = model.path("id").asText("");
            if (!id.isBlank()) {
                String name = model.path("name").asText(id);
                Modulo modulo = new Modulo(OPEN_WEBUI_PREFIX + id, name.isBlank() ? id : name);
                moduli.putIfAbsent(modulo.id(), modulo);
            }
        }
        return List.copyOf(moduli.values());
    }

    private Map<String, Modulo> moduliGemini() {
        if (geminiApiKey.isBlank()) {
            throw new IllegalStateException("La variabile d'ambiente GEMINI_API_KEY non è configurata");
        }

        Map<String, Modulo> moduli = new LinkedHashMap<>();
        String pageToken = null;
        do {
            String requestedPageToken = pageToken;
            JsonNode response = geminiRestClient.get()
                    .uri(UriComponentsBuilder.fromUriString(geminiBaseUrl)
                            .path(GEMINI_MODELS_PATH)
                            .queryParam("pageSize", 100)
                            .queryParamIfPresent("pageToken", Optional.ofNullable(requestedPageToken))
                            .build()
                            .encode()
                            .toUri())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode models = response == null ? null : response.path("models");
            if (models == null || !models.isArray()) {
                throw new IllegalStateException("Gemini ha restituito un elenco di modelli non valido");
            }
            for (JsonNode model : models) {
                String resourceName = model.path("name").asText("");
                String modelName = resourceName.startsWith("models/")
                        ? resourceName.substring("models/".length())
                        : resourceName;
                if (!modelName.isBlank()) {
                    String displayName = model.path("displayName").asText(modelName);
                    if (isUsefulForCodeAnalysis(
                            model.path("supportedGenerationMethods"), modelName, displayName)) {
                        logger.debug(displayName + " - " + modelName);
                        Modulo modulo = new Modulo(
                                GEMINI_MODEL_PREFIX + modelName,
                                displayName.isBlank() ? modelName : displayName);
                        moduli.putIfAbsent(modulo.id(), modulo);
                    }
                }
            }
            pageToken = response.path("nextPageToken").asText("");
            if (pageToken.isBlank()) {
                pageToken = null;
            }
        } while (pageToken != null);

        return moduli;
    }

    private static boolean isUsefulForCodeAnalysis(JsonNode methods, String modelName, String displayName) {
        if (!supportaGenerazioneContenuti(methods)) {
            return false;
        }
        String metadata = (modelName + " " + displayName).toLowerCase(Locale.ROOT);
        return IRRELEVANT_MODEL_MARKERS.stream().noneMatch(metadata::contains);
    }

    private static boolean supportaGenerazioneContenuti(JsonNode methods) {
        if (!methods.isArray()) {
            return false;
        }
        for (JsonNode method : methods) {
            if ("generateContent".equals(method.asText())) {
                return true;
            }
        }
        return false;
    }

    public Risposta spiega(
            AgenteSmith agenteSmith, String memoryId, String linguaggio, String codice, String moduloId) {
        if (moduloId == null || moduloId.isBlank() || GEMINI_ID.equals(moduloId)) {
            return rispostaGemini(agenteSmith.ciacola(memoryId, linguaggio, codice));
        }
        if (moduloId.startsWith(GEMINI_MODEL_PREFIX)) {
            String modelName = moduloId.substring(GEMINI_MODEL_PREFIX.length());
            if (modelName.isBlank() || !moduliGemini().containsKey(moduloId)) {
                throw new IllegalArgumentException("Il modello Gemini selezionato non è disponibile");
            }
            return rispostaGemini(AgenteSmithConfiguration.createAgent(geminiApiKey, modelName)
                    .ciacola(memoryId, linguaggio, codice));
        }
        if (!moduloId.startsWith(OPEN_WEBUI_PREFIX)) {
            throw new IllegalArgumentException("Modulo AI non riconosciuto");
        }

        String modelId = moduloId.substring(OPEN_WEBUI_PREFIX.length());
        boolean disponibile = moduliDisponibili().stream()
                .anyMatch(modulo -> modulo.id().equals(moduloId));
        if (!disponibile) {
            throw new IllegalArgumentException("Il modello Open WebUI selezionato non è disponibile");
        }

        JsonNode response = restClient.post()
                .uri(baseUrl + "/api/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", modelId,
                        "temperature", 0.2,
                        "messages", List.of(
                                Map.of("role", "system", "content",
                                        "Sei un esperto programmatore nel linguaggio " + linguaggio + "."),
                                Map.of("role", "user", "content",
                                        "Cosa fa il seguente codice in linguaggio " + linguaggio + "?\n" + codice))))
                .retrieve()
                .body(JsonNode.class);

        String answer = response == null
                ? ""
                : response.path("choices").path(0).path("message").path("content").asText("");
        if (answer.isBlank()) {
            throw new IllegalStateException("Open WebUI ha restituito una risposta vuota");
        }
        JsonNode usage = response == null ? null : response.path("usage");
        Integer tokenCount = tokenCount(usage, "total_tokens");
        if (tokenCount == null) {
            Integer inputTokens = tokenCount(usage, "prompt_tokens");
            Integer outputTokens = tokenCount(usage, "completion_tokens");
            if (inputTokens != null && outputTokens != null) {
                tokenCount = inputTokens + outputTokens;
            }
        }
        return new Risposta(answer, tokenCount);
    }

    private static Risposta rispostaGemini(Result<String> result) {
        Integer tokenCount = result.tokenUsage() == null
                ? null
                : result.tokenUsage().totalTokenCount();
        return new Risposta(result.content(), tokenCount);
    }

    private static Integer tokenCount(JsonNode usage, String field) {
        if (usage == null || !usage.has(field) || !usage.path(field).canConvertToInt()) {
            return null;
        }
        return usage.path(field).intValue();
    }

    public record Risposta(String spiegazione, Integer token) {}

    public record Modulo(String id, String nome) {}
}
