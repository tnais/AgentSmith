package org.gianoziaorientale.ingestor;

import java.io.EOFException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import org.gianoziaorientale.agent.AgentSmithOperatorClient;
import org.gianoziaorientale.agent.ModelMenu;
import org.gianoziaorientale.agent.ModuliAiService;
import org.gianoziaorientale.agent.RestClientSupport;
import org.gianoziaorientale.rag.RagOperationalMetrics;
import org.springframework.web.client.RestClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public final class DocumentIngestionCli {

    private DocumentIngestionCli() {}

    public static void main(String[] args) {
        try {
            DocumentIngestionCliConfig config =
                    DocumentIngestionCliConfig.fromEnvironment(System.getenv());
            PrintStream output = System.out;
            CliInput reader = new CliInput(System.in, output);
            DocumentIngestionService ingestionService = createIngestionService(config);
            boolean completed = run(config, AgentSmithOperatorClient.create(
                    config.agentSmithUrl(), config.operatorUsername(), config.operatorPassword()),
                    reader, output, ingestionService);
            if (!completed) {
                System.exit(1);
            }
        } catch (EOFException exception) {
            System.err.println("Input ended before the operation was confirmed.");
            System.exit(1);
        } catch (Exception exception) {
            System.err.println("Document ingestion CLI failed: " + exception.getClass().getSimpleName());
            System.exit(1);
        }
    }

    static DocumentIngestionService createIngestionService(DocumentIngestionCliConfig config) {
        EmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
                .apiKey(config.geminiApiKey())
                .modelName(config.embeddingModel())
                .outputDimensionality(config.embeddingDimensions())
                .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT)
                .timeout(Duration.ofSeconds(30))
                .build();
        RestClient elasticsearchClient = RestClient.builder()
                .baseUrl(config.elasticsearch().getUrl().replaceAll("/+$", ""))
                .defaultHeader("Authorization", "ApiKey " + config.elasticsearch().getWriteApiKey())
                .requestFactory(RestClientSupport.requestFactory())
                .build();
        return new DocumentIngestionService(
                embeddingModel,
                new ElasticsearchDocumentIndex(elasticsearchClient, config.elasticsearch().getIndexAlias()),
                config.embeddingDimensions(),
                new RagOperationalMetrics(new SimpleMeterRegistry()));
    }

    static boolean run(
            DocumentIngestionCliConfig config,
            AgentSmithOperatorClient operatorClient,
            CliInput reader,
            PrintStream output,
            DocumentIngestionRunner ingestionRunner) throws IOException {
        String enteredPath = reader.readDirectory(
                "Document directory (enter a partial path, then Tab and Enter to complete): ");
        Path directory;
        try {
            directory = Path.of(enteredPath).toRealPath();
        } catch (Exception exception) {
            output.println("The entered path could not be resolved to a readable directory.");
            return false;
        }
        if (!Files.isDirectory(directory) || !Files.isReadable(directory)) {
            output.println("The resolved path is not a readable directory.");
            return false;
        }

        output.println("Resolved directory: " + directory);
        String confirmation = reader.readLine("Type YES to confirm this directory: ");
        if (!"YES".equals(confirmation)) {
            output.println("Operation cancelled; no model setting was changed.");
            return false;
        }

        List<ModuliAiService.Modulo> models = operatorClient.availableModels();
        if (models.isEmpty()) {
            output.println("Agent Smith returned no available chat models.");
            return false;
        }

        int page = 0;
        while (true) {
            output.print(ModelMenu.renderPage(models, page));
            if (ModelMenu.pageCount(models) > 1) {
                output.printf(Locale.ROOT, "Page %d of %d — enter a letter, next, or prev: ",
                        page + 1, ModelMenu.pageCount(models));
            } else {
                output.print("Select a model by letter: ");
            }
            String choice = reader.readLine();
            String normalized = choice == null ? "" : choice.trim();
            if ("next".equalsIgnoreCase(normalized)) {
                page = (page + 1) % ModelMenu.pageCount(models);
                continue;
            }
            if ("prev".equalsIgnoreCase(normalized)) {
                page = (page + ModelMenu.pageCount(models) - 1) % ModelMenu.pageCount(models);
                continue;
            }
            try {
                ModuliAiService.Modulo selected = ModelMenu.select(models, page, normalized);
                operatorClient.saveAnswerModel(selected.id());
                output.println("Saved RAG answer model: " + ModelMenu.safeText(selected.nome()));
                output.println("Directory confirmed: " + directory);
                DocumentIngestionService.IngestionSummary summary =
                        ingestionRunner.ingest(directory, output);
                return summary.successful();
            } catch (IllegalArgumentException exception) {
                output.println(exception.getMessage());
            }
        }
    }

}
