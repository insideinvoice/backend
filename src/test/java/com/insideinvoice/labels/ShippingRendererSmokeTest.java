package com.insideinvoice.labels;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.insideinvoice.labels.enums.LabelPreset;
import com.insideinvoice.labels.render.LabelAddress;
import com.insideinvoice.labels.render.ShippingLabelSpec;
import com.insideinvoice.labels.render.ShippingRenderers;
import com.insideinvoice.labels.render.LabelRenderer;
import com.insideinvoice.labels.util.CheckDigits;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 3 smoke: every shipping preset renders a valid PDF (page count, paper
 * size), the SSCC sequence increments per carton, and the hero barcode on the
 * standard label decodes back to the tracking number.
 */
class ShippingRendererSmokeTest {

    private static final String TRACKING = "1Z999AA10123456784";

    private static LabelAddress from() {
        return LabelAddress.builder().name("Rajesh Kumar").company("RS Hardware")
                .addressLines(new java.util.ArrayList<>(List.of(
                        "12 MG Road, Shivaji Nagar", "Landmark: Near Bus Stand")))
                .city("Bengaluru").state("KARNATAKA").pincode("560001")
                .country("IN").phone("9876543210").build();
    }

    private static LabelAddress to() {
        return LabelAddress.builder().name("Priya Sharma").company("Sharma Textiles Pvt Ltd")
                .addressLines(new java.util.ArrayList<>(List.of(
                        "44 Anna Salai", "Opposite City Mall", "Floor 2, Unit B")))
                .city("Chennai").state("TAMIL NADU").pincode("600002")
                .country("IN").phone("9123456780").build();
    }

    private static ShippingLabelSpec.ShippingLabelSpecBuilder base() {
        return ShippingLabelSpec.builder()
                .preset(LabelPreset.STANDARD_CARRIER_4X6)
                .labelNumber("LBL-0001")
                .shipFrom(from()).shipTo(to())
                .carrier("BlueDart").serviceCode("GND").serviceLevel("Ground")
                .trackingNumber(TRACKING)
                .shipDate(LocalDate.of(2026, 10, 4))
                .weightKg("2.5").dimsCm("40x30x20")
                .billingType("PREPAID")
                .invoiceNo("INV-0042").poNumber("PO-7788")
                .thisWayUp(true).fragile(true).keepDry(true)
                .printTimestamp(LocalDateTime.of(2026, 10, 4, 9, 30));
    }

