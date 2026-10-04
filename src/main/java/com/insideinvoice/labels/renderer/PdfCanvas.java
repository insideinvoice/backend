package com.insideinvoice.labels.renderer;

import com.google.zxing.common.BitMatrix;
import com.insideinvoice.labels.util.BarcodeUtils;
import com.insideinvoice.labels.util.FontBundle;
import com.insideinvoice.labels.util.LabelUnits;
import com.insideinvoice.labels.util.TextFit;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * PDFBox wrapper for print-ready thermal label rendering.
 *
 * <p>All coordinates are in millimetres with a TOP-LEFT origin (PDF origin is bottom-left and is
 * converted internally). Every coordinate, size and line weight snaps to the printer dot grid for
 * the configured DPI so thermal output never blurs. Pages are created with
 * MediaBox = CropBox = TrimBox = label size and PrintScaling = None.</p>
 *
 * <p>Multi-up sheets (A4/Letter N-up) use {@link #beginSheetPage} plus {@link #setCell}; drawing
 * coordinates then stay relative to each cell's top-left corner.</p>
 */
public final class PdfCanvas implements AutoCloseable {

    public enum Align {
        LEFT, CENTER, RIGHT
    }

    private static final double CAP_FALLBACK = 0.70;
    private static final Calendar FIXED_DATE =
            new GregorianCalendar(2026, Calendar.JANUARY, 1, 0, 0, 0);

    private final PDDocument document;
    private final int dpi;
    private final FontBundle fonts;

    private PDPageContentStream cs;
    private double pageHmm;
    private double cellX;
    private double cellY;
    private boolean pageOpen;

    public PdfCanvas(int dpi) {
        this(new PDDocument(), dpi);
    }

    public PdfCanvas(PDDocument document, int dpi) {
        if (dpi != 203 && dpi != 300) {
            throw new IllegalArgumentException("DPI must be 203 or 300");
        }
        this.document = document;
        this.dpi = dpi;
        this.fonts = new FontBundle(document);
    }

    public PDDocument document() {
        return document;
    }

    public FontBundle fonts() {
        return fonts;
    }

    public int dpi() {
        return dpi;
    }

    // ------------------------------------------------------------------ pages

    /** Starts a new single-label page exactly label-sized (no PDF-added margins). */
    public void beginLabelPage(double widthMm, double heightMm) throws IOException {
        beginPage(widthMm, heightMm, true);
    }

    /** Starts a new sheet page for multi-up layouts (A4 / US Letter). */
    public void beginSheetPage(double widthMm, double heightMm) throws IOException {
        beginPage(widthMm, heightMm, false);
        clearCell();
    }

    private void beginPage(double widthMm, double heightMm, boolean labelCell) throws IOException {
        closePage();
        // Label pages snap to the printer dot grid (thermal roll widths); sheet pages
        // (A4/Letter) keep the exact requested paper size.
        double w = labelCell ? LabelUnits.snapMm(widthMm, dpi) : widthMm;
        double h = labelCell ? LabelUnits.snapMm(heightMm, dpi) : heightMm;
        PDRectangle box = new PDRectangle((float) LabelUnits.mmToPt(w), (float) LabelUnits.mmToPt(h));
        PDPage page = new PDPage(box);
        page.setMediaBox(box);
        page.setCropBox(box);
        page.setTrimBox(box);
        page.setBleedBox(box);
        document.addPage(page);
        cs = new PDPageContentStream(document, page);
        pageHmm = h;
        pageOpen = true;
        cs.setNonStrokingColor(Color.WHITE);
        cs.addRect(0, 0, (float) LabelUnits.mmToPt(w), (float) LabelUnits.mmToPt(h));
        cs.fill();
        if (labelCell) {
            cellX = 0;
            cellY = 0;
        }
    }

    /** Selects the current cell (top-left coordinates on the sheet, mm) for multi-up rendering. */
    public void setCell(double xMm, double yMm) {
        cellX = xMm;
        cellY = yMm;
    }

    public void clearCell() {
        cellX = 0;
        cellY = 0;
    }

    public double pageHeightMm() {
        return pageHmm;
    }

    // ------------------------------------------------------------ conversions

    /** Snaps a millimetre value to the printer dot grid at this canvas' DPI. */
    public double snap(double mm) {
        return LabelUnits.snapMm(mm, dpi);
    }

    private float px(double xMm) {
        return (float) LabelUnits.mmToPt(LabelUnits.snapMm(cellX + xMm, dpi));
    }

    private float py(double yTopMm) {
        double absTop = LabelUnits.snapMm(cellY + yTopMm, dpi);
        return (float) LabelUnits.mmToPt(pageHmm - absTop);
    }

    private float pw(double wMm) {
        return (float) LabelUnits.mmToPt(LabelUnits.snapMm(wMm, dpi));
    }

    private float linePt(double lineMm) {
        double snapped = LabelUnits.snapMm(lineMm, dpi);
        return (float) LabelUnits.mmToPt(Math.max(snapped, LabelUnits.dotMm(dpi)));
    }

    // ---------------------------------------------------------------- shapes

    public void fillRect(double xMm, double yTopMm, double wMm, double hMm) throws IOException {
        ensureOpen();
        double x = LabelUnits.snapMm(cellX + xMm, dpi);
        double yTop = LabelUnits.snapMm(cellY + yTopMm, dpi);
        double w = LabelUnits.snapMm(wMm, dpi);
        double h = LabelUnits.snapMm(hMm, dpi);
        cs.addRect((float) LabelUnits.mmToPt(x),
                (float) LabelUnits.mmToPt(pageHmm - (yTop + h)),
                (float) LabelUnits.mmToPt(w),
                (float) LabelUnits.mmToPt(h));
        cs.fill();
    }

    public void strokeRect(double xMm, double yTopMm, double wMm, double hMm, double lineMm) throws IOException {
        ensureOpen();
        double x = LabelUnits.snapMm(cellX + xMm, dpi);
        double yTop = LabelUnits.snapMm(cellY + yTopMm, dpi);
        double w = LabelUnits.snapMm(wMm, dpi);
        double h = LabelUnits.snapMm(hMm, dpi);
        cs.setLineWidth(linePt(lineMm));
        cs.addRect((float) LabelUnits.mmToPt(x),
                (float) LabelUnits.mmToPt(pageHmm - (yTop + h)),
                (float) LabelUnits.mmToPt(w),
                (float) LabelUnits.mmToPt(h));
        cs.stroke();
    }

    /** Outer keyline / border uses the 2-dot border weight. */
    public void strokeBorder(double xMm, double yTopMm, double wMm, double hMm) throws IOException {
        strokeRect(xMm, yTopMm, wMm, hMm, LabelUnits.lineWeightMm(dpi, true));
    }

    public void line(double x1Mm, double y1Mm, double x2Mm, double y2Mm, double lineMm) throws IOException {
        ensureOpen();
        cs.setLineWidth(linePt(lineMm));
        cs.moveTo(px(x1Mm), py(y1Mm));
        cs.lineTo(px(x2Mm), py(y2Mm));
        cs.stroke();
    }

    public void fillPolygon(double[][] pointsMm) throws IOException {
        ensureOpen();
        path(pointsMm, false);
        cs.fill();
    }

    public void strokePolygon(double[][] pointsMm, double lineMm) throws IOException {
        ensureOpen();
        cs.setLineWidth(linePt(lineMm));
        path(pointsMm, true);
        cs.stroke();
    }

    private void path(double[][] pointsMm, boolean close) throws IOException {
        for (int i = 0; i < pointsMm.length; i++) {
            float x = px(pointsMm[i][0]);
            float y = py(pointsMm[i][1]);
            if (i == 0) {
                cs.moveTo(x, y);
            } else {
                cs.lineTo(x, y);
            }
        }
        if (close) {
            cs.closePath();
        }
    }

    /** Clips subsequent drawing to the given polygon. Undo with {@link #restoreState()}. */
    public void clipPolygon(double[][] pointsMm) throws IOException {
        ensureOpen();
        path(pointsMm, true);
        cs.clip();
    }

    public void saveState() throws IOException {
        ensureOpen();
        cs.saveGraphicsState();
    }

    public void restoreState() throws IOException {
        ensureOpen();
        cs.restoreGraphicsState();
    }

    public void fillEllipse(double cxMm, double cyMm, double rxMm, double ryMm) throws IOException {
        ensureOpen();
        ellipsePath(cxMm, cyMm, rxMm, ryMm);
        cs.fill();
    }

    public void strokeEllipse(double cxMm, double cyMm, double rxMm, double ryMm, double lineMm) throws IOException {
        ensureOpen();
        cs.setLineWidth(linePt(lineMm));
        ellipsePath(cxMm, cyMm, rxMm, ryMm);
        cs.stroke();
    }

    private void ellipsePath(double cxMm, double cyMm, double rxMm, double ryMm) throws IOException {
        final double k = 0.5522847498;
        double x0 = cxMm + rxMm;
        double x1 = cxMm - rxMm;
        double y0 = cyMm;
        double y1 = cyMm + ryMm;     // top (mm, top-left origin: +y is down; these are just points)
        double y2 = cyMm - ryMm;     // bottom
        cs.moveTo(px(x0), py(cyMm));
        cs.curveTo(px(x0), py(cyMm - ryMm * k),
                px(cxMm + rxMm * k), py(y2),
                px(cxMm), py(y2));
        cs.curveTo(px(cxMm - rxMm * k), py(y2),
                px(x1), py(cyMm - ryMm * k),
                px(x1), py(cyMm));
        cs.curveTo(px(x1), py(cyMm + ryMm * k),
                px(cxMm - rxMm * k), py(y1),
                px(cxMm), py(y1));
        cs.curveTo(px(cxMm + rxMm * k), py(y1),
                px(x0), py(cyMm + ryMm * k),
                px(x0), py(cyMm));
        cs.closePath();
    }

    // ----------------------------------------------------------------- color

    public void fillBlack() throws IOException {
        cs.setNonStrokingColor(Color.BLACK);
    }

    public void fillWhite() throws IOException {
        cs.setNonStrokingColor(Color.WHITE);
    }

    public void fillGray(float gray) throws IOException {
        cs.setNonStrokingColor(gray);
    }

    public void fillRgb(int r, int g, int b) throws IOException {
        cs.setNonStrokingColor(new Color(r, g, b));
    }

    public void strokeBlack() throws IOException {
        cs.setStrokingColor(Color.BLACK);
    }

    public void strokeRgb(int r, int g, int b) throws IOException {
        cs.setStrokingColor(new Color(r, g, b));
    }

    public void setDash(float[] pattern, float phase) throws IOException {
        cs.setLineDashPattern(pattern, phase);
    }

    public void resetDash() throws IOException {
        cs.setLineDashPattern(new float[0], 0);
    }

    // ------------------------------------------------------------------ text

    /** Width of a single-run string in points at the given size (Liberation/Noto fallback aware). */
    public double textWidthPt(String text, double sizePt, boolean bold) throws IOException {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (FontBundle.Run run : fonts.segment(text, bold)) {
            total += run.font().getStringWidth(run.text()) / 1000.0 * sizePt;
        }
        return total;
    }

    public double textWidthMm(String text, double sizePt, boolean bold) throws IOException {
        return LabelUnits.ptToMm(textWidthPt(text, sizePt, bold));
    }

    private double capAscentMm(PDType0Font font, double sizePt) {
        try {
            PDFontDescriptor desc = font.getFontDescriptor();
            if (desc != null && desc.getCapHeight() > 0) {
                return LabelUnits.ptToMm(desc.getCapHeight() / 1000.0 * sizePt);
            }
        } catch (Exception ignored) {
            // fall through to ratio
        }
        return LabelUnits.ptToMm(CAP_FALLBACK * sizePt);
    }

    /**
     * Draws text with its visual top at {@code yTopMm} (baseline derived from the font cap height),
     * anchored per {@code align} around {@code xMm}. Supports script fallback runs.
     */
    public void text(String value, double xMm, double yTopMm, double sizePt, boolean bold, Align align)
            throws IOException {
        ensureOpen();
        if (value == null || value.isEmpty()) {
            return;
        }
        double widthMm = textWidthMm(value, sizePt, bold);
        double left = xMm;
        if (align == Align.CENTER) {
            left = xMm - widthMm / 2;
        } else if (align == Align.RIGHT) {
            left = xMm - widthMm;
        }
        double baselineTop = LabelUnits.snapMm(cellY + yTopMm, dpi) - cellY;
        drawRuns(value, left, baselineTop, sizePt, bold, 0);
    }

    private void drawRuns(String value, double leftTopMm, double yTopMm, double sizePt, boolean bold,
                          double rotationRad) throws IOException {
        double cursorMm = 0;
        for (FontBundle.Run run : fonts.segment(value, bold)) {
            double runWmm = LabelUnits.ptToMm(run.font().getStringWidth(run.text()) / 1000.0 * sizePt);
            double ascentMm = capAscentMm(run.font(), sizePt);
            double x = leftTopMm + cursorMm;
            float baselineX;
            float baselineY;
            if (rotationRad == 0) {
                baselineX = px(x);
                baselineY = py(yTopMm + ascentMm);
            } else {
                baselineX = px(x);
                baselineY = py(yTopMm + ascentMm);
            }
            cs.beginText();
            cs.setFont(run.font(), (float) sizePt);
            if (rotationRad == 0) {
                cs.setTextMatrix(Matrix.getTranslateInstance(baselineX, baselineY));
            } else {
                // rotate around the point, then advance along the baseline
                float originX = px(leftTopMm);
                float originY = py(yTopMm);
                Matrix m = Matrix.getRotateInstance(rotationRad, originX, originY);
                float advancePt = (float) LabelUnits.mmToPt(cursorMm);
                m.concatenate(Matrix.getTranslateInstance(advancePt, 0));
                cs.setTextMatrix(m);
            }
            cs.showText(run.text());
            cs.endText();
            cursorMm += runWmm;
        }
    }

    /** Draws text rotated around its centre point (used for vertical SHIP TO captions). */
    public void rotatedText(String value, double cxMm, double cyMm, double angleDeg, double sizePt, boolean bold)
            throws IOException {
        ensureOpen();
        if (value == null || value.isEmpty()) {
            return;
        }
        double widthMm = textWidthMm(value, sizePt, bold);
        double left = cxMm - widthMm / 2;
        double top = cyMm - LabelUnits.ptToMm(sizePt * CAP_FALLBACK) / 2;
        drawRuns(value, left, top, sizePt, bold, Math.toRadians(angleDeg));
    }

    /** Width of a single-line string in mm (for chip/box sizing). */
    public double stringWidth(String text, double sizePt, boolean bold) throws IOException {
        return textWidthMm(text, sizePt, bold);
    }

    /** Largest font size (0.5 pt steps) that fits the text into maxWidthMm. */
    public double fitFontSize(String text, double maxWidthMm, double startPt, double minPt, boolean bold)
            throws IOException {
        return TextFit.fitFontSize(text, maxWidthMm, startPt, minPt,
                (s, size) -> textWidthMm(s, size, bold));
    }

    /** Word-wraps text at a fixed size to a max width in mm. */
    public List<String> wrap(String text, double maxWidthMm, double sizePt, boolean bold) throws IOException {
        return TextFit.wrapText(text, maxWidthMm,
                (s, size) -> textWidthMm(s, sizePt, bold), sizePt);
    }

    /** Fit-then-wrap a paragraph; returns font size + lines. */
    public TextFit.Result fitParagraph(String text, double maxWidthMm, double startPt, double minPt,
                                       int maxLines, boolean bold) throws IOException {
        return TextFit.fitParagraph(text, maxWidthMm, startPt, minPt, maxLines,
                (s, size) -> textWidthMm(s, size, bold));
    }

    /** Draws pre-wrapped lines starting at yTopMm with the given line height; returns the bottom mm. */
    public double drawLines(List<String> lines, double xMm, double yTopMm, double lineHeightMm,
                            double sizePt, boolean bold, Align align) throws IOException {
        double y = yTopMm;
        for (String ln : lines) {
            text(ln, xMm, y, sizePt, bold, align);
            y += lineHeightMm;
        }
        return y;
    }

    /**
     * Fit + draw in one call: shrinks to minPt, wraps to maxLines, draws and returns the bottom mm.
     * Line height is 1.15x the chosen font size (spec: line-height 1.15).
     */
    public double drawParagraph(String text, double xMm, double yTopMm, double maxWidthMm,
                                double startPt, double minPt, int maxLines, boolean bold, Align align)
            throws IOException {
        TextFit.Result result = fitParagraph(text, maxWidthMm, startPt, minPt, maxLines, bold);
        double lineHeightMm = LabelUnits.ptToMm(result.fontSize() * 1.15);
        return drawLines(result.lines(), xMm, yTopMm, lineHeightMm, result.fontSize(), bold, align);
    }

    // --------------------------------------------------------------- barcodes

    /**
     * Draws a linear barcode (Code 128 / GS1-128 / UPC ...) as vector rectangles centred in the box.
     * Module width is always an integer number of printer dots; quiet zones use the same module width.
     *
     * @return the bottom edge (mm, top-left origin) of the drawn bars, for placing human-readable text.
     */
    public double barcodeLinear(BitMatrix matrix, double boxXMm, double boxTopYMm, double boxWMm,
                                double barHMm, int quietZoneModules, int minModuleDots) throws IOException {
        ensureOpen();
        double dotMm = LabelUnits.dotMm(dpi);
        int modules = matrix.getWidth();
        int availableDots = (int) Math.floor(boxWMm / dotMm);
        int moduleDots = BarcodeUtils.moduleWidthDots(modules, quietZoneModules, availableDots, minModuleDots);
        int totalDots = (modules + 2 * quietZoneModules) * moduleDots;
        // Integer-dot placement: every bar edge lands exactly on the printer grid so
        // modules stay uniform (no per-edge snap jitter that breaks decoders).
        int boxX0 = (int) Math.round((cellX + boxXMm) / dotMm);
        int originX = boxX0 + Math.max(0, (availableDots - totalDots) / 2) + quietZoneModules * moduleDots;
        double barH = Math.max(LabelUnits.snapMm(barHMm, dpi), dotMm);
        double yPdf = LabelUnits.mmToPt(pageHmm - LabelUnits.snapMm(cellY + boxTopYMm + barH, dpi));

        fillBlack();
        int x = 0;
        while (x < modules) {
            if (matrix.get(x, 0)) {
                int run = 1;
                while (x + run < modules && matrix.get(x + run, 0)) {
                    run++;
                }
                double rectX = (originX + x * moduleDots) * dotMm;
                double rectW = (run * moduleDots) * dotMm;
                cs.addRect((float) LabelUnits.mmToPt(rectX), (float) yPdf,
                        (float) LabelUnits.mmToPt(rectW),
                        (float) LabelUnits.mmToPt(barH));
                x += run;
            } else {
                x++;
            }
        }
        cs.fill();
        return boxTopYMm + barH;
    }

    /**
     * Draws a 2D code (Data Matrix / QR) as vector squares centred in the box with the requested
     * quiet zone in modules. Returns the bottom edge (mm).
     */
    public double barcode2D(BitMatrix matrix, double boxXMm, double boxTopYMm, double boxWMm,
                            double boxHMm, int quietZoneModules) throws IOException {
        ensureOpen();
        double dotMm = LabelUnits.dotMm(dpi);
        int cols = matrix.getWidth();
        int rows = matrix.getHeight();
        int wDots = (int) Math.floor(boxWMm / dotMm);
        int hDots = (int) Math.floor(boxHMm / dotMm);
        int moduleDots = Math.max(1, Math.min(
                wDots / (cols + 2 * quietZoneModules),
                hDots / (rows + 2 * quietZoneModules)));
        int totalDotsW = (cols + 2 * quietZoneModules) * moduleDots;
        int totalDotsH = (rows + 2 * quietZoneModules) * moduleDots;
        // Integer-dot placement keeps every module edge uniformly spaced; decoders
        // (especially DataMatrix) fail on per-edge snap jitter.
        int boxX0 = (int) Math.round((cellX + boxXMm) / dotMm);
        int boxY0 = (int) Math.round((cellY + boxTopYMm) / dotMm);
        int originX = boxX0 + Math.max(0, (wDots - totalDotsW) / 2) + quietZoneModules * moduleDots;
        int originY = boxY0 + Math.max(0, (hDots - totalDotsH) / 2) + quietZoneModules * moduleDots;

        fillBlack();
        for (int my = 0; my < rows; my++) {
            int mx = 0;
            while (mx < cols) {
                if (matrix.get(mx, my)) {
                    int run = 1;
                    while (mx + run < cols && matrix.get(mx + run, my)) {
                        run++;
                    }
                    double rectX = (originX + mx * moduleDots) * dotMm;
                    double rectTop = (originY + my * moduleDots) * dotMm;
                    double rectBottom = (originY + (my + 1) * moduleDots) * dotMm;
                    cs.addRect((float) LabelUnits.mmToPt(rectX),
                            (float) LabelUnits.mmToPt(pageHmm - rectBottom),
                            (float) LabelUnits.mmToPt((run * moduleDots) * dotMm),
                            (float) LabelUnits.mmToPt(rectBottom - rectTop));
                    mx += run;
                } else {
                    mx++;
                }
            }
        }
        cs.fill();
        double startY = (originY - quietZoneModules * moduleDots) * dotMm;
        return boxTopYMm + Math.max(0, (boxHMm - totalDotsH * dotMm) / 2) + rows * moduleDots * dotMm;
    }

    // -------------------------------------------------------------- SVG paths

    /**
     * Draws an SVG path subset (M/m, L/l, H/h, V/v, C/c, Q/q, Z/z, multiple subpaths)
     * where path units are scaled by {@code mmPerUnit} and placed with origin (xMm, yTopMm).
     * Either fills or strokes with a snapped line weight.
     */
    public void svgPath(String d, double xMm, double yTopMm, double mmPerUnit,
                        boolean fill, Double strokeLineMm) throws IOException {
        ensureOpen();
        List<double[][]> subpaths = new ArrayList<>();
        List<double[]> current = new ArrayList<>();
        double cx = 0;
        double cy = 0;
        double startX = 0;
        double startY = 0;
        char lastCmd = 0;

        String[] tokens = d.trim().replaceAll(",", " ").split("\\s+");
        int i = 0;
        char cmd = 'M';
        while (i < tokens.length) {
            String t = tokens[i];
            if (t.length() > 1 && Character.isLetter(t.charAt(0))
                    && "MLHVCQZmlhvcqz".indexOf(t.charAt(0)) >= 0
                    && (Character.isDigit(t.charAt(1)) || t.charAt(1) == '-')) {
                // glued command + number ("M50 4") — valid SVG; split in place
                cmd = t.charAt(0);
                if (cmd == 'Z' || cmd == 'z') {
                    if (!current.isEmpty()) {
                        subpaths.add(current.toArray(new double[0][]));
                        current = new ArrayList<>();
                    }
                    cx = startX;
                    cy = startY;
                    lastCmd = cmd;
                    i++;
                    continue;
                }
                tokens[i] = t.substring(1);
            } else if (t.length() == 1 && Character.isLetter(t.charAt(0))) {
                cmd = t.charAt(0);
                i++;
                if (cmd == 'Z' || cmd == 'z') {
                    if (!current.isEmpty()) {
                        subpaths.add(current.toArray(new double[0][]));
                        current = new ArrayList<>();
                    }
                    cx = startX;
                    cy = startY;
                    lastCmd = cmd;
                    continue;
                }
            } else if (lastCmd == 'M') {
                cmd = 'L';
            } else if (lastCmd == 'm') {
                cmd = 'l';
            }
            switch (cmd) {
                case 'M' -> {
                    cx = Double.parseDouble(tokens[i++]);
                    cy = Double.parseDouble(tokens[i++]);
                    if (!current.isEmpty()) {
                        subpaths.add(current.toArray(new double[0][]));
                    }
                    current = new ArrayList<>();
                    startX = cx;
                    startY = cy;
                    current.add(new double[]{cx, cy});
                    lastCmd = 'M';
                    cmd = 'L'; // subsequent pairs are implicit lineto
                }
                case 'm' -> {
                    cx += Double.parseDouble(tokens[i++]);
                    cy += Double.parseDouble(tokens[i++]);
                    if (!current.isEmpty()) {
                        subpaths.add(current.toArray(new double[0][]));
                    }
                    current = new ArrayList<>();
                    startX = cx;
                    startY = cy;
                    current.add(new double[]{cx, cy});
                    lastCmd = 'm';
                    cmd = 'l';
                }
                case 'L' -> {
                    cx = Double.parseDouble(tokens[i++]);
                    cy = Double.parseDouble(tokens[i++]);
                    current.add(new double[]{cx, cy});
                    lastCmd = 'L';
                }
                case 'l' -> {
                    cx += Double.parseDouble(tokens[i++]);
                    cy += Double.parseDouble(tokens[i++]);
                    current.add(new double[]{cx, cy});
                    lastCmd = 'l';
                }
                case 'H' -> {
                    cx = Double.parseDouble(tokens[i++]);
                    current.add(new double[]{cx, cy});
                    lastCmd = 'H';
                }
                case 'h' -> {
                    cx += Double.parseDouble(tokens[i++]);
                    current.add(new double[]{cx, cy});
                    lastCmd = 'h';
                }
                case 'V' -> {
                    cy = Double.parseDouble(tokens[i++]);
                    current.add(new double[]{cx, cy});
                    lastCmd = 'V';
                }
                case 'v' -> {
                    cy += Double.parseDouble(tokens[i++]);
                    current.add(new double[]{cx, cy});
                    lastCmd = 'v';
                }
                case 'C' -> {
                    double x1 = Double.parseDouble(tokens[i++]);
                    double y1 = Double.parseDouble(tokens[i++]);
                    double x2 = Double.parseDouble(tokens[i++]);
                    double y2 = Double.parseDouble(tokens[i++]);
                    cx = Double.parseDouble(tokens[i++]);
                    cy = Double.parseDouble(tokens[i++]);
                    emitCurve(current, x1, y1, x2, y2, cx, cy);
                    lastCmd = 'C';
                }
                case 'c' -> {
                    double x1 = cx + Double.parseDouble(tokens[i++]);
                    double y1 = cy + Double.parseDouble(tokens[i++]);
                    double x2 = cx + Double.parseDouble(tokens[i++]);
                    double y2 = cy + Double.parseDouble(tokens[i++]);
                    cx += Double.parseDouble(tokens[i++]);
                    cy += Double.parseDouble(tokens[i++]);
                    emitCurve(current, x1, y1, x2, y2, cx, cy);
                    lastCmd = 'c';
                }
                case 'Q' -> {
                    double x1 = Double.parseDouble(tokens[i++]);
                    double y1 = Double.parseDouble(tokens[i++]);
                    cx = Double.parseDouble(tokens[i++]);
                    cy = Double.parseDouble(tokens[i++]);
                    // approximate quadratic with cubic
                    double c1x = x1 + (cx - x1) / 3.0 * 2;
                    double c1y = y1 + (cy - y1) / 3.0 * 2;
                    double c2x = x1 + (cx - x1) / 3.0;
                    double c2y = y1 + (cy - y1) / 3.0;
                    emitCurve(current, c1x, c1y, c2x, c2y, cx, cy);
                    lastCmd = 'Q';
                }
                case 'q' -> {
                    double x1 = cx + Double.parseDouble(tokens[i++]);
                    double y1 = cy + Double.parseDouble(tokens[i++]);
                    cx += Double.parseDouble(tokens[i++]);
                    cy += Double.parseDouble(tokens[i++]);
                    double c1x = x1 + (cx - x1) / 3.0 * 2;
                    double c1y = y1 + (cy - y1) / 3.0 * 2;
                    double c2x = x1 + (cx - x1) / 3.0;
                    double c2y = y1 + (cy - y1) / 3.0;
                    emitCurve(current, c1x, c1y, c2x, c2y, cx, cy);
                    lastCmd = 'q';
                }
                default -> throw new IllegalArgumentException("Unsupported SVG path command: " + cmd);
            }
        }
        if (!current.isEmpty()) {
            subpaths.add(current.toArray(new double[0][]));
        }

        for (double[][] sp : subpaths) {
            for (int p = 0; p < sp.length; p++) {
                double ux = sp[p][0];
                double uy = sp[p][1];
                double mx = xMm + ux * mmPerUnit;
                double my = yTopMm + uy * mmPerUnit;
                if (p == 0) {
                    cs.moveTo(px(mx), py(my));
                } else {
                    cs.lineTo(px(mx), py(my));
                }
            }
            cs.closePath();
        }
        if (fill) {
            cs.fill();
        } else {
            cs.setLineWidth(linePt(strokeLineMm == null ? 0.4 : strokeLineMm));
            cs.stroke();
        }
    }

    /**
     * Draws an SVG path with Bezier curves preserved (uses the raw curve segments rather than
     * polyline approximation). Used by hazard symbols where curves matter.
     */
    public void svgPathCurves(String d, double xMm, double yTopMm, double mmPerUnit,
                              boolean fill, Double strokeLineMm) throws IOException {
        ensureOpen();
        PathParser.parse(d, (op, coords) -> {
            switch (op) {
                case 'M' -> cs.moveTo(px(xMm + coords[0] * mmPerUnit), py(yTopMm + coords[1] * mmPerUnit));
                case 'L' -> cs.lineTo(px(xMm + coords[0] * mmPerUnit), py(yTopMm + coords[1] * mmPerUnit));
                case 'C' -> cs.curveTo(
                        px(xMm + coords[0] * mmPerUnit), py(yTopMm + coords[1] * mmPerUnit),
                        px(xMm + coords[2] * mmPerUnit), py(yTopMm + coords[3] * mmPerUnit),
                        px(xMm + coords[4] * mmPerUnit), py(yTopMm + coords[5] * mmPerUnit));
                case 'Q' -> {
                    // convert quadratic to cubic on the fly
                    throw new IllegalArgumentException("Q handled by parser as C");
                }
                case 'Z' -> cs.closePath();
                default -> throw new IllegalArgumentException("Unsupported op " + op);
            }
        });
        if (fill) {
            cs.fill();
        } else {
            cs.setLineWidth(linePt(strokeLineMm == null ? 0.4 : strokeLineMm));
            cs.stroke();
        }
    }

    private static void emitCurve(List<double[]> current, double x1, double y1,
                                  double x2, double y2, double x3, double y3) {
        // polyline approximation for the simplified path API
        double x0 = current.isEmpty() ? 0 : current.get(current.size() - 1)[0];
        double y0 = current.isEmpty() ? 0 : current.get(current.size() - 1)[1];
        for (int s = 1; s <= 8; s++) {
            double t = s / 8.0;
            double mt = 1 - t;
            double x = mt * mt * mt * x0 + 3 * mt * mt * t * x1 + 3 * mt * t * t * x2 + t * t * t * x3;
            double y = mt * mt * mt * y0 + 3 * mt * mt * t * y1 + 3 * mt * t * t * y2 + t * t * t * y3;
            current.add(new double[]{x, y});
        }
    }

    // ---------------------------------------------------------------- hatches

    /**
     * Fills a rectangle with a hatch/stripe pattern (used by the thermal B/W fallback for
     * coloured hazmat diamond areas). angleDeg: 0 = vertical stripes, 45 = diagonal.
     */
    public void hatchRect(double xMm, double yTopMm, double wMm, double hMm, double spacingMm,
                          double angleDeg, double lineMm, float gray) throws IOException {
        ensureOpen();
        cs.saveGraphicsState();
        // clip to rect
        double x = LabelUnits.snapMm(cellX + xMm, dpi);
        double yTop = LabelUnits.snapMm(cellY + yTopMm, dpi);
        double w = LabelUnits.snapMm(wMm, dpi);
        double h = LabelUnits.snapMm(hMm, dpi);
        cs.addRect((float) LabelUnits.mmToPt(x),
                (float) LabelUnits.mmToPt(pageHmm - (yTop + h)),
                (float) LabelUnits.mmToPt(w),
                (float) LabelUnits.mmToPt(h));
        cs.clip();
        cs.setStrokingColor(gray);
        cs.setLineWidth(linePt(lineMm));
        double diagonal = Math.hypot(w, h);
        double gap = Math.max(LabelUnits.snapMm(spacingMm, dpi), LabelUnits.dotMm(dpi));
        if (angleDeg == 0) {
            for (double sx = x; sx <= x + w + gap; sx += gap) {
                cs.moveTo((float) LabelUnits.mmToPt(sx), (float) LabelUnits.mmToPt(pageHmm - yTop));
                cs.lineTo((float) LabelUnits.mmToPt(sx), (float) LabelUnits.mmToPt(pageHmm - (yTop + h)));
            }
        } else if (angleDeg == 90) {
            for (double sy = yTop; sy <= yTop + h + gap; sy += gap) {
                cs.moveTo((float) LabelUnits.mmToPt(x), (float) LabelUnits.mmToPt(pageHmm - sy));
                cs.lineTo((float) LabelUnits.mmToPt(x + w), (float) LabelUnits.mmToPt(pageHmm - sy));
            }
        } else {
            double rad = Math.toRadians(angleDeg);
            double dx = Math.cos(rad);
            double dy = Math.sin(rad);
            double cxm = x + w / 2;
            double cym = yTop + h / 2;
            for (double o = -diagonal; o <= diagonal; o += gap) {
                double x1 = cxm + o * -dy - dx * diagonal / 2;
                double y1 = cym + o * dx - dy * diagonal / 2;
                double x2 = cxm + o * -dy + dx * diagonal / 2;
                double y2 = cym + o * dx + dy * diagonal / 2;
                cs.moveTo((float) LabelUnits.mmToPt(x1), (float) LabelUnits.mmToPt(pageHmm - y1));
                cs.lineTo((float) LabelUnits.mmToPt(x2), (float) LabelUnits.mmToPt(pageHmm - y2));
            }
        }
        cs.stroke();
        cs.restoreGraphicsState();
    }

    // ------------------------------------------------------------ finalization

    private void ensureOpen() {
        if (!pageOpen || cs == null) {
            throw new IllegalStateException("No page open — call beginLabelPage/beginSheetPage first");
        }
    }

    private void closePage() throws IOException {
        if (cs != null) {
            cs.close();
            cs = null;
        }
        pageOpen = false;
    }

    /**
     * Closes the open page, applies viewer preferences (PrintScaling = None, actual-size printing),
     * fixed document dates for deterministic output, and returns the PDF bytes.
     */
    public byte[] save() throws IOException {
        closePage();
        PDViewerPreferences prefs = document.getDocumentCatalog().getViewerPreferences();
        if (prefs == null) {
            prefs = new PDViewerPreferences(document.getDocumentCatalog().getCOSObject());
            document.getDocumentCatalog().setViewerPreferences(prefs);
        }
        prefs.setPrintScaling(PDViewerPreferences.PRINT_SCALING.None);
        PDDocumentInformation info = document.getDocumentInformation();
        info.setProducer("Inside Invoice");
        info.setCreator("Inside Invoice Labels");
        info.setCreationDate(FIXED_DATE);
        info.setModificationDate(FIXED_DATE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        closePage();
        document.close();
    }

    /** Minimal SVG path parser emitting raw operations with unscaled coordinates. */
    static final class PathParser {

        @FunctionalInterface
        interface Sink {
            void accept(char op, double[] coords) throws IOException;
        }

        private PathParser() {
        }

        static void parse(String d, Sink sink) throws IOException {
            String normalized = d.trim().replaceAll(",", " ").replaceAll("([MmLlHhVvCcSsQqTtAaZz])", " $1 ");
            String[] tokens = normalized.trim().split("\\s+");
            int i = 0;
            char cmd = 'M';
            double cx = 0;
            double cy = 0;
            double sx = 0;
            double sy = 0;
            double prevCtrlX = 0;
            double prevCtrlY = 0;
            char prevCmd = 0;
            try {
                while (i < tokens.length) {
                    String t = tokens[i];
                    if (t.isEmpty()) {
                        i++;
                        continue;
                    }
                    if (t.length() == 1 && Character.isLetter(t.charAt(0))) {
                        cmd = t.charAt(0);
                        i++;
                    } else if (Character.isLetter(t.charAt(0))) {
                        throw new IllegalArgumentException("Bad path token: " + t);
                    } else if (prevCmd == 'M') {
                        cmd = 'L';
                    } else if (prevCmd == 'm') {
                        cmd = 'l';
                    }
                    boolean rel = Character.isLowerCase(cmd);
                    char uc = Character.toUpperCase(cmd);
                    switch (uc) {
                        case 'M' -> {
                            double x = num(tokens[i++]);
                            double y = num(tokens[i++]);
                            cx = rel ? cx + x : x;
                            cy = rel ? cy + y : y;
                            sx = cx;
                            sy = cy;
                            sink.accept('M', new double[]{cx, cy});
                            prevCmd = cmd;
                        }
                        case 'L' -> {
                            double x = num(tokens[i++]);
                            double y = num(tokens[i++]);
                            cx = rel ? cx + x : x;
                            cy = rel ? cy + y : y;
                            sink.accept('L', new double[]{cx, cy});
                            prevCmd = cmd;
                        }
                        case 'H' -> {
                            double x = num(tokens[i++]);
                            cx = rel ? cx + x : x;
                            sink.accept('L', new double[]{cx, cy});
                            prevCmd = cmd;
                        }
                        case 'V' -> {
                            double y = num(tokens[i++]);
                            cy = rel ? cy + y : y;
                            sink.accept('L', new double[]{cx, cy});
                            prevCmd = cmd;
                        }
                        case 'C' -> {
                            double x1 = num(tokens[i++]);
                            double y1 = num(tokens[i++]);
                            double x2 = num(tokens[i++]);
                            double y2 = num(tokens[i++]);
                            double x = num(tokens[i++]);
                            double y = num(tokens[i++]);
                            double ax1 = rel ? cx + x1 : x1;
                            double ay1 = rel ? cy + y1 : y1;
                            double ax2 = rel ? cx + x2 : x2;
                            double ay2 = rel ? cy + y2 : y2;
                            cx = rel ? cx + x : x;
                            cy = rel ? cy + y : y;
                            sink.accept('C', new double[]{ax1, ay1, ax2, ay2, cx, cy});
                            prevCtrlX = ax2;
                            prevCtrlY = ay2;
                            prevCmd = cmd;
                        }
                        case 'S' -> {
                            double x2 = num(tokens[i++]);
                            double y2 = num(tokens[i++]);
                            double x = num(tokens[i++]);
                            double y = num(tokens[i++]);
                            double ax1 = 2 * cx - prevCtrlX;
                            double ay1 = 2 * cy - prevCtrlY;
                            double ax2 = rel ? cx + x2 : x2;
                            double ay2 = rel ? cy + y2 : y2;
                            cx = rel ? cx + x : x;
                            cy = rel ? cy + y : y;
                            sink.accept('C', new double[]{ax1, ay1, ax2, ay2, cx, cy});
                            prevCtrlX = ax2;
                            prevCtrlY = ay2;
                            prevCmd = cmd;
                        }
                        case 'Q' -> {
                            double qx = num(tokens[i++]);
                            double qy = num(tokens[i++]);
                            double x = num(tokens[i++]);
                            double y = num(tokens[i++]);
                            double aqx = rel ? cx + qx : qx;
                            double aqy = rel ? cy + qy : qy;
                            double ax1 = cx + 2.0 / 3.0 * (aqx - cx);
                            double ay1 = cy + 2.0 / 3.0 * (aqy - cy);
                            cx = rel ? cx + x : x;
                            cy = rel ? cy + y : y;
                            double ax2 = cx + 2.0 / 3.0 * (aqx - cx);
                            double ay2 = cy + 2.0 / 3.0 * (aqy - cy);
                            sink.accept('C', new double[]{ax1, ay1, ax2, ay2, cx, cy});
                            prevCtrlX = aqx;
                            prevCtrlY = aqy;
                            prevCmd = cmd;
                        }
                        case 'Z' -> {
                            sink.accept('Z', new double[0]);
                            cx = sx;
                            cy = sy;
                            prevCmd = cmd;
                        }
                        default -> throw new IllegalArgumentException("Unsupported SVG command: " + cmd);
                    }
                }
            } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
                throw new IllegalArgumentException("Malformed SVG path: " + d, e);
            }
        }

        private static double num(String s) {
            return Double.parseDouble(s);
        }
    }
}
