package org.gianoziaorientale.ingestor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gianoziaorientale.agent.AgentSmithRagIndex;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

final class ElasticsearchDocumentIndex implements DocumentIndex {

    private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");

    private final RestClient client;
    private final String alias;

    ElasticsearchDocumentIndex(RestClient client, String alias) {
        this.client = client;
        this.alias = AgentSmithRagIndex.validateAlias(alias);
    }

    @Override
    public void index(List<IndexedDocumentChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        StringBuilder payload = new StringBuilder();
        for (IndexedDocumentChunk chunk : chunks) {
            payload.append("{\"index\":{\"_index\":\"").append(json(alias))
                    .append("\",\"_id\":\"").append(json(chunk.id())).append("\"}}\n");
            payload.append(documentJson(chunk)).append('\n');
        }
        JsonNode response = client.post()
                .uri("/_bulk?refresh=wait_for")
                .contentType(NDJSON)
                .body(payload.toString())
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.path("errors").asBoolean(true)) {
            throw new IllegalStateException("Elasticsearch rejected one or more document chunks.");
        }
    }

    @Override
    public void deleteChunksAtOrAbove(String sourceId, int firstStaleChunkIndex) {
        Map<String, Object> query = Map.of("query", Map.of("bool", Map.of("filter", List.of(
                Map.of("term", Map.of("sourceId", sourceId)),
                Map.of("range", Map.of("chunkIndex", Map.of("gte", firstStaleChunkIndex)))))));
        deleteByQuery(query);
    }

    @Override
    public void deleteFilesNotInCorpus(String corpusId, Set<String> currentFileIds) {
        Map<String, Object> bool = currentFileIds.isEmpty()
                ? Map.of("filter", List.of(Map.of("term", Map.of("corpusId", corpusId))))
                : Map.of(
                        "filter", List.of(Map.of("term", Map.of("corpusId", corpusId))),
                        "must_not", List.of(Map.of("terms", Map.of("fileId", currentFileIds))));
        deleteByQuery(Map.of("query", Map.of("bool", bool)));
    }

    private void deleteByQuery(Map<String, Object> query) {
        JsonNode response = client.post()
                .uri("/{alias}/_delete_by_query?conflicts=proceed&refresh=true", alias)
                .contentType(MediaType.APPLICATION_JSON)
                .body(query)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || (response.path("failures").isArray()
                && !response.path("failures").isEmpty())) {
            throw new IllegalStateException("Elasticsearch could not safely remove stale document chunks.");
        }
    }

    private static String documentJson(IndexedDocumentChunk chunk) {
        String vector = java.util.Arrays.stream(toDoubleArray(chunk.embedding()))
                .mapToObj(value -> Double.toString(value))
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"embedding\":" + vector
                + ",\"text\":\"" + json(chunk.text()) + "\""
                + ",\"sourceId\":\"" + json(chunk.sourceId()) + "\""
                + ",\"path\":\"" + json(chunk.path()) + "\""
                + ",\"startLine\":" + chunk.startLine()
                + ",\"endLine\":" + chunk.endLine()
                + ",\"corpusId\":\"" + json(chunk.corpusId()) + "\""
                + ",\"fileId\":\"" + json(chunk.fileId()) + "\""
                + ",\"contentHash\":\"" + json(chunk.contentHash()) + "\""
                + ",\"chunkIndex\":" + chunk.chunkIndex() + "}";
    }

    private static double[] toDoubleArray(float[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            if (!Float.isFinite(values[i])) {
                throw new IllegalArgumentException("Embedding contains a non-finite value.");
            }
            result[i] = values[i];
        }
        return result;
    }

    private static String json(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
