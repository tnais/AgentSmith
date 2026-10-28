package org.gianoziaorientale.agent;

import java.util.ArrayList;
import java.util.List;

public final class ModelMenu {

    public static final int PAGE_SIZE = 26;
    public static final int TERMINAL_WIDTH = 80;
    private static final int COLUMN_WIDTH = 26;
    private static final int COLUMN_COUNT = 3;

    private ModelMenu() {}

    public static int pageCount(List<ModuliAiService.Modulo> models) {
        return Math.max(1, (models.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public static String renderPage(List<ModuliAiService.Modulo> models, int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pageCount(models)) {
            throw new IllegalArgumentException("Invalid model menu page.");
        }
        int first = pageIndex * PAGE_SIZE;
        int last = Math.min(models.size(), first + PAGE_SIZE);
        StringBuilder output = new StringBuilder();

        for (int row = first; row < last; row += COLUMN_COUNT) {
            List<List<String>> cells = new ArrayList<>();
            int rowCount = Math.min(COLUMN_COUNT, last - row);
            for (int column = 0; column < rowCount; column++) {
                ModuliAiService.Modulo model = models.get(row + column);
                String label = (char) ('A' + row + column - first) + ") ";
                cells.add(wrap(label, safeText(model.nome()) + " (" + safeText(model.id()) + ")"));
            }

            int height = cells.stream().mapToInt(List::size).max().orElse(0);
            for (int line = 0; line < height; line++) {
                for (int column = 0; column < cells.size(); column++) {
                    List<String> cell = cells.get(column);
                    String text = line < cell.size() ? cell.get(line) : "";
                    output.append(text);
                    if (column < cells.size() - 1) {
                        output.append(" ".repeat(Math.max(1, COLUMN_WIDTH - displayWidth(text) + 2)));
                    }
                }
                output.append(System.lineSeparator());
            }
        }
        return output.toString();
    }

    public static ModuliAiService.Modulo select(
            List<ModuliAiService.Modulo> models, int pageIndex, String choice) {
        if (choice == null || choice.length() != 1) {
            throw new IllegalArgumentException("Choose a model using its letter.");
        }
        char key = Character.toUpperCase(choice.charAt(0));
        int modelIndex = pageIndex * PAGE_SIZE + key - 'A';
        int pageEnd = Math.min(models.size(), (pageIndex + 1) * PAGE_SIZE);
        if (key < 'A' || key > 'Z' || modelIndex < pageIndex * PAGE_SIZE || modelIndex >= pageEnd) {
            throw new IllegalArgumentException("That letter is not a model choice on this page.");
        }
        return models.get(modelIndex);
    }

    public static String safeText(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder();
        value.codePoints()
                .forEach(codePoint -> safe.appendCodePoint(
                        Character.isISOControl(codePoint) ? ' ' : codePoint));
        return safe.toString().replaceAll("\\s+", " ").trim();
    }

    private static List<String> wrap(String label, String value) {
        List<String> lines = new ArrayList<>();
        int firstLineWidth = COLUMN_WIDTH - displayWidth(label);
        String remaining = value;
        lines.add(label + takeLine(remaining, firstLineWidth));
        remaining = dropLine(remaining, firstLineWidth);
        while (!remaining.isEmpty()) {
            String continuation = takeLine(remaining, COLUMN_WIDTH - 3);
            lines.add("   " + continuation);
            remaining = dropLine(remaining, COLUMN_WIDTH - 3);
        }
        return lines;
    }

    private static String takeLine(String text, int width) {
        if (displayWidth(text) <= width) {
            return text;
        }
        int boundary = 0;
        int lastWhitespace = -1;
        int columns = 0;
        while (boundary < text.length()) {
            int codePoint = text.codePointAt(boundary);
            int chars = Character.charCount(codePoint);
            int codePointWidth = terminalWidth(codePoint);
            if (columns + codePointWidth > width) {
                break;
            }
            if (Character.isWhitespace(codePoint)) {
                lastWhitespace = boundary;
            }
            columns += codePointWidth;
            boundary += chars;
        }
        if (boundary == text.length()) {
            return text;
        }
        if (lastWhitespace > 0) {
            return text.substring(0, lastWhitespace);
        }
        return text.substring(0, boundary);
    }

    private static String dropLine(String text, int width) {
        String line = takeLine(text, width);
        int next = line.length();
        while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
            next++;
        }
        return text.substring(next);
    }

    private static int displayWidth(String text) {
        return text.codePoints().map(ModelMenu::terminalWidth).sum();
    }

    private static int terminalWidth(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
            return 2;
        }
        return 1;
    }
}
