package org.gianoziaorientale.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.gianoziaorientale.agent.ModuliAiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RagModelSelectionStoreTest {

    @TempDir
    Path tempDirectory;

    @Test
    void persistsOnlyTheAnswerModelIdAndReloadsIt() {
        Path file = tempDirectory.resolve("rag.properties");
        RagModelSelectionStore store = new RagModelSelectionStore("gemini:default", file.toString());

        store.save("openwebui:approved");

        assertEquals("openwebui:approved", store.selectedModelId());
        assertEquals("openwebui:approved",
                new RagModelSelectionStore("gemini:default", file.toString()).selectedModelId());
    }

    @Test
    void configurationServiceAcceptsOnlyAnExactCatalogIdentifier() {
        ModuliAiService catalog = mock(ModuliAiService.class);
        when(catalog.moduliDisponibili()).thenReturn(
                List.of(new ModuliAiService.Modulo("gemini:catalog-id", "Display name")));
        RagModelSelectionStore store =
                new RagModelSelectionStore("", tempDirectory.resolve("selection.properties").toString());
        RagModelConfigurationService service = new RagModelConfigurationService(catalog, store);

        service.selectAnswerModel("gemini:catalog-id");

        assertEquals("gemini:catalog-id", store.selectedModelId());
        assertThrows(IllegalArgumentException.class, () -> service.selectAnswerModel("Display name"));
    }
}
