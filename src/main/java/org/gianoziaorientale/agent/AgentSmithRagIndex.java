package org.gianoziaorientale.agent;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

public final class AgentSmithRagIndex {

    public static final String REQUIRED_PREFIX = "agentsmith-rag-v1-";
    public static final String DEFAULT_ALIAS = REQUIRED_PREFIX + "active";
    public static final String INDEX_PATTERN = REQUIRED_PREFIX + "*";

    private static final Pattern ALIAS_PATTERN = Pattern.compile(
            "^" + Pattern.quote(REQUIRED_PREFIX) + "[a-z0-9][a-z0-9._-]{0,200}$");

    private AgentSmithRagIndex() {}

    public static String validateAlias(String alias) {
        if (alias == null || !ALIAS_PATTERN.matcher(alias).matches()) {
            throw new IllegalArgumentException(
                    "RAG Elasticsearch alias must start with " + REQUIRED_PREFIX
                            + " and contain only lowercase letters, digits, '.', '_' or '-'.");
        }
        return alias;
    }

    public static URI validateHttpsEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("RAG Elasticsearch HTTPS endpoint is required.");
        }
        try {
            URI uri = new URI(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65_535))
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException(
                        "RAG Elasticsearch endpoint must be an HTTPS URL without embedded credentials, query, or fragment.");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("RAG Elasticsearch endpoint is not a valid URL.", e);
        }
    }
}