    private static int pageCount(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    private static PDRectangle mediaBox(byte[] pdf, int page) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getPage(page).getMediaBox();
        }
    }

    // ---------------------------------------------------------------- pages

    @Test
    void standardCarrierProducesOnePagePerCartonAt4x6() throws Exception {
        byte[] pdf = ShippingRenderers.forPreset(LabelPreset.STANDARD_CARRIER_4X6)
                .render(base().cartonCount(3).build());
        assertThat(pageCount(pdf)).isEqualTo(3);
        PDRectangle box = mediaBox(pdf, 0);
        // 4x6 in = 288 x 432 pt (label pages snap to the 203-DPI dot grid)
        assertThat(box.getWidth()).isBetween(287.9f, 288.1f);
        assertThat(box.getHeight()).isBetween(431.9f, 432.1f);
    }

    @Test
    void a4SheetKeepsExactPaperSizeAndPacksCells() throws Exception {
        byte[] pdf = ShippingRenderers.forPreset(LabelPreset.STANDARD_CARRIER_4X6)
                .render(base().sizeKey("a4-2up").cartonCount(3).build());
        assertThat(pageCount(pdf)).isEqualTo(2); // 2-up cells -> 3 cartons = 2 sheets
        PDRectangle box = mediaBox(pdf, 0);
        // sheet pages keep exact A4 (595.28 x 841.89 pt), no dot snapping
        assertThat(box.getWidth()).isBetween(595f, 596f);
        assertThat(box.getHeight()).isBetween(841f, 843f);
    }

    @Test
    void amazonFbaRenders() throws Exception {
        byte[] pdf = ShippingRenderers.forPreset(LabelPreset.AMAZON_FBA_4X6)
                .render(base().preset(LabelPreset.AMAZON_FBA_4X6)
                        .fba(ShippingLabelSpec.FbaFields.builder()
                                .shipmentId("FBA16X8K2").boxId("BOX-01")
                                .fromWarehouse("BLR-WH1").shipToFc("BLR7")
                                .productUnits(24).createdDate("04-OCT-2026")
                                .build())
                        .build());
        assertThat(pageCount(pdf)).isEqualTo(1);
    }

    @Test
    void simpleAddressRendersOnSmallThermalSize() throws Exception {
        byte[] pdf = ShippingRenderers.forPreset(LabelPreset.SIMPLE_ADDRESS)
                .render(base().preset(LabelPreset.SIMPLE_ADDRESS).sizeKey("100x100mm").build());
        assertThat(pageCount(pdf)).isEqualTo(1);
        PDRectangle box = mediaBox(pdf, 0);
        assertThat(box.getWidth()).isBetween(283f, 284f); // 100 mm
    }

    @Test
    void returnLabelSwapsAddresses() throws Exception {
        byte[] pdf = ShippingRenderers.forPreset(LabelPreset.RETURN_LABEL)
                .render(base().preset(LabelPreset.RETURN_LABEL).build());
        assertThat(pageCount(pdf)).isEqualTo(1);
        assertThat(pdf).isNotEmpty();
    }

    @Test
    void fnskuSheetProducesCopiesPerItem() throws Exception {
        byte[] pdf = ShippingRenderers.forPreset(LabelPreset.FNSKU_30UP)
                .render(ShippingLabelSpec.builder()
                        .preset(LabelPreset.FNSKU_30UP)
                        .fnskuCopies(2)
                        .fnskuItems(new java.util.ArrayList<>(List.of(
                                ShippingLabelSpec.FnskuItem.builder()
                                        .title("Steel Ball Bearing 6203 ZZ")
                                        .fnsku("X0K1J2").condition("New").build(),
                                ShippingLabelSpec.FnskuItem.builder()
                                        .title("V-Belt A-Section 1050")
                                        .fnsku("B7Q3M9").condition("New").build())))
                        .build());
        assertThat(pageCount(pdf)).isEqualTo(1); // 2 items x 2 copies = 4 cells on 1 sheet
        PDRectangle box = mediaBox(pdf, 0);
        assertThat(box.getWidth()).isBetween(611f, 613f); // letter
    }

    @Test
    void fnskuWithoutItemsFailsFast() {
        LabelRenderer r = ShippingRenderers.forPreset(LabelPreset.FNSKU_30UP);
        assertThatThrownBy(() -> r.render(ShippingLabelSpec.builder()
                .preset(LabelPreset.FNSKU_30UP).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one product");
    }

    @Test
    void gs1PresetIncrementsSsccSerialPerCarton() throws Exception {
        ShippingLabelSpec spec = base().preset(LabelPreset.GS1_SSCC_4X6)
                .cartonCount(3)
                .gs1(ShippingLabelSpec.Gs1Fields.builder()
                        .sscc("12345678901234501") // 17 digits, serial ...501
                        .gtin("0123456789012").count("10")
                        .batchLot("LOT-99").poNumber("PO-7788")
                        .postalCode("600002").build())
                .build();

        byte[] pdf = ShippingRenderers.forPreset(LabelPreset.GS1_SSCC_4X6).render(spec);
        assertThat(pageCount(pdf)).isEqualTo(3);

        String s1 = Gs1SsccRendererSscc.serial(0);
        String s2 = Gs1SsccRendererSscc.serial(1);
        String s3 = Gs1SsccRendererSscc.serial(2);
        assertThat(s1).hasSize(18).isNotEqualTo(s2).isNotEqualTo(s3);
        // serial reference lives in digits 12..17 and advances by 1 per carton
        assertThat(s1.substring(11, 17)).isEqualTo("234501");
        assertThat(s2.substring(11, 17)).isEqualTo("234502");
        assertThat(s3.substring(11, 17)).isEqualTo("234503");
        // check digits recomputed for every serial
        assertThat(CheckDigits.isValidSscc18(s1)).isTrue();
        assertThat(CheckDigits.isValidSscc18(s2)).isTrue();
        assertThat(CheckDigits.isValidSscc18(s3)).isTrue();
        assertThat(s1).isEqualTo(CheckDigits.sscc18("12345678901234501"));
        assertThat(s2).isEqualTo(CheckDigits.sscc18(
                CheckDigits.withSerialDelta("12345678901234501", 1)));
    }

    @Test
    void gs1WithoutSsccFailsFast() {
        LabelRenderer r = ShippingRenderers.forPreset(LabelPreset.GS1_SSCC_4X6);
        assertThatThrownBy(() -> r.render(base().preset(LabelPreset.GS1_SSCC_4X6).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SSCC");
    }

    @Test
    void heroTrackingBarcodeDecodesBackFromRenderedLabel() throws Exception {
        byte[] pdf = ShippingRenderers.forPreset(LabelPreset.STANDARD_CARRIER_4X6)
                .render(base().build());
        assertThat(decodeFirst(pdf, 0)).isEqualTo(TRACKING);
    }

    // --------------------------------------------------------------- decode

    /** Mirrors BarcodeRoundTripTest: full page, then bbox crop fallback. */
    private static String decodeFirst(byte[] pdf, int pageIndex) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            org.apache.pdfbox.rendering.PDFRenderer renderer =
                    new org.apache.pdfbox.rendering.PDFRenderer(doc);
            BufferedImage image = renderer.renderImage(pageIndex, 300f / 72f);
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            try {
                return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                        new BufferedImageLuminanceSource(image))), hints).getText();
            } catch (Exception fullPage) {
                int minX = image.getWidth(), minY = image.getHeight(), maxX = 0, maxY = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if ((image.getRGB(x, y) & 0xFF) < 128) {
                            minX = Math.min(minX, x);
                            maxX = Math.max(maxX, x);
                            minY = Math.min(minY, y);
                            maxY = Math.max(maxY, y);
                        }
                    }
                }
                int module = Math.max(6, Math.max(maxX - minX + 1, maxY - minY + 1) / 24);
                int margin = 4 * module;
                BufferedImage crop = image.getSubimage(
                        Math.max(0, minX - margin), Math.max(0, minY - margin),
                        Math.min(image.getWidth() - 1, maxX + margin)
                                - Math.max(0, minX - margin) + 1,
                        Math.min(image.getHeight() - 1, maxY + margin)
                                - Math.max(0, minY - margin) + 1);
                return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                        new BufferedImageLuminanceSource(crop))), hints).getText();
            }
        }
    }

    /** Access the Gs1 renderer's sequential SSCC helper without exposing it elsewhere. */
    private static final class Gs1SsccRendererSscc {
        static String serial(int index) {
            ShippingLabelSpec spec = ShippingLabelSpec.builder()
                    .gs1(ShippingLabelSpec.Gs1Fields.builder()
                            .sscc("12345678901234501").build())
                    .build();
            return com.insideinvoice.labels.render.Gs1SsccRenderer.ssccFor(spec, index + 1);
        }
    }
}
