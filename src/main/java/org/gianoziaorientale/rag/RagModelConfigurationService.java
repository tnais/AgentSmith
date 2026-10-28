package org.gianoziaorientale.rag;

import org.gianoziaorientale.agent.ModuliAiService;
import org.springframework.stereotype.Service;

@Service
public class RagModelConfigurationService {

    private final ModuliAiService moduliAiService;
    private final RagModelSelectionStore modelSelectionStore;

    public RagModelConfigurationService(
            ModuliAiService moduliAiService, RagModelSelectionStore modelSelectionStore) {
        this.moduliAiService = moduliAiService;
        this.modelSelectionStore = modelSelectionStore;
    }

    public void selectAnswerModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("RAG answer model identifier is required.");
        }
        boolean available = moduliAiService.moduliDisponibili().stream()
                .anyMatch(model -> model.id().equals(modelId));
        if (!available) {
            throw new IllegalArgumentException("RAG answer model is not in the available /moduli catalog.");
        }
        modelSelectionStore.save(modelId);
    }
}
