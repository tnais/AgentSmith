package org.gianoziaorientale.rag;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.micrometer.core.instrument.Metrics;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.gianoziaorientale.agent.AgentSmithRagIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Service
@ConditionalOnProperty(prefix = "agentsmith.rag", name = "enabled", havingValue = "true")
public class RagService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagService.class);
    private static final String EMBEDDING_FIELD = "embedding";
    private static final String TEXT_FIELD = "text";
    private static final Pattern SOURCE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,256}");

    private final EmbeddingModel embeddingModel;
    private final RestClient elasticsearch;
    private final RagAnswerGenerator answerGenerator;
    private final RagProperties properties;
    private final RagModelSelectionStore modelSelectionStore;
    private final RagOperationalMetrics metrics;

    public RagService(
            EmbeddingModel embeddingModel,
            RestClient ragElasticsearchRestClient,
            RagAnswerGenerator answerGenerator,
            RagProperties properties,
            RagModelSelectionStore modelSelectionStore) {
        this(embeddingModel, ragElasticsearchRestClient, answerGenerator, properties, modelSelectionStore,
                new RagOperationalMetrics(Metrics.globalRegistry));
    }

    @Autowired
    public RagService(
            EmbeddingModel embeddingModel,
            RestClient ragElasticsearchRestClient,
            RagAnswerGenerator answerGenerator,
            RagProperties properties,
            RagModelSelectionStore modelSelectionStore,
            RagOperationalMetrics metrics) {
        this.embeddingModel = embeddingModel;
        this.elasticsearch = ragElasticsearchRestClient;
        this.answerGenerator = answerGenerator;
        this.properties = properties;
        this.modelSelectionStore = modelSelectionStore;
        this.metrics = metrics;
    }

    public RagResponse query(String question) {
        try {
            return metrics.time("agentsmith.rag.query.duration", () -> queryInternal(question));
        } catch (RuntimeException exception) {
            metrics.increment("agentsmith.rag.queries", "outcome", "failure");
            LOGGER.warn("RAG query failed: errorType={}", exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private RagResponse queryInternal(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("La domanda non può essere vuota.");
        }
        if (question.length() > properties.getMaxQuestionCharacters()) {
            throw new IllegalArgumentException("La domanda supera la lunghezza massima consentita.");
        }

        var embeddingResponse = metrics.time("agentsmith.rag.embedding.duration", () -> embeddingModel.embed(question));
        if (embeddingResponse == null || embeddingResponse.content() == null
                || embeddingResponse.content().vector() == null) {
            throw new IllegalStateException("Embedding model returned an empty vector.");
        }
        float[] vector = embeddingResponse.content().vector();
        if (vector.length != properties.getEmbeddingDimensions()) {
            throw new IllegalStateException("Embedding dimension does not match the configured index mapping.");
        }

        JsonNode searchResponse = metrics.time("agentsmith.rag.retrieval.duration", () -> elasticsearch.post()
                .uri("/{alias}/_search", AgentSmithRagIndex.validateAlias(properties.getElasticsearchIndexAlias()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(searchBody(vector))
                .retrieve()
                .body(JsonNode.class));

        List<RetrievedChunk> chunks = parseChunks(searchResponse);
        AssembledContext assembledContext = assembleContext(chunks);
        if (assembledContext.text().isBlank()) {
            metrics.increment("agentsmith.rag.queries", "outcome", "no_results");
            return new RagResponse(
                    "Non ho trovato contenuti pertinenti nel corpus Agent Smith.",
                    List.of());
        }

        String selectedModelId = modelSelectionStore.selectedModelId();
        final String answerModelId = selectedModelId == null || selectedModelId.isBlank()
                ? properties.getAnswerModelId()
                : selectedModelId;
        String answer = metrics.time("agentsmith.rag.answer.duration",
                () -> answerGenerator.answer(question, assembledContext.text(), answerModelId));
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("Il modello ha restituito una risposta vuota.");
        }
        metrics.increment("agentsmith.rag.queries", "outcome", "success");
        return new RagResponse(answer, assembledContext.sources());
    }

    private Map<String, Object> searchBody(float[] vector) {
        List<Float> queryVector = new ArrayList<>(vector.length);
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException("Embedding model returned a non-finite vector component.");
            }
            queryVector.add(value);
        }
        int candidates = Math.max(50, properties.getMaxResults() * 10);
        return Map.of(
                "size", properties.getMaxResults(),
                "_source", List.of(TEXT_FIELD, "sourceId", "path", "startLine", "endLine"),
                "knn", Map.of(
                        "field", EMBEDDING_FIELD,
                        "query_vector", queryVector,
                        "k", properties.getMaxResults(),
                        "num_candidates", candidates));
    }

    private List<RetrievedChunk> parseChunks(JsonNode response) {
        JsonNode hits = response == null ? null : response.path("hits").path("hits");
        if (hits == null || !hits.isArray()) {
            throw new IllegalStateException("Elasticsearch ha restituito una risposta di ricerca non valida.");
        }

        List<RetrievedChunk> chunks = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            String text = source.path(TEXT_FIELD).asText("");
            String sourceId = source.path("sourceId").asText("");
            String path = source.path("path").asText("");
            int startLine = source.path("startLine").asInt(0);
            int endLine = source.path("endLine").asInt(0);
            if (text.isBlank()) {
                throw new IllegalStateException("Elasticsearch returned a chunk without text.");
            }
            if (!SOURCE_ID_PATTERN.matcher(sourceId).matches()
                    || !isSafeRelativePath(path)
                    || startLine < 1
                    || endLine < startLine) {
                throw new IllegalStateException("Elasticsearch returned invalid RAG source metadata.");
            }
            chunks.add(new RetrievedChunk(
                    text, new SourceReference(sourceId, path.replace('\\', '/'), startLine, endLine)));
        }
        return List.copyOf(chunks);
    }

    private AssembledContext assembleContext(List<RetrievedChunk> chunks) {
        StringBuilder context = new StringBuilder();
        Set<SourceReference> sources = new LinkedHashSet<>();
        for (RetrievedChunk chunk : chunks) {
            if (context.length() >= properties.getMaxContextCharacters()) {
                break;
            }
            String heading = "\n[Source " + chunk.source().sourceId()
                    + " | " + chunk.source().path()
                    + ":" + chunk.source().startLine() + "-" + chunk.source().endLine() + "]\n";
            int remaining = properties.getMaxContextCharacters() - context.length() - heading.length();
            if (remaining <= 0) {
                break;
            }
            int includedTextLength = Math.min(chunk.text().length(), remaining);
            if (includedTextLength > 0) {
                context.append(heading).append(chunk.text(), 0, includedTextLength);
                sources.add(chunk.source());
            }
        }
        return new AssembledContext(context.toString(), List.copyOf(sources));
    }

    private static boolean isSafeRelativePath(String path) {
        if (path == null || path.isBlank() || path.length() > 1_024
                || path.startsWith("/") || path.startsWith("\\")
                || path.matches("^[A-Za-z]:.*")) {
            return false;
        }
        for (String segment : path.replace('\\', '/').split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return path.chars().noneMatch(Character::isISOControl);
    }

    public record RagResponse(String answer, List<SourceReference> sources) {
        public RagResponse {
            sources = List.copyOf(sources);
        }
    }

    public record SourceReference(String sourceId, String path, int startLine, int endLine) {}

    private record RetrievedChunk(String text, SourceReference source) {}

    private record AssembledContext(String text, List<SourceReference> sources) {}
}
