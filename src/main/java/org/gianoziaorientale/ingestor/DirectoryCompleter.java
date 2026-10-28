package org.gianoziaorientale.ingestor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class DirectoryCompleter {

    static List<String> completeDirectories(String input) {
        String word = input == null ? "" : input;
        int separator = Math.max(word.lastIndexOf('/'), word.lastIndexOf('\\'));
        String parentText = separator < 0 ? "." : word.substring(0, separator + 1);
        String childPrefix = word.substring(separator + 1);
        Path parent;
        try {
            parent = Path.of(parentText).normalize();
        } catch (InvalidPathException exception) {
            return List.of();
        }
        if (!Files.isDirectory(parent)) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        try (var children = Files.list(parent)) {
            children.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(childPrefix))
                    .sorted()
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        completions.add(parentText.equals("./") ? name + "/"
                                : parentText.equals(".") ? name + "/"
                                : parentText + name + "/");
                    });
        } catch (IOException exception) {
            return List.of();
        }
        return List.copyOf(completions);
    }
}
