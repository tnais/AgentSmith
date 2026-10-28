package org.gianoziaorientale.ingestor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.gianoziaorientale.rag.DocumentFilePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class DocumentIngestionServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void ingestsNestedTextFilesAndUsesStableMetadataAndChunkIds() throws Exception {
        Path root = Files.createDirectory(tempDirectory.resolve("source"));
        Path nested = Files.createDirectory(root.resolve("nested"));
        Path small = Files.writeString(nested.resolve("small.txt"), "hello\nworld\n");
        Path large = Files.writeString(root.resolve("large.java"), "x".repeat(5_001));
        EmbeddingModel embeddings = embeddingModel();
        DocumentIndex index = mock(DocumentIndex.class);
        DocumentIngestionService service = new DocumentIngestionService(embeddings, index, 2);

        var firstRun = service.ingest(root, new StringBuilder());
        ArgumentCaptor<List<DocumentIndex.IndexedDocumentChunk>> indexed =
                ArgumentCaptor.forClass(List.class);

        assertTrue(firstRun.successful());
        assertEquals(2, firstRun.indexedFiles());
        verify(index, org.mockito.Mockito.times(2)).index(indexed.capture());
        List<DocumentIndex.IndexedDocumentChunk> firstLargeChunks = indexed.getAllValues().getFirst();
        List<DocumentIndex.IndexedDocumentChunk> firstSmallChunks = indexed.getAllValues().get(1);
        assertEquals("nested/small.txt", firstSmallChunks.getFirst().path());
        assertEquals(1, firstSmallChunks.size());
        assertEquals(2, firstSmallChunks.getFirst().embedding().length);
        assertEquals(1, firstSmallChunks.getFirst().startLine());
        assertEquals(2, firstSmallChunks.getFirst().endLine());
        assertEquals(2, firstLargeChunks.size());
        assertTrue(firstLargeChunks.stream().allMatch(chunk -> chunk.contentHash().length() == 64));
        verify(index).deleteChunksAtOrAbove(firstLargeChunks.getFirst().sourceId(), 2);

        DocumentIndex secondIndex = mock(DocumentIndex.class);
        var secondRun = new DocumentIngestionService(embeddings, secondIndex, 2)
                .ingest(root, new StringBuilder());
        ArgumentCaptor<List<DocumentIndex.IndexedDocumentChunk>> reindexed =
                ArgumentCaptor.forClass(List.class);
        verify(secondIndex, org.mockito.Mockito.times(2)).index(reindexed.capture());
        assertEquals(firstRun.indexedFiles(), secondRun.indexedFiles());
        assertEquals(indexed.getAllValues().stream().flatMap(List::stream).map(
                DocumentIndex.IndexedDocumentChunk::id).toList(),
                reindexed.getAllValues().stream().flatMap(List::stream).map(
                        DocumentIndex.IndexedDocumentChunk::id).toList());
        assertTrue(Files.exists(small));
        assertTrue(Files.exists(large));
    }

    @Test
    void ingestsTextFilesRegardlessOfExtension() throws Exception {
        Path root = Files.createDirectory(tempDirectory.resolve("arbitrary-extensions"));
        Files.writeString(root.resolve("notes.txt"), "readable document");
        Files.writeString(root.resolve("program.exe"), "plain text, not executable content");
        Files.writeString(root.resolve("README"), "extensionless text");
        EmbeddingModel embeddings = embeddingModel();
        DocumentIndex index = mock(DocumentIndex.class);
        StringBuilder progress = new StringBuilder();

        var summary = new DocumentIngestionService(embeddings, index, 2).ingest(root, progress);

        assertTrue(summary.successful());
        assertEquals(3, summary.indexedFiles());
        assertTrue(progress.toString().contains("Indexed 1 chunk(s): program.exe"));
        assertTrue(progress.toString().contains("Indexed 1 chunk(s): README"));
        verify(index).deleteFilesNotInCorpus(anyString(), org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void recordsBoundedIngestionCountsAndRunDuration() throws Exception {
        Path root = Files.createDirectory(tempDirectory.resolve("metrics"));
        Files.writeString(root.resolve("doc.txt"), "document");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DocumentIngestionService service = new DocumentIngestionService(
                embeddingModel(), mock(DocumentIndex.class), 2, new org.gianoziaorientale.rag.RagOperationalMetrics(registry));

        var summary = service.ingest(root, new StringBuilder());

        assertTrue(summary.successful());
        assertEquals(1.0, registry.get("agentsmith.rag.ingestion.files")
                .tag("outcome", "indexed").counter().count());
        assertEquals(1.0, registry.get("agentsmith.rag.ingestion.chunks")
                .tag("kind", "indexed").counter().count());
        assertEquals(1.0, registry.get("agentsmith.rag.ingestion.runs")
                .tag("outcome", "success").counter().count());
        assertEquals(1, registry.get("agentsmith.rag.ingestion.duration").timer().count());
    }

    @Test
    void cleansUpRemovedFilesAfterACompleteSuccessfulScan() throws Exception {
        Path root = Files.createDirectory(tempDirectory.resolve("removed"));
        Files.writeString(root.resolve("old.md"), "document");
        DocumentIndex firstIndex = mock(DocumentIndex.class);
        assertTrue(new DocumentIngestionService(embeddingModel(), firstIndex, 2)
                .ingest(root, new StringBuilder()).successful());

        Files.delete(root.resolve("old.md"));
        DocumentIndex secondIndex = mock(DocumentIndex.class);
        var summary = new DocumentIngestionService(embeddingModel(), secondIndex, 2)
                .ingest(root, new StringBuilder());

        assertTrue(summary.successful());
        assertEquals(0, summary.indexedFiles());
        verify(secondIndex).deleteFilesNotInCorpus(anyString(), eq(Set.of()));
    }

    @Test
    void rejectsOversizedAndSymbolicLinkFilesWithoutFollowingThem() throws Exception {
        Path root = Files.createDirectory(tempDirectory.resolve("unsafe"));
        Files.write(root.resolve("too-large.txt"),
                new byte[Math.toIntExact(DocumentFilePolicy.MAX_FILE_BYTES + 1)]);
        Path outside = Files.writeString(tempDirectory.resolve("outside.txt"), "outside");
        Files.createSymbolicLink(root.resolve("linked.txt"), outside);
        EmbeddingModel embeddings = embeddingModel();
        DocumentIndex index = mock(DocumentIndex.class);

        var summary = new DocumentIngestionService(embeddings, index, 2)
                .ingest(root, new StringBuilder());

        assertFalse(summary.successful());
        assertTrue(summary.issues().stream().anyMatch(issue -> issue.path().equals("too-large.txt")));
        assertTrue(summary.issues().stream().anyMatch(issue -> issue.path().equals("linked.txt")));
        verify(index, never()).index(org.mockito.ArgumentMatchers.anyList());
        verify(index, never()).deleteFilesNotInCorpus(anyString(), org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void keepsSmallFileThresholdInclusiveAtFiveThousandRawBytes() throws Exception {
        Path root = Files.createDirectory(tempDirectory.resolve("threshold"));
        Files.writeString(root.resolve("exact.txt"), "x".repeat(5_000));
        DocumentIndex index = mock(DocumentIndex.class);
        var summary = new DocumentIngestionService(embeddingModel(), index, 2)
                .ingest(root, new StringBuilder());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentIndex.IndexedDocumentChunk>> indexed =
                ArgumentCaptor.forClass(List.class);

        assertTrue(summary.successful());
        verify(index).index(indexed.capture());
        assertEquals(1, indexed.getValue().size());
        assertEquals(5_000, indexed.getValue().getFirst().text().length());
    }

    @Test
    void skipsCorpusCleanupWhenAFileIsNotValidUtf8() throws Exception {
        Path root = Files.createDirectory(tempDirectory.resolve("invalid-text"));
        Files.write(root.resolve("invalid.txt"), new byte[] {(byte) 0xc3, 0x28});
        DocumentIndex index = mock(DocumentIndex.class);

        var summary = new DocumentIngestionService(embeddingModel(), index, 2)
                .ingest(root, new StringBuilder());

        assertFalse(summary.successful());
        verify(index, never()).deleteFilesNotInCorpus(anyString(), org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void reportsEmbeddingDimensionMismatchWithoutDeletingExistingChunks() throws Exception {
        Path root = Files.createDirectory(tempDirectory.resolve("wrong-vector"));
        Files.writeString(root.resolve("doc.txt"), "document");
        EmbeddingModel embeddings = mock(EmbeddingModel.class);
        when(embeddings.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[] {0.1f})));
        DocumentIndex index = mock(DocumentIndex.class);

        var summary = new DocumentIngestionService(embeddings, index, 2)
                .ingest(root, new StringBuilder());

        assertFalse(summary.successful());
        assertTrue(summary.issues().getFirst().message().contains("failed"));
        verify(index, never()).index(org.mockito.ArgumentMatchers.anyList());
        verify(index, never()).deleteChunksAtOrAbove(anyString(), org.mockito.ArgumentMatchers.anyInt());
        verify(index, never()).deleteFilesNotInCorpus(anyString(), org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void reingestingShorterContentKeepsFirstChunkIdAndRemovesHigherChunkNumbers() throws Exception {
        Path root = Files.createDirectory(tempDirectory.resolve("shrink"));
        Path file = Files.writeString(root.resolve("doc.txt"), "x".repeat(8_500));
        DocumentIndex firstIndex = mock(DocumentIndex.class);
        new DocumentIngestionService(embeddingModel(), firstIndex, 2)
                .ingest(root, new StringBuilder());
        ArgumentCaptor<List<DocumentIndex.IndexedDocumentChunk>> firstCapture =
                ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(firstIndex).index(firstCapture.capture());

        Files.writeString(file, "short replacement");
        DocumentIndex secondIndex = mock(DocumentIndex.class);
        new DocumentIngestionService(embeddingModel(), secondIndex, 2)
                .ingest(root, new StringBuilder());
        ArgumentCaptor<List<DocumentIndex.IndexedDocumentChunk>> secondCapture =
                ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(secondIndex).index(secondCapture.capture());

        assertEquals(3, firstCapture.getValue().size());
        assertEquals(1, secondCapture.getValue().size());
        assertEquals(firstCapture.getValue().getFirst().id(), secondCapture.getValue().getFirst().id());
        assertTrue(!firstCapture.getValue().getFirst().contentHash()
                .equals(secondCapture.getValue().getFirst().contentHash()));
        org.mockito.Mockito.verify(secondIndex)
                .deleteChunksAtOrAbove(secondCapture.getValue().getFirst().sourceId(), 1);
    }

    private static EmbeddingModel embeddingModel() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
        return model;
    }

}
