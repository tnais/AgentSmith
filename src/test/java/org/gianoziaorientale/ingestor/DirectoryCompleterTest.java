package org.gianoziaorientale.ingestor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoryCompleterTest {

    @TempDir
    Path tempDirectory;

    @Test
    void completesOnlyMatchingDirectories() throws Exception {
        Files.createDirectory(tempDirectory.resolve("docs"));
        Files.createDirectory(tempDirectory.resolve("docs-old"));
        Files.writeString(tempDirectory.resolve("docs.txt"), "not a directory");

        List<String> completions =
                DirectoryCompleter.completeDirectories(tempDirectory.resolve("doc").toString());

        assertEquals(
                List.of(tempDirectory.resolve("docs").toString() + "/",
                        tempDirectory.resolve("docs-old").toString() + "/"),
                completions);
    }

    @Test
    void completesChildDirectoriesFromAnEnteredParent() throws Exception {
        Path parent = Files.createDirectory(tempDirectory.resolve("parent"));
        Files.createDirectory(parent.resolve("wanted"));
        Files.createDirectory(parent.resolve("other"));

        assertEquals(
                List.of(parent + "/wanted/"),
                DirectoryCompleter.completeDirectories(parent + "/w"));
    }
}
