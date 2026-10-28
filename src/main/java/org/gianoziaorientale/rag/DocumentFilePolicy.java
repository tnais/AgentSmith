package org.gianoziaorientale.rag;

public final class DocumentFilePolicy {

    public static final long SMALL_FILE_MAX_BYTES = 5_000L;
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    public static final int MAX_FILES_PER_RUN = 10_000;
    public static final int MAX_ENTRIES_PER_RUN = 20_000;

    private DocumentFilePolicy() {}
}
