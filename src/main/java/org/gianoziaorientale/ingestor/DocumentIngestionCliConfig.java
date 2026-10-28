package org.gianoziaorientale.ingestor;

import org.gianoziaorientale.agent.AgentSmithRagIndex;

import java.net.URI;
import java.util.Map;

record DocumentIngestionCliConfig(
        URI agentSmithUrl,
        String operatorUsername,
        String operatorPassword,
        String geminiApiKey,
        String embeddingModel,
        int embeddingDimensions,
        IngestionElasticsearchProperties elasticsearch) {

    static DocumentIngestionCliConfig fromEnvironment(Map<String, String> environment) {
        String agentSmithUrl = required(environment, "AGENTSMITH_BASE_URL");
        URI baseUri = validateAgentSmithUrl(agentSmithUrl);
        String username = required(environment, "AGENTSMITH_RAG_OPERATOR_USERNAME");
        String password = required(environment, "AGENTSMITH_RAG_OPERATOR_PASSWORD");
        String apiKey = required(environment, "GEMINI_API_KEY");
        String embeddingModel = required(environment, "EMBEDDING_MODEL");
        int embeddingDimensions;
        try {
            embeddingDimensions = Integer.parseInt(required(environment, "EMBEDDING_DIMENSIONS"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("EMBEDDING_DIMENSIONS must be a positive integer.", exception);
        }
        if (embeddingDimensions <= 0) {
            throw new IllegalArgumentException("EMBEDDING_DIMENSIONS must be a positive integer.");
        }

        IngestionElasticsearchProperties elasticsearch = new IngestionElasticsearchProperties();
        elasticsearch.setUrl(required(environment, "ELASTICSEARCH_URL"));
        elasticsearch.setWriteApiKey(required(environment, "AGENTSMITH_ES_WRITE_API_KEY"));
        elasticsearch.setIndexAlias(environment.getOrDefault(
                "RAG_INDEX_ALIAS", AgentSmithRagIndex.DEFAULT_ALIAS));
        elasticsearch.validateForIngestion();

        return new DocumentIngestionCliConfig(
                baseUri, username, password, apiKey, embeddingModel, embeddingDimensions, elasticsearch);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set in the CLI environment.");
        }
        return value;
    }

    private static URI validateAgentSmithUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("AGENTSMITH_BASE_URL is not a valid URL.", exception);
        }
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost())
                        || "127.0.0.1".equals(uri.getHost())
                        || "::1".equals(uri.getHost()));
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        if ((!https && !localHttp) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException(
                    "AGENTSMITH_BASE_URL must use HTTPS (HTTP is allowed only for a local loopback server).");
        }
        return uri;
    }
}
