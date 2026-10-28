package org.gianoziaorientale.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelMenuTest {

    @Test
    void rendersMultipleColumnsWithinEightyColumnsAndWrapsLongNamesAndIds() {
        List<ModuliAiService.Modulo> models = List.of(
                new ModuliAiService.Modulo("gemini:" + "model-".repeat(12), "Model " + "name ".repeat(20)),
                new ModuliAiService.Modulo("openwebui:short", "Short model"),
                new ModuliAiService.Modulo("gemini:third", "Third model"),
                new ModuliAiService.Modulo("openwebui:fourth", "Fourth model"));

        String rendered = ModelMenu.renderPage(models, 0);
        String compactLongRendered = ModelMenu.renderPage(List.of(models.getFirst()), 0)
                .replaceAll("\\s+", "");

        assertTrue(compactLongRendered.contains("Model" + "name".repeat(20)));
        assertTrue(compactLongRendered.contains("gemini:" + "model-".repeat(12)));
        assertTrue(rendered.contains("A)"));
        assertTrue(rendered.contains("B)"));
        assertTrue(rendered.contains("C)"));
        assertTrue(rendered.contains("D)"));
        for (String line : rendered.split("\\R")) {
            assertTrue(line.codePointCount(0, line.length()) <= ModelMenu.TERMINAL_WIDTH, line);
        }
    }

    @Test
    void pagesCatalogsBeyondTwentySixModelsAndPreservesExactIdentifiers() {
        List<ModuliAiService.Modulo> models = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            models.add(new ModuliAiService.Modulo("model:" + index, "Model " + index));
        }

        assertEquals(2, ModelMenu.pageCount(models));
        assertEquals(models.get(25), ModelMenu.select(models, 0, "z"));
        assertEquals(models.get(26), ModelMenu.select(models, 1, "A"));
        assertThrows(IllegalArgumentException.class, () -> ModelMenu.select(models, 0, "{"));
        assertThrows(IllegalArgumentException.class, () -> ModelMenu.renderPage(models, 2));
    }

    @Test
    void stripsControlCharactersFromTerminalOutput() {
        assertEquals("name model", ModelMenu.safeText("name\n\tmodel"));
    }
}
