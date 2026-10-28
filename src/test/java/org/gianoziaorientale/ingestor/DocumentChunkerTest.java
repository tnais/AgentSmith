package org.gianoziaorientale.ingestor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentChunkerTest {

    @Test
    void smallDocumentRemainsOneUnmodifiedChunk() {
        String text = "first line\nsecond line\n";

        List<DocumentChunker.DocumentChunk> chunks = DocumentChunker.single(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.getFirst().text());
        assertEquals(1, chunks.getFirst().startLine());
        assertEquals(2, chunks.getFirst().endLine());
    }

    @Test
    void largeDocumentsSplitWithOverlapAndPreserveLineLocations() {
        String text = ("class Example {\n    String value = \"" + "x".repeat(2_000) + "\";\n}\n")
                .repeat(4);

        List<DocumentChunker.DocumentChunk> chunks = DocumentChunker.split(text);

        assertTrue(chunks.size() > 1);
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunker.DocumentChunk chunk = chunks.get(i);
            assertTrue(chunk.text().length() <= DocumentChunker.MAX_CHUNK_CHARACTERS);
            assertTrue(chunk.startLine() >= 1);
            assertTrue(chunk.endLine() >= chunk.startLine());
            if (i > 0) {
                DocumentChunker.DocumentChunk previous = chunks.get(i - 1);
                assertTrue(chunk.startOffset() < previous.endOffset());
                assertTrue(previous.endOffset() - chunk.startOffset() <= DocumentChunker.OVERLAP_CHARACTERS);
            }
        }
    }

    @Test
    void longSingleLineIsSplitWithoutLosingOverlap() {
        String text = "x".repeat(9_000);

        List<DocumentChunker.DocumentChunk> chunks = DocumentChunker.split(text);

        assertEquals(3, chunks.size());
        assertEquals(DocumentChunker.OVERLAP_CHARACTERS,
                chunks.get(0).endOffset() - chunks.get(1).startOffset());
        assertEquals(1, chunks.get(1).startLine());
        assertEquals(1, chunks.get(1).endLine());
    }

    @Test
    void largeUnicodeTextNeverSplitsSurrogatePairs() {
        String text = "x" + "💡".repeat(3_000);

        List<DocumentChunker.DocumentChunk> chunks = DocumentChunker.split(text);

        assertTrue(chunks.size() > 1);
        for (DocumentChunker.DocumentChunk chunk : chunks) {
            for (int i = 0; i < chunk.text().length(); i++) {
                char value = chunk.text().charAt(i);
                if (Character.isHighSurrogate(value)) {
                    assertTrue(i + 1 < chunk.text().length()
                            && Character.isLowSurrogate(chunk.text().charAt(i + 1)));
                } else if (Character.isLowSurrogate(value)) {
                    assertTrue(i > 0 && Character.isHighSurrogate(chunk.text().charAt(i - 1)));
                }
            }
        }
    }
}
