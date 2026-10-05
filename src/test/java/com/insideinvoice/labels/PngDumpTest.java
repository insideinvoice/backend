package com.insideinvoice.labels;

import com.insideinvoice.labels.enums.ColorMode;
import com.insideinvoice.labels.enums.HazmatLabelType;
import com.insideinvoice.labels.enums.TransportMode;
import com.insideinvoice.labels.hazmat.HazmatRenderers;
import com.insideinvoice.labels.hazmat.HazmatSpec;
import com.insideinvoice.labels.render.LabelAddress;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Dev helper: renders hazmat labels to PNG in /tmp for visual inspection. */
class PngDumpTest {

    private static HazmatSpec.HazmatSpecBuilder base() {
        return HazmatSpec.builder()
                .labelNumber("HZ-0001")
                .unNumber("UN3480")
                .properShippingName("Lithium ion batteries")
                .hazardClass("9")
                .packingGroup("")
                .netQuantity("1")
                .packageCount(1)
                .transportMode(TransportMode.ROAD)
                .emergencyPhone("+1-760-476-3961")
                .graCode("333146")
                .ergGuide("")
                .consignor(LabelAddress.builder().name("RS Hardware")
                        .addressLines(new ArrayList<>(List.of("12 MG Road")))
                        .city("Bengaluru").pincode("560001").build())
                .printTimestamp(LocalDateTime.of(2026, 10, 6, 9, 30));
    }

    private static void dump(String name, HazmatSpec spec) throws Exception {
        byte[] pdf = HazmatRenderers.forSpec(spec).render(spec);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            BufferedImage img = new PDFRenderer(doc).renderImage(0, 2.5f);
            File out = new File("/tmp/label-dump");
            out.mkdirs();
            ImageIO.write(img, "png", new File(out, name + ".png"));
        }
    }

    @Test
    void dumpAll() throws Exception {
        dump("lithium", base().labelType(HazmatLabelType.LITHIUM).labelSize("hazmat-4x4").build());
        dump("lithium-color", base().labelType(HazmatLabelType.LITHIUM)
                .colorMode(ColorMode.COLOR).labelSize("hazmat-4x4").build());
        dump("class3", base().labelType(HazmatLabelType.CLASS_DIAMOND).hazardClass("3")
                .packingGroup("II").labelSize("hazmat-4x4").build());
        dump("class9", base().labelType(HazmatLabelType.CLASS_DIAMOND).hazardClass("9")
                .labelSize("hazmat-4x4").build());
        dump("class8", base().labelType(HazmatLabelType.CLASS_DIAMOND).hazardClass("8")
                .labelSize("hazmat-4x4").build());
        dump("class7", base().labelType(HazmatLabelType.CLASS_DIAMOND).hazardClass("7")
                .radiationCategory("II").labelSize("hazmat-4x4").build());
        dump("class52", base().labelType(HazmatLabelType.CLASS_DIAMOND).hazardClass("5.2")
                .labelSize("hazmat-4x4").build());
        dump("class41", base().labelType(HazmatLabelType.CLASS_DIAMOND).hazardClass("4.1")
                .labelSize("hazmat-4x4").build());
        dump("class42", base().labelType(HazmatLabelType.CLASS_DIAMOND).hazardClass("4.2")
                .labelSize("hazmat-4x4").build());
        dump("class21", base().labelType(HazmatLabelType.CLASS_DIAMOND).hazardClass("2.1")
                .labelSize("hazmat-4x4").build());
        dump("class22", base().labelType(HazmatLabelType.CLASS_DIAMOND).hazardClass("2.2")
                .labelSize("hazmat-4x4").build());
        dump("class62", base().labelType(HazmatLabelType.CLASS_DIAMOND).hazardClass("6.2")
                .labelSize("hazmat-4x4").build());
        dump("class1", base().labelType(HazmatLabelType.CLASS_DIAMOND).hazardClass("1")
                .division("1.4").compatGroup("G").labelSize("hazmat-4x4").build());
        dump("limited", base().labelType(HazmatLabelType.LIMITED_QTY)
                .transportMode(TransportMode.AIR).labelSize("hazmat-4x4").build());
        dump("excepted", base().labelType(HazmatLabelType.EXCEPTED_QTY)
                .hazardClass("3").labelSize("hazmat-4x4").build());
        dump("env", base().labelType(HazmatLabelType.ENV_HAZARD).labelSize("hazmat-4x4").build());
        dump("orientation", base().labelType(HazmatLabelType.ORIENTATION).labelSize("hazmat-4x4").build());
        dump("cao", base().labelType(HazmatLabelType.CAO).labelSize("hazmat-4x4").build());
        dump("overpack", base().labelType(HazmatLabelType.OVERPACK).labelSize("hazmat-4x4").build());
        dump("lithium-wh", base().labelType(HazmatLabelType.LITHIUM)
                .lithiumWh(new BigDecimal("100")).labelSize("hazmat-4x4").build());
    }
}
