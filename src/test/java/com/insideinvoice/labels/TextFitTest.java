package com.insideinvoice.labels;

import com.insideinvoice.labels.util.TextFit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextFitTest {

    /** Width model: every character is 0.5 * size wide. */
    private static final TextFit.Measurer CHAR_W =
            (text, size) -> text.length() * 0.5 * size;

    @Test
    void fitsAtStartSizeWhenNarrow() throws IOException {
        double size = TextFit.fitFontSize("SHORT", 1000, 16, 6, CHAR_W);
        assertThat(size).isEqualTo(16);
    }

    @Test
    void shrinksInHalfPointStepsUntilItFits() throws IOException {
        // "ABCDEFGHIJKLMNOP" = 16 chars * 0.5 * size <= 60  => size <= 7.5
        double size = TextFit.fitFontSize("ABCDEFGHIJKLMNOP", 60, 16, 6, CHAR_W);
        assertThat(size).isEqualTo(7.5);
    }

    @Test
    void returnsFloorWhenEvenFloorOverflows() throws IOException {
        double size = TextFit.fitFontSize("X".repeat(500), 10, 16, 6, CHAR_W);
        assertThat(size).isEqualTo(6);
    }

    @Test
    void wrapsOnWordBoundaries() throws IOException {
        List<String> lines = TextFit.wrapText("one two three four five", 50, CHAR_W, 10);
        assertThat(lines).allSatisfy(line ->
                assertThat(CHAR_W.width(line, 10)).isLessThanOrEqualTo(50));
        assertThat(String.join(" ", lines)).isEqualTo("one two three four five");
    }

    @Test
    void hardBreaksWordsLongerThanLine() throws IOException {
        List<String> lines = TextFit.wrapText("SUPERLONGTOKEN", 20, CHAR_W, 10);
        assertThat(lines).hasSize(4); // 10pt => char width 5, 4 chars per 20pt line
        assertThat(lines.get(0)).isEqualTo("SUPE");
        assertThat(lines.get(3)).isEqualTo("EN");
    }

    @Test
    void fitParagraphShrinksThenWrapsWithinMaxLines() throws IOException {
        String text = "word ".repeat(60).trim();
        TextFit.Result r = TextFit.fitParagraph(text, 100, 16, 6, 5, CHAR_W);
        assertThat(r.lines()).hasSizeLessThanOrEqualTo(5);
        assertThat(r.fontSize()).isGreaterThanOrEqualTo(6).isLessThanOrEqualTo(16);
        assertThat(r.fontSize() % TextFit.STEP_PT).isEqualTo(0.0);
    }

    @Test
    void fitParagraphClipsWithEllipsisWhenHopeless() throws IOException {
        String text = "W".repeat(400);
        TextFit.Result r = TextFit.fitParagraph(text, 30, 16, 6, 3, CHAR_W);
        assertThat(r.lines()).hasSize(3);
        assertThat(r.lines().get(2)).endsWith("…");
        assertThat(r.fontSize()).isEqualTo(6);
    }

    @Test
    void rejectsInvalidBounds() {
        assertThatThrownBy(() -> TextFit.fitFontSize("a", 10, 6, 16, CHAR_W))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextFit.fitParagraph("a", 10, 16, 6, 0, CHAR_W))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
