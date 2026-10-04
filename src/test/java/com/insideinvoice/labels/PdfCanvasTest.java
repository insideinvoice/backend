package com.insideinvoice.labels;

import com.insideinvoice.labels.renderer.PdfCanvas;
import com.insideinvoice.labels.util.LabelUnits;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class PdfCanvasTest {

    @Test
    void labelPageIsExactly4x6InchInPoints() throws IOException {
        try (PdfCanvas canvas = new PdfCanvas(203)) {
            canvas.beginLabelPage(101.6, 152.4);
            canvas.fillRect(10, 10, 20, 5);
            byte[] pdf = canvas.save();
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                assertThat(doc.getNumberOfPages()).isEqualTo(1);
                PDRectangle box = doc.getPage(0).getMediaBox();
                // 4x6 in = 288 x 432 pt (page box must equal label size, no margins)
                assertThat(box.getWidth()).isCloseTo(288f, org.assertj.core.data.Offset.offset(0.05f));
                assertThat(box.getHeight()).isCloseTo(432f, org.assertj.core.data.Offset.offset(0.05f));
                // CropBox/TrimBox/BleedBox must match MediaBox
                PDRectangle crop = doc.getPage(0).getCropBox();
                PDRectangle trim = doc.getPage(0).getTrimBox();
                assertThat(crop.getWidth()).isEqualTo(box.getWidth());
                assertThat(crop.getHeight()).isEqualTo(box.getHeight());
                assertThat(trim.getWidth()).isEqualTo(box.getWidth());
                assertThat(trim.getHeight()).isEqualTo(box.getHeight());
            }
        }
    }

    @Test
    void viewerPreferenceIsPrintScalingNone() throws IOException {
        byte[] pdf;
        try (PdfCanvas canvas = new PdfCanvas(203)) {
            canvas.beginLabelPage(101.6, 152.4);
            canvas.text("TEST", 5, 5, 12, false, PdfCanvas.Align.LEFT);
            pdf = canvas.save();
        }
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String scaling = doc.getDocumentCatalog().getViewerPreferences().getPrintScaling();
            assertThat(scaling).isEqualTo("None");
            PDDocumentInformation info = doc.getDocumentInformation();
            assertThat(info.getProducer()).isEqualTo("Inside Invoice");
        }
    }

    @Test
    void snapRoundsToPrinterDotGridAt203Dpi() throws IOException {
        try (PdfCanvas canvas = new PdfCanvas(203)) {
            double dot = LabelUnits.dotMm(203);
            assertThat(canvas.snap(0.13)).isCloseTo(1 * dot, offset(1e-9));   // 1.03 dots -> 1
            assertThat(canvas.snap(0.19)).isCloseTo(2 * dot, offset(1e-9));   // 1.52 dots -> 2
            assertThat(canvas.snap(101.6)).isCloseTo(101.6, offset(1e-9));    // 4 in = 812 dots exact
            assertThat(canvas.snap(101.7)).isCloseTo(813 * dot, offset(1e-9));
        }
        try (PdfCanvas canvas = new PdfCanvas(300)) {
            assertThat(canvas.snap(25.4)).isCloseTo(25.4, offset(1e-9));      // 1 in = 300 dots
        }
    }

    @Test
    void textMeasurementUsesEmbeddedFonts() throws IOException {
        try (PdfCanvas canvas = new PdfCanvas(203)) {
            canvas.beginLabelPage(101.6, 152.4);
            double w12 = canvas.textWidthPt("SHIPPING LABEL", 12, true);
            double w8 = canvas.textWidthPt("SHIPPING LABEL", 8, true);
            assertThat(w12).isGreaterThan(0);
            assertThat(w8).isLessThan(w12);
            // fallback script runs must measure too (no exception, non-zero width for Devanagari)
            double deva = canvas.textWidthPt("नई दिल्ली", 11, false);
            assertThat(deva).isGreaterThan(0);
        }
    }

    @Test
    void multiUpSheetKeepsCellRelativeCoordinates() throws IOException {
        try (PdfCanvas canvas = new PdfCanvas(203)) {
            canvas.beginSheetPage(210, 297); // A4
            canvas.setCell(3, 20);
            canvas.fillRect(0, 0, 10, 10); // must not throw / must stay on page
            canvas.clearCell();
            byte[] pdf = canvas.save();
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                PDRectangle box = doc.getPage(0).getMediaBox();
                assertThat(box.getWidth()).isCloseTo(210 * 2.8346457f, org.assertj.core.data.Offset.offset(0.1f));
                assertThat(box.getHeight()).isCloseTo(297 * 2.8346457f, org.assertj.core.data.Offset.offset(0.1f));
            }
        }
    }

    @Test
    void drawingWithoutPageThrows() {
        try (PdfCanvas canvas = new PdfCanvas(203)) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> canvas.fillRect(0, 0, 10, 10));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
