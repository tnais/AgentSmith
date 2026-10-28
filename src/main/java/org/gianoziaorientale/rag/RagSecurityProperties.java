package org.gianoziaorientale.rag;

import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentsmith.security.rag")
public class RagSecurityProperties {

    private static final Pattern BCRYPT_HASH = Pattern.compile(
            "^\\{bcrypt}\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private boolean enabled;
    private String operatorUsername = "";
    private String operatorPasswordHash = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getOperatorUsername() {
        return operatorUsername;
    }

    public void setOperatorUsername(String operatorUsername) {
        this.operatorUsername = operatorUsername;
    }

    public String getOperatorPasswordHash() {
        return operatorPasswordHash;
    }

    public void setOperatorPasswordHash(String operatorPasswordHash) {
        this.operatorPasswordHash = operatorPasswordHash;
    }

    void validate() {
        if (!enabled) {
            return;
        }
        if (isBlank(operatorUsername) || isBlank(operatorPasswordHash)) {
            throw new IllegalStateException(
                    "RAG configuration writes require an operator username and password hash.");
        }
        if (!BCRYPT_HASH.matcher(operatorPasswordHash).matches()) {
            throw new IllegalStateException(
                    "RAG operator password must be a valid {bcrypt} hash.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
