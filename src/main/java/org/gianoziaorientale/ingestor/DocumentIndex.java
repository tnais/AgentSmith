package org.gianoziaorientale.ingestor;

import java.util.List;
import java.util.Set;

interface DocumentIndex {

    void index(List<IndexedDocumentChunk> chunks);

    void deleteChunksAtOrAbove(String sourceId, int firstStaleChunkIndex);

    void deleteFilesNotInCorpus(String corpusId, Set<String> currentFileIds);

    record IndexedDocumentChunk(
            String id,
            String corpusId,
            String fileId,
            String sourceId,
            String path,
            String contentHash,
            int chunkIndex,
            String text,
            int startLine,
            int endLine,
            float[] embedding) {}
}
