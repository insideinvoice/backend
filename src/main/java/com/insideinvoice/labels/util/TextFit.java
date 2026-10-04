package com.insideinvoice.labels.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure text-fitting helpers: shrink-to-fit in 0.5 pt steps down to a floor, then word wrap.
 * Width measurement is injected so the logic is unit-testable without a PDF document.
 */
public final class TextFit {

    public static final double STEP_PT = 0.5;

    /** Measures rendered width of a string at a given font size (points). */
    @FunctionalInterface
    public interface Measurer {
        double width(String text, double sizePt) throws java.io.IOException;
    }

    private TextFit() {
    }

    /**
     * Returns the largest font size in 0.5 pt steps between {@code startPt} and {@code minPt}
     * (inclusive) at which {@code text} fits into {@code maxWidth}. Returns {@code minPt} if it
     * still does not fit at the floor size.
     */
    public static double fitFontSize(String text, double maxWidth, double startPt, double minPt,
                                     Measurer measurer) throws java.io.IOException {
        if (minPt > startPt) {
            throw new IllegalArgumentException("minPt must be <= startPt");
        }
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return minPt;
        }
        for (double size = startPt; size >= minPt - 1e-9; size -= STEP_PT) {
            if (measurer.width(text, size) <= maxWidth) {
                return size;
            }
        }
        return minPt;
    }

    /**
     * Greedy word wrap at a fixed font size. Words longer than a line are hard-broken
     * at character level. Blank input yields an empty list.
     */
    public static List<String> wrapText(String text, double maxWidth, Measurer measurer, double sizePt)
            throws java.io.IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank() || maxWidth <= 0) {
            return lines;
        }
        for (String paragraph : text.trim().split("\\R")) {
            wrapParagraph(paragraph, maxWidth, measurer, sizePt, lines);
        }
        return lines;
    }

    private static void wrapParagraph(String paragraph, double maxWidth, Measurer measurer,
                                      double sizePt, List<String> out) throws java.io.IOException {
        if (paragraph.isBlank()) {
            out.add("");
            return;
        }
        String[] words = paragraph.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (measurer.width(candidate, sizePt) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (measurer.width(word, sizePt) <= maxWidth) {
                line.append(word);
                continue;
            }
            StringBuilder chunk = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                String next = chunk.toString() + word.charAt(i);
                if (!chunk.isEmpty() && measurer.width(next, sizePt) > maxWidth) {
                    out.add(chunk.toString());
                    chunk.setLength(0);
                }
                chunk.append(word.charAt(i));
            }
            line = chunk;
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
    }

    /**
     * Fit a paragraph into at most {@code maxLines}: shrink the size in 0.5 pt steps until the
     * wrapped text fits, then wrap at the final size. If even the floor size overflows, the
     * result is clipped with an ellipsis on the last line.
     */
    public static Result fitParagraph(String text, double maxWidth, double startPt, double minPt,
                                      int maxLines, Measurer measurer) throws java.io.IOException {
        if (maxLines < 1) {
            throw new IllegalArgumentException("maxLines must be >= 1");
        }
        for (double size = startPt; size >= minPt - 1e-9; size -= TextFit.STEP_PT) {
            List<String> lines = wrapText(text, maxWidth, measurer, size);
            if (lines.size() <= maxLines) {
                return new Result(size, lines);
            }
        }
        List<String> lines = wrapText(text, maxWidth, measurer, minPt);
        if (lines.size() > maxLines) {
            List<String> clipped = new ArrayList<>(lines.subList(0, maxLines));
            String last = clipped.get(maxLines - 1);
            clipped.set(maxLines - 1, last.length() > 1 ? last.substring(0, last.length() - 1) + "…" : last);
            return new Result(minPt, clipped);
        }
        return new Result(minPt, lines);
    }

    public record Result(double fontSize, List<String> lines) {
    }
}
