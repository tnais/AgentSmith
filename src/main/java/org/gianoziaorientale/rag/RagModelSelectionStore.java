package org.gianoziaorientale.rag;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RagModelSelectionStore {

    private static final String MODEL_ID_PROPERTY = "answerModelId";

    private final Path file;
    private volatile String selectedModelId;

    public RagModelSelectionStore(
            @Value("${agentsmith.rag.answer-model-id:}") String configuredModelId,
            @Value("${agentsmith.rag.config-file:./data/agentsmith-rag.properties}") String file) {
        this.file = Path.of(file).toAbsolutePath().normalize();
        this.selectedModelId = configuredModelId == null ? "" : configuredModelId;
        if (Files.exists(this.file)) {
            this.selectedModelId = readFromDisk();
        }
    }

    public String selectedModelId() {
        return selectedModelId;
    }

    public synchronized void save(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("RAG answer model identifier is required.");
        }
        Properties properties = new Properties();
        properties.setProperty(MODEL_ID_PROPERTY, modelId);
        Path parent = file.getParent();
        Path temporaryFile = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            temporaryFile = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
            try (OutputStream output = Files.newOutputStream(temporaryFile)) {
                properties.store(output, "Agent Smith RAG answer model selection");
            }
            try {
                Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
            selectedModelId = modelId;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist Agent Smith RAG model configuration.", exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException exception) {
                    throw new IllegalStateException("Could not clean up temporary RAG configuration file.", exception);
                }
            }
        }
    }

    private String readFromDisk() {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read Agent Smith RAG model configuration.", exception);
        }
        String modelId = properties.getProperty(MODEL_ID_PROPERTY, "").trim();
        if (modelId.isEmpty()) {
            throw new IllegalStateException("Persisted Agent Smith RAG model configuration is missing answerModelId.");
        }
        return modelId;
    }
}
