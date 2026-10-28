package org.gianoziaorientale.ingestor;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.gianoziaorientale.agent.ModelMenu;
import org.gianoziaorientale.rag.RagOperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gianoziaorientale.rag.DocumentFilePolicy;

public final class DocumentIngestionService implements DocumentIngestionRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIngestionService.class);
    private final EmbeddingModel embeddingModel;
    private final DocumentIndex index;
    private final DocumentTextReader textReader = new DocumentTextReader();
    private final int expectedDimensions;
    private final RagOperationalMetrics metrics;

    public DocumentIngestionService(EmbeddingModel embeddingModel, DocumentIndex index, int expectedDimensions) {
        this(embeddingModel, index, expectedDimensions, new RagOperationalMetrics(Metrics.globalRegistry));
    }

    public DocumentIngestionService(
            EmbeddingModel embeddingModel,
            DocumentIndex index,
            int expectedDimensions,
            RagOperationalMetrics metrics) {
        if (expectedDimensions <= 0) {
            throw new IllegalArgumentException("Embedding dimensions must be positive.");
        }
        this.embeddingModel = embeddingModel;
        this.index = index;
        this.expectedDimensions = expectedDimensions;
        this.metrics = metrics;
    }

    @Override
    public IngestionSummary ingest(Path selectedDirectory, Appendable progress) {
        long startedAt = System.nanoTime();
        Timer.Sample sample = metrics.startTimer();
        try {
            IngestionSummary summary = ingestInternal(selectedDirectory, progress);
            metrics.increment("agentsmith.rag.ingestion.files", "outcome", "indexed", summary.indexedFiles());
            metrics.increment("agentsmith.rag.ingestion.files", "outcome", "issue", summary.issues().size());
            metrics.increment("agentsmith.rag.ingestion.runs", "outcome",
                    summary.successful() ? "success" : "failure");
            LOGGER.info("RAG ingestion completed: indexedFiles={}, issues={}, durationMs={}",
                    summary.indexedFiles(), summary.issues().size(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            return summary;
        } catch (RuntimeException exception) {
            metrics.increment("agentsmith.rag.ingestion.runs", "outcome", "failure");
            LOGGER.warn("RAG ingestion failed: errorType={}, durationMs={}",
                    exception.getClass().getSimpleName(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            throw exception;
        } finally {
            metrics.stop(sample, "agentsmith.rag.ingestion.duration");
        }
    }

    private IngestionSummary ingestInternal(Path selectedDirectory, Appendable progress) {
        final Path root;
        try {
            root = selectedDirectory.toRealPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException("The selected source directory could not be resolved.", exception);
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(root)) {
            throw new IllegalArgumentException("The selected source path is not a readable directory.");
        }

        String corpusId = sha256(root.toString());
        ScanResult scan = scan(root);
        List<IngestionIssue> issues = new ArrayList<>(scan.issues());
        for (IngestionIssue issue : scan.issues()) {
            append(progress, "SKIPPED " + ModelMenu.safeText(issue.path()) + ": " + issue.message());
        }
        Set<String> currentFileIds = new LinkedHashSet<>();
        int ingested = 0;

        for (int fileNumber = 0; fileNumber < scan.files().size(); fileNumber++) {
            Path file = scan.files().get(fileNumber);
            String relativePath = relativePath(root, file);
            append(progress, "Ingesting [" + (fileNumber + 1) + "/" + scan.files().size()
                    + "] " + ModelMenu.safeText(relativePath));
            try {
                byte[] raw = textReader.read(file);
                String text = decodeText(raw);
                if (text.isBlank()) {
                    throw new IOException("empty text files are not indexed");
                }
                String fileId = sha256(corpusId + "\n" + relativePath);
                String contentHash = sha256(raw);
                List<DocumentChunker.DocumentChunk> chunks = raw.length <= DocumentFilePolicy.SMALL_FILE_MAX_BYTES
                        ? DocumentChunker.single(text)
                        : DocumentChunker.split(text);
                List<DocumentIndex.IndexedDocumentChunk> documents =
                        createIndexedChunks(corpusId, fileId, relativePath, contentHash, chunks);
                index.index(documents);
                metrics.increment("agentsmith.rag.ingestion.chunks", "kind", "indexed", documents.size());
                index.deleteChunksAtOrAbove(fileId, documents.size());
                currentFileIds.add(fileId);
                ingested++;
                append(progress, "Indexed " + documents.size() + " chunk(s): "
                        + ModelMenu.safeText(relativePath));
            } catch (IOException | RuntimeException exception) {
                String reason = exception instanceof IOException
                        ? "could not be read as bounded UTF-8 text"
                        : "embedding or Elasticsearch indexing failed";
                issues.add(new IngestionIssue(relativePath, reason));
                append(progress, "ERROR " + ModelMenu.safeText(relativePath) + ": " + reason);
            }
        }

        if (issues.isEmpty()) {
            try {
                index.deleteFilesNotInCorpus(corpusId, currentFileIds);
            } catch (RuntimeException exception) {
                issues.add(new IngestionIssue(".", "stale document cleanup failed"));
                append(progress, "ERROR stale document cleanup failed.");
            }
        } else {
            append(progress, "Skipped corpus-wide stale-file cleanup because this run had file errors.");
        }

        append(progress, "Ingestion complete: " + ingested + " indexed, "
                + issues.size() + " issue(s).");
        return new IngestionSummary(ingested, issues);
    }

    private List<DocumentIndex.IndexedDocumentChunk> createIndexedChunks(
            String corpusId,
            String fileId,
            String relativePath,
            String contentHash,
            List<DocumentChunker.DocumentChunk> chunks) {
        List<DocumentIndex.IndexedDocumentChunk> documents = new ArrayList<>(chunks.size());
        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            DocumentChunker.DocumentChunk chunk = chunks.get(chunkIndex);
            var response = embeddingModel.embed(chunk.text());
            if (response == null || response.content() == null || response.content().vector() == null) {
                throw new IllegalStateException("Embedding model returned an empty vector.");
            }
            float[] vector = response.content().vector();
            if (vector.length != expectedDimensions) {
                throw new IllegalStateException("Embedding dimension does not match configured dimensions.");
            }
            for (float value : vector) {
                if (!Float.isFinite(value)) {
                    throw new IllegalStateException("Embedding model returned a non-finite vector component.");
                }
            }
            documents.add(new DocumentIndex.IndexedDocumentChunk(
                    sha256(fileId + ":" + chunkIndex),
                    corpusId,
                    fileId,
                    fileId,
                    relativePath,
                    contentHash,
                    chunkIndex,
                    chunk.text(),
                    chunk.startLine(),
                    Math.max(chunk.startLine(), chunk.endLine()),
                    vector.clone()));
        }
        return List.copyOf(documents);
    }

    private ScanResult scan(Path root) {
        List<Path> files = new ArrayList<>();
        List<IngestionIssue> issues = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                private int visitedFiles;
                private int visitedEntries;

                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    visitedEntries++;
                    if (visitedEntries > DocumentFilePolicy.MAX_ENTRIES_PER_RUN) {
                        issues.add(new IngestionIssue(safeRelativePath(root, directory),
                                "directory tree exceeds the maximum of "
                                        + DocumentFilePolicy.MAX_ENTRIES_PER_RUN + " entries"));
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    visitedFiles++;
                    visitedEntries++;
                    String relative;
                    try {
                        relative = relativePath(root, file);
                    } catch (IllegalArgumentException invalidPath) {
                        issues.add(new IngestionIssue(".", "path cannot be represented safely as source metadata"));
                        return FileVisitResult.CONTINUE;
                    }
                    if (visitedEntries > DocumentFilePolicy.MAX_ENTRIES_PER_RUN) {
                        issues.add(new IngestionIssue(relative,
                                "directory tree exceeds the maximum of "
                                        + DocumentFilePolicy.MAX_ENTRIES_PER_RUN + " entries"));
                        return FileVisitResult.TERMINATE;
                    }
                    if (visitedFiles > DocumentFilePolicy.MAX_FILES_PER_RUN) {
                        issues.add(new IngestionIssue(relative,
                                "directory exceeds the maximum of "
                                        + DocumentFilePolicy.MAX_FILES_PER_RUN + " files"));
                        return FileVisitResult.TERMINATE;
                    }
                    if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
                        issues.add(new IngestionIssue(relative, "symbolic links and special files are unsupported"));
                    } else if (!Files.isReadable(file)) {
                        issues.add(new IngestionIssue(relative, "file is not readable"));
                    } else {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    String relative;
                    try {
                        relative = relativePath(root, file);
                    } catch (IllegalArgumentException invalidPath) {
                        relative = ".";
                    }
                    issues.add(new IngestionIssue(relative, "file or directory could not be read"));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            issues.add(new IngestionIssue(".", "directory traversal failed"));
        }
        files.sort(Path::compareTo);
        return new ScanResult(List.copyOf(files), issues);
    }

    private static String decodeText(byte[] bytes) throws IOException {
        if (bytes.length > DocumentFilePolicy.MAX_FILE_BYTES) {
            throw new IOException("file exceeds maximum size");
        }
        for (byte value : bytes) {
            if (value == 0) {
                throw new IOException("binary content is not supported");
            }
        }
        try {
            return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new IOException("file is not valid UTF-8", exception);
        }
    }

    private static String relativePath(Path root, Path file) {
        String relative = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
        if (relative.length() > 1_024 || relative.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Document path is outside the supported metadata format.");
        }
        return relative;
    }

    private static String safeRelativePath(Path root, Path file) {
        try {
            return relativePath(root, file);
        } catch (IllegalArgumentException exception) {
            return ".";
        }
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void append(Appendable progress, String line) {
        try {
            progress.append(line).append(System.lineSeparator());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write ingestion progress.", exception);
        }
    }

    private record ScanResult(List<Path> files, List<IngestionIssue> issues) {}

    public record IngestionIssue(String path, String message) {}

    public record IngestionSummary(int indexedFiles, List<IngestionIssue> issues) {
        public IngestionSummary {
            issues = List.copyOf(issues);
        }

        public boolean successful() {
            return issues.isEmpty();
        }
    }
}
