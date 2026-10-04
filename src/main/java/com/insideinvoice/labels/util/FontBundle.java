package com.insideinvoice.labels.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-document font set for label rendering:
 * Liberation Sans (metric-compatible with Arial/Helvetica, OFL) for Latin, plus Noto Sans /
 * Noto Sans Devanagari / Noto Sans Arabic fallbacks so Indian and Arabic addresses do not
 * render as empty boxes. Fonts are embedded subset.
 */
public final class FontBundle {

    private static final String RESOURCE_DIR = "/fonts/";
    private static final String REGULAR = "LiberationSans-Regular.ttf";
    private static final String BOLD = "LiberationSans-Bold.ttf";
    private static final String SANS = "NotoSans-Regular.ttf";
    private static final String DEVANAGARI = "NotoSansDevanagari-Regular.ttf";
    private static final String ARABIC = "NotoSansArabic-Regular.ttf";

    private final PDDocument document;

    private PDType0Font regular;
    private PDType0Font bold;
    private PDType0Font sans;
    private PDType0Font devanagari;
    private PDType0Font arabic;

    private final java.util.Map<String, java.util.Set<Integer>> missing =
            new java.util.concurrent.ConcurrentHashMap<>();

    public FontBundle(PDDocument document) {
        this.document = document;
    }

    public PDType0Font regular() throws IOException {
        if (regular == null) {
            regular = load(REGULAR);
        }
        return regular;
    }

    public PDType0Font bold() throws IOException {
        if (bold == null) {
            bold = load(BOLD);
        }
        return bold;
    }

    private PDType0Font lazySans() throws IOException {
        if (sans == null) {
            sans = load(SANS);
        }
        return sans;
    }

    private PDType0Font lazyDevanagari() throws IOException {
        if (devanagari == null) {
            devanagari = load(DEVANAGARI);
        }
        return devanagari;
    }

    private PDType0Font lazyArabic() throws IOException {
        if (arabic == null) {
            arabic = load(ARABIC);
        }
        return arabic;
    }

    private PDType0Font load(String fileName) throws IOException {
        try (InputStream in = FontBundle.class.getResourceAsStream(RESOURCE_DIR + fileName)) {
            if (in == null) {
                throw new IOException("Font resource missing: " + fileName);
            }
            return PDType0Font.load(document, in, true);
        }
    }

    public record Run(PDType0Font font, String text) {
    }

    /**
     * Splits a string into consecutive runs that each map to a single font.
     * Picks Liberation Regular/Bold for covered codepoints, then Noto Sans,
     * then script-specific fallbacks.
     */
    public List<Run> segment(String text, boolean boldRequested) throws IOException {
        List<Run> runs = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return runs;
        }
        PDType0Font current = null;
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            PDType0Font font = pick(cp, boldRequested);
            if (font == current) {
                buf.appendCodePoint(cp);
            } else {
                if (!buf.isEmpty() && current != null) {
                    runs.add(new Run(current, buf.toString()));
                }
                buf.setLength(0);
                buf.appendCodePoint(cp);
                current = font;
            }
            i += Character.charCount(cp);
        }
        if (!buf.isEmpty() && current != null) {
            runs.add(new Run(current, buf.toString()));
        }
        return runs;
    }

    private PDType0Font pick(int cp, boolean boldRequested) throws IOException {
        PDType0Font primary = boldRequested ? bold() : regular();
        if (covers(primary, cp)) {
            return primary;
        }
        PDType0Font secondary = boldRequested ? regular() : bold();
        if (covers(secondary, cp)) {
            return secondary;
        }
        if (isDevanagari(cp)) {
            PDType0Font deva = lazyDevanagari();
            if (covers(deva, cp)) {
                return deva;
            }
        }
        if (isArabic(cp)) {
            PDType0Font arb = lazyArabic();
            if (covers(arb, cp)) {
                return arb;
            }
        }
        PDType0Font sans = lazySans();
        if (covers(sans, cp)) {
            return sans;
        }
        return primary;
    }

    /**
     * Reliable coverage probe: PDType0Font.hasGlyph() misreports for several of our
     * fonts (verified against PDFBox 3.0.3), while getStringWidth throws exactly
     * when the glyph is missing. Negative results are cached because fit loops
     * would otherwise pay for repeated exceptions.
     */
    private boolean covers(PDType0Font font, int cp) {
        String key = font.getName();
        Set<Integer> miss = missing.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        if (miss.contains(cp)) {
            return false;
        }
        try {
            font.getStringWidth(new String(Character.toChars(cp)));
            return true;
        } catch (IOException | RuntimeException e) {
            miss.add(cp);
            return false;
        }
    }

    private static boolean isDevanagari(int cp) {
        return (cp >= 0x0900 && cp <= 0x097F) || (cp >= 0xA8E0 && cp <= 0xA8FF);
    }

    private static boolean isArabic(int cp) {
        return (cp >= 0x0600 && cp <= 0x06FF) || (cp >= 0x0750 && cp <= 0x077F)
                || (cp >= 0x08A0 && cp <= 0x08FF) || (cp >= 0xFB50 && cp <= 0xFDFF)
                || (cp >= 0xFE70 && cp <= 0xFEFF);
    }
}
