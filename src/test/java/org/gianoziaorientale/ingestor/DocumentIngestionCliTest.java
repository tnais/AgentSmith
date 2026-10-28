package org.gianoziaorientale.ingestor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.gianoziaorientale.agent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentIngestionCliTest {

    @TempDir
    Path tempDirectory;

    @Test
    void confirmsResolvedPathAndSavesTheExactCatalogModelId() throws Exception {
        Path sourceDir = Files.createDirectory(tempDirectory.resolve("docs"));
        AgentSmithOperatorClient client = mock(AgentSmithOperatorClient.class);
        when(client.availableModels()).thenReturn(List.of(
                new ModuliAiService.Modulo("gemini:one", "First model"),
                new ModuliAiService.Modulo("openwebui:exact-provider/model-id", "Second model")));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliInput reader = input(output, sourceDir + "\nYES\nB\n");
        DocumentIngestionCliConfig config = cliConfig();
        DocumentIngestionRunner ingestionRunner = (directory, progress) -> {
            try {
                progress.append("Indexed ").append(directory.toString()).append(System.lineSeparator());
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
            return new DocumentIngestionService.IngestionSummary(1, List.of());
        };

        boolean completed = DocumentIngestionCli.run(
                config, client, reader, new PrintStream(output), ingestionRunner);

        assertTrue(completed);
        verify(client).saveAnswerModel("openwebui:exact-provider/model-id");
        assertTrue(output.toString().contains(sourceDir.toRealPath().toString()));
        assertTrue(output.toString().contains("Saved RAG answer model: Second model"));
    }

    @Test
    void cancellationDoesNotFetchModelsOrSaveConfiguration() throws Exception {
        Path sourceDir = Files.createDirectory(tempDirectory.resolve("cancel"));
        AgentSmithOperatorClient client = mock(AgentSmithOperatorClient.class);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliInput reader = input(output, sourceDir + "\nno\n");
        DocumentIngestionRunner ingestionRunner =
                (directory, progress) -> new DocumentIngestionService.IngestionSummary(0, List.of());

        boolean completed = DocumentIngestionCli.run(
                cliConfig(), client, reader, new PrintStream(output), ingestionRunner);

        assertFalse(completed);
        verify(client, never()).availableModels();
        verify(client, never()).saveAnswerModel(anyString());
    }

    @Test
    void completesUniqueDirectoryPrefixWhenTabIsSubmitted() throws Exception {
        Path sourceDir = Files.createDirectory(tempDirectory.resolve("tab-completion-docs"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliInput reader = input(output, tempDirectory.resolve("tab-completion-doc").toString()
                + "\t\nYES\nA\n");
        AgentSmithOperatorClient client = mock(AgentSmithOperatorClient.class);
        when(client.availableModels()).thenReturn(List.of(
                new ModuliAiService.Modulo("gemini:one", "First model")));
        DocumentIngestionRunner ingestionRunner =
                (directory, progress) -> new DocumentIngestionService.IngestionSummary(1, List.of());

        assertTrue(DocumentIngestionCli.run(
                cliConfig(), client, reader, new PrintStream(output), ingestionRunner));
        assertTrue(output.toString().contains("Completed directory: " + sourceDir + "/"));
        assertTrue(output.toString().contains(sourceDir.toRealPath().toString()));
    }

    @Test
    void listsAmbiguousDirectoryMatchesAndPromptsForTheFullPath() throws Exception {
        Files.createDirectory(tempDirectory.resolve("docs-one"));
        Path selectedDir = Files.createDirectory(tempDirectory.resolve("docs-two"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliInput reader = input(output, tempDirectory.resolve("docs-").toString()
                + "\t\n" + selectedDir + "\nYES\nA\n");
        AgentSmithOperatorClient client = mock(AgentSmithOperatorClient.class);
        when(client.availableModels()).thenReturn(List.of(
                new ModuliAiService.Modulo("gemini:one", "First model")));
        DocumentIngestionRunner ingestionRunner =
                (directory, progress) -> new DocumentIngestionService.IngestionSummary(1, List.of());

        assertTrue(DocumentIngestionCli.run(
                cliConfig(), client, reader, new PrintStream(output), ingestionRunner));
        assertTrue(output.toString().contains(tempDirectory.resolve("docs-one") + "/"));
        assertTrue(output.toString().contains(tempDirectory.resolve("docs-two") + "/"));
        assertTrue(output.toString().contains(selectedDir.toRealPath().toString()));
    }

    @Test
    void cliConfigurationRequiresOperatorAndDistinctWriteScopedElasticsearchCredentials() {
        Map<String, String> environment = Map.of(
                "AGENTSMITH_BASE_URL", "https://agent.example",
                "AGENTSMITH_RAG_OPERATOR_USERNAME", "operator",
                "AGENTSMITH_RAG_OPERATOR_PASSWORD", "operator-secret",
                "GEMINI_API_KEY", "embedding-key",
                "EMBEDDING_MODEL", "gemini-embedding-model",
                "EMBEDDING_DIMENSIONS", "768",
                "ELASTICSEARCH_URL", "https://elasticsearch.example:9200",
                "AGENTSMITH_ES_WRITE_API_KEY", "cli-write-key",
                "RAG_INDEX_ALIAS", "agentsmith-rag-v1-active");

        DocumentIngestionCliConfig config = DocumentIngestionCliConfig.fromEnvironment(environment);

        assertEquals(URI.create("https://agent.example"), config.agentSmithUrl());
        assertEquals("cli-write-key", config.elasticsearch().getWriteApiKey());
        assertEquals("gemini-embedding-model", config.embeddingModel());
        assertEquals(768, config.embeddingDimensions());
        assertThrows(IllegalStateException.class, () -> DocumentIngestionCliConfig.fromEnvironment(
                Map.of("AGENTSMITH_BASE_URL", "https://agent.example")));
        assertThrows(IllegalArgumentException.class, () -> DocumentIngestionCliConfig.fromEnvironment(
                Map.of(
                        "AGENTSMITH_BASE_URL", "http://agent.example",
                        "AGENTSMITH_RAG_OPERATOR_USERNAME", "operator",
                        "AGENTSMITH_RAG_OPERATOR_PASSWORD", "operator-secret",
                        "GEMINI_API_KEY", "embedding-key",
                        "EMBEDDING_MODEL", "gemini-embedding-model",
                        "EMBEDDING_DIMENSIONS", "768",
                        "ELASTICSEARCH_URL", "https://elasticsearch.example",
                        "AGENTSMITH_ES_WRITE_API_KEY", "cli-write-key")));
    }

    private DocumentIngestionCliConfig cliConfig() {
        return new DocumentIngestionCliConfig(
                URI.create("https://agent.example"),
                "operator",
                "operator-secret",
                "embedding-key",
                "gemini-embedding-model",
                768,
                new IngestionElasticsearchProperties());
    }

    private CliInput input(ByteArrayOutputStream output, String input) {
        return new CliInput(new ByteArrayInputStream(
                input.getBytes(java.nio.charset.StandardCharsets.UTF_8)), new PrintStream(output));
    }
}
