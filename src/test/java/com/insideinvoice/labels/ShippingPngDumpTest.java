package com.insideinvoice.labels;

import com.insideinvoice.labels.enums.LabelPreset;
import com.insideinvoice.labels.render.LabelAddress;
import com.insideinvoice.labels.render.LabelRenderer;
import com.insideinvoice.labels.render.ShippingLabelSpec;
import com.insideinvoice.labels.render.ShippingRenderers;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Dev helper: renders shipping label presets to PNG in /tmp for visual inspection. */
class ShippingPngDumpTest {

    private static ShippingLabelSpec.ShippingLabelSpecBuilder base() {
        return ShippingLabelSpec.builder()
                .preset(LabelPreset.STANDARD_CARRIER_4X6)
                .labelNumber("LBL-0001")
                .shipDate(LocalDate.of(2026, 10, 25))
                .printTimestamp(LocalDateTime.of(2026, 10, 25, 9, 30));
    }

    private static void dump(String name, ShippingLabelSpec spec) throws Exception {
        LabelRenderer r = ShippingRenderers.forPreset(spec.effectivePreset());
        byte[] pdf = r.render(spec);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            File out = new File("/tmp/label-dump");
            out.mkdirs();
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = renderer.renderImage(i, 2.5f);
                ImageIO.write(img, "png", new File(out, name + "-" + i + ".png"));
            }
        }
    }

    /** The screenshot scenario: blank addresses except country, no tracking. */
    @Test
    void dumpEmptyInternational() throws Exception {
        dump("std-empty", base()
                .shipFrom(LabelAddress.builder().country("India").build())
                .shipTo(LabelAddress.builder().country("India").build())
                .build());
    }

    /** Fully populated international shipment (US destination). */
    @Test
    void dumpFullInternational() throws Exception {
        dump("std-intl", base()
                .carrier("FedEx").serviceCode("PMD").serviceLevel("Ground")
                .trackingNumber("1234567890123")
                .weightKg("2.5").dimsCm("40x30x20")
                .billingType("PREPAID")
                .invoiceNo("INV-0042").poNumber("PO-7788")
                .thisWayUp(true).fragile(true).keepDry(true)
                .shipFrom(LabelAddress.builder()
                        .name("Asha Verma").company("Verma Exports")
                        .addressLines(new java.util.ArrayList<>(List.of(
                                "12 MG Road, Shivaji Nagar", "Landmark: Near Bus Stand")))
                        .city("Bengaluru").state("Karnataka").pincode("560001")
                        .country("India").phone("+91 9876543210").build())
                .shipTo(LabelAddress.builder()
                        .name("John Anderson").company("Anderson Retail LLC")
                        .addressLines(new java.util.ArrayList<>(List.of(
                                "44 Anna Salai", "Opposite City Mall", "Floor 2, Unit B")))
                        .city("Austin").state("Texas").pincode("78701")
                        .country("United States").phone("+1 512 555 0142").build())
                .build());
    }

    @Test
    void dumpPresets() throws Exception {
        LabelAddress from = LabelAddress.builder().name("Asha Verma").company("Verma Exports")
                .addressLines(new java.util.ArrayList<>(List.of("12 MG Road")))
                .city("Bengaluru").state("Karnataka").pincode("560001")
                .country("India").phone("+91 9876543210").build();
        LabelAddress to = LabelAddress.builder().name("John Anderson")
                .addressLines(new java.util.ArrayList<>(List.of("44 Anna Salai")))
                .city("Austin").state("Texas").pincode("78701")
                .country("United States").build();

        dump("simple", base().preset(LabelPreset.SIMPLE_ADDRESS)
                .shipFrom(from).shipTo(to).build());
        dump("return", base().preset(LabelPreset.RETURN_LABEL)
                .carrier("DHL").serviceCode("EXP").trackingNumber("JD014000000123456789")
                .shipFrom(from).shipTo(to).build());
        dump("fba", base().preset(LabelPreset.AMAZON_FBA_4X6)
                .carrier("Amazon Logistics").serviceCode("2D")
                .trackingNumber("TBA309876543210")
                .fba(ShippingLabelSpec.FbaFields.builder()
                        .shipmentId("FBA16X8K2").boxId("BOX-01")
                        .fromWarehouse("BLR-WH1").shipToFc("BLR7")
                        .productUnits(24).createdDate("04-OCT-2026").build())
                .shipFrom(from).shipTo(to).build());
        dump("gs1", base().preset(LabelPreset.GS1_SSCC_4X6)
                .carrier("DHL").serviceCode("EXP").trackingNumber("JD014000000123456789")
                .gs1(ShippingLabelSpec.Gs1Fields.builder()
                        .sscc("12345678901234501").gtin("0123456789012").count("10")
                        .batchLot("LOT-99").poNumber("PO-7788").postalCode("78701").build())
                .shipFrom(from).shipTo(to).build());
    }
}
