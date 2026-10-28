package org.gianoziaorientale.ingestor;

import org.gianoziaorientale.agent.AgentSmithRagIndex;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentsmith.ingestion.elasticsearch")
public class IngestionElasticsearchProperties {

    private String url = "";
    private String writeApiKey = "";
    private String indexAlias = AgentSmithRagIndex.DEFAULT_ALIAS;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getWriteApiKey() {
        return writeApiKey;
    }

    public void setWriteApiKey(String writeApiKey) {
        this.writeApiKey = writeApiKey;
    }

    public String getIndexAlias() {
        return indexAlias;
    }

    public void setIndexAlias(String indexAlias) {
        this.indexAlias = indexAlias;
    }

    public void validateForIngestion() {
        AgentSmithRagIndex.validateAlias(indexAlias);
        AgentSmithRagIndex.validateHttpsEndpoint(url);
        if (writeApiKey == null || writeApiKey.isBlank()) {
            throw new IllegalStateException(
                    "AGENTSMITH_ES_WRITE_API_KEY is required before document ingestion.");
        }
    }
}
