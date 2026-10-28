package org.gianoziaorientale.ingestor;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class CliInput {

    private final BufferedReader reader;
    private final PrintStream output;

    CliInput(InputStream input, PrintStream output) {
        this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.output = output;
    }

    String readLine(String prompt) throws IOException {
        output.print(prompt);
        output.flush();
        return readLine();
    }

    String readLine() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new EOFException("Input ended before the operation was confirmed.");
        }
        return line;
    }

    String readDirectory(String prompt) throws IOException {
        while (true) {
            String enteredPath = readLine(prompt);
            int tabPosition = enteredPath.indexOf('\t');
            if (tabPosition < 0) {
                return enteredPath;
            }

            List<String> matches = DirectoryCompleter.completeDirectories(
                    enteredPath.substring(0, tabPosition));
            if (matches.size() == 1) {
                output.println("Completed directory: " + matches.getFirst());
                return matches.getFirst();
            }
            if (matches.isEmpty()) {
                output.println("No matching directories; enter the path again.");
            } else {
                output.println("Matching directories:");
                matches.forEach(output::println);
                output.println("Enter a complete directory path.");
            }
        }
    }
}
