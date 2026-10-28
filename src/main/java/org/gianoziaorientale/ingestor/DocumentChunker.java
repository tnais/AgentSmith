package org.gianoziaorientale.ingestor;

import java.util.ArrayList;
import java.util.List;

final class DocumentChunker {

    static final int MAX_CHUNK_CHARACTERS = 4_000;
    static final int OVERLAP_CHARACTERS = 300;
    private static final int BOUNDARY_SEARCH_CHARACTERS = 1_000;

    private DocumentChunker() {}

    static List<DocumentChunk> single(String text) {
        return text.isEmpty()
                ? List.of()
                : List.of(new DocumentChunk(text, 0, text.length(),
                        lineAt(text, 0), endLineAt(text, text.length())));
    }

    static List<DocumentChunk> split(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        List<DocumentChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_CHARACTERS, text.length());
            if (end < text.length()) {
                end = chooseBoundary(text, start, end);
            }
            end = beforeSurrogatePair(text, end);
            if (end <= start) {
                end = Math.min(start + MAX_CHUNK_CHARACTERS, text.length());
                end = beforeSurrogatePair(text, end);
            }
            chunks.add(new DocumentChunk(
                    text.substring(start, end), start, end,
                    lineAt(text, start), endLineAt(text, end)));
            if (end == text.length()) {
                break;
            }
            int overlapStart = Math.max(start + 1, end - OVERLAP_CHARACTERS);
            overlapStart = afterSurrogatePair(text, overlapStart);
            int boundary = text.indexOf('\n', overlapStart);
            if (boundary >= 0 && boundary < Math.min(end, overlapStart + BOUNDARY_SEARCH_CHARACTERS)) {
                start = boundary + 1;
            } else {
                while (overlapStart < end && Character.isWhitespace(text.charAt(overlapStart))) {
                    overlapStart++;
                }
                start = overlapStart;
            }
        }
        return List.copyOf(chunks);
    }

    private static int beforeSurrogatePair(String text, int offset) {
        return offset > 0 && offset < text.length()
                        && Character.isHighSurrogate(text.charAt(offset - 1))
                        && Character.isLowSurrogate(text.charAt(offset))
                ? offset - 1
                : offset;
    }

    private static int afterSurrogatePair(String text, int offset) {
        return offset > 0 && offset < text.length()
                        && Character.isHighSurrogate(text.charAt(offset - 1))
                        && Character.isLowSurrogate(text.charAt(offset))
                ? offset + 1
                : offset;
    }

    private static int chooseBoundary(String text, int start, int end) {
        int minimum = Math.max(start + 1, end - BOUNDARY_SEARCH_CHARACTERS);
        for (int i = end - 1; i >= minimum; i--) {
            if (text.charAt(i) == '\n') {
                return i + 1;
            }
        }
        for (int i = end - 1; i >= minimum; i--) {
            char current = text.charAt(i);
            if (Character.isWhitespace(current) || current == ';' || current == '{' || current == '}') {
                return i + 1;
            }
        }
        return end;
    }

    private static int lineAt(String text, int offset) {
        int line = 1;
        for (int i = 0; i < Math.min(offset, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static int endLineAt(String text, int offset) {
        int line = lineAt(text, offset);
        if (offset > 0 && text.charAt(offset - 1) == '\n') {
            line--;
        }
        return Math.max(1, line);
    }

    record DocumentChunk(String text, int startOffset, int endOffset, int startLine, int endLine) {}
}
