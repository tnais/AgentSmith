package org.gianoziaorientale.rag;

import org.gianoziaorientale.agent.AgentSmithRagIndex;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentsmith.rag")
public class RagProperties implements InitializingBean {

    public static final long SMALL_FILE_MAX_BYTES = DocumentFilePolicy.SMALL_FILE_MAX_BYTES;

    private boolean enabled;
    private String answerModelId = "";
    private String embeddingModel = "";
    private int embeddingDimensions;
    private String elasticsearchUrl = "";
    private String elasticsearchReadApiKey = "";
    private String elasticsearchIndexAlias = AgentSmithRagIndex.DEFAULT_ALIAS;
    private int maxResults = 5;
    private int maxQuestionCharacters = 4_000;
    private int maxContextCharacters = 20_000;
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAnswerModelId() {
        return answerModelId;
    }

    public void setAnswerModelId(String answerModelId) {
        this.answerModelId = answerModelId;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public String getElasticsearchUrl() {
        return elasticsearchUrl;
    }

    public void setElasticsearchUrl(String elasticsearchUrl) {
        this.elasticsearchUrl = elasticsearchUrl;
    }

    public String getElasticsearchReadApiKey() {
        return elasticsearchReadApiKey;
    }

    public void setElasticsearchReadApiKey(String elasticsearchReadApiKey) {
        this.elasticsearchReadApiKey = elasticsearchReadApiKey;
    }

    public String getElasticsearchIndexAlias() {
        return elasticsearchIndexAlias;
    }

    public void setElasticsearchIndexAlias(String elasticsearchIndexAlias) {
        this.elasticsearchIndexAlias = elasticsearchIndexAlias;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public int getMaxQuestionCharacters() {
        return maxQuestionCharacters;
    }

    public void setMaxQuestionCharacters(int maxQuestionCharacters) {
        this.maxQuestionCharacters = maxQuestionCharacters;
    }

    public int getMaxContextCharacters() {
        return maxContextCharacters;
    }

    public void setMaxContextCharacters(int maxContextCharacters) {
        this.maxContextCharacters = maxContextCharacters;
    }

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    void validate() {
        AgentSmithRagIndex.validateAlias(elasticsearchIndexAlias);
        if (elasticsearchUrl != null && !elasticsearchUrl.isBlank()) {
            AgentSmithRagIndex.validateHttpsEndpoint(elasticsearchUrl);
        }
        if (enabled) {
            AgentSmithRagIndex.validateHttpsEndpoint(elasticsearchUrl);
            if (elasticsearchReadApiKey == null || elasticsearchReadApiKey.isBlank()) {
                throw new IllegalStateException(
                        "AGENTSMITH_ES_READ_API_KEY is required when Agent Smith RAG is enabled.");
            }
            if (answerModelId == null || answerModelId.isBlank()) {
                throw new IllegalStateException(
                        "RAG answer model selection is required when Agent Smith RAG is enabled.");
            }
            if (embeddingModel == null || embeddingModel.isBlank() || embeddingDimensions <= 0) {
                throw new IllegalStateException(
                        "RAG embedding model and positive embedding dimensions are required when RAG is enabled.");
            }
            if (maxResults < 1 || maxResults > 20
                    || maxQuestionCharacters < 1 || maxQuestionCharacters > 20_000
                    || maxContextCharacters < 256 || maxContextCharacters > 100_000) {
                throw new IllegalStateException("RAG query limits are outside the supported range.");
            }
        }
    }
}
