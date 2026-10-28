package org.gianoziaorientale.ingestor;

import java.nio.file.Path;

@FunctionalInterface
interface DocumentIngestionRunner {

    DocumentIngestionService.IngestionSummary ingest(Path directory, Appendable progress);
}
