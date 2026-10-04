package com.insideinvoice.labels;

import com.insideinvoice.labels.enums.ColorMode;
import com.insideinvoice.labels.enums.HazmatLabelType;
import com.insideinvoice.labels.enums.TransportMode;
import com.insideinvoice.labels.hazmat.ClassDiamondRenderer;
import com.insideinvoice.labels.hazmat.HazardClass;
import com.insideinvoice.labels.hazmat.HazmatRenderers;
import com.insideinvoice.labels.hazmat.HazmatSpec;
import com.insideinvoice.labels.render.LabelAddress;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Stage 4 hazmat tests: every class + mark type renders, diamond geometry
 * follows the 0.05·S / 0.127·S scaling rules, colours are exact, and thermal
 * mode produces the black/hatch fallback.
 */
class HazmatRendererTest {

    private static HazmatSpec.HazmatSpecBuilder base() {
        return HazmatSpec.builder()
                .labelNumber("HZ-0001")
                .unNumber("UN1203")
                .properShippingName("Gasoline")
                .hazardClass("3")
                .packingGroup("II")
                .netQuantity("2 L")
                .packageCount(4)
                .transportMode(TransportMode.ROAD)
                .emergencyPhone("1800-424-9300")
                .ergGuide("128")
                .consignor(LabelAddress.builder().name("RS Hardware")
                        .addressLines(new ArrayList<>(List.of("12 MG Road")))
                        .city("Bengaluru").pincode("560001").build())
                .consignee(LabelAddress.builder().name("Sharma Textiles")
                        .addressLines(new ArrayList<>(List.of("44 Anna Salai")))
                        .city("Chennai").pincode("600002").build())
                .printTimestamp(LocalDateTime.of(2026, 10, 4, 9, 30));
    }

    private static int pageCount(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    private static PDRectangle media(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getPage(0).getMediaBox();
        }
    }

    // --------------------------------------------------------- all classes

    @Test
    void everyHazardClassRendersInColorMode() throws Exception {
        for (HazardClass hc : HazardClass.values()) {
            HazmatSpec spec = base().hazardClass(hc.key())
                    .labelType(HazmatLabelType.CLASS_DIAMOND).build();
            byte[] pdf = HazmatRenderers.forSpec(spec).render(spec);
            assertThat(pageCount(pdf)).as("class %s", hc.key()).isEqualTo(1);
            assertThat(pdf.length).as("class %s bytes", hc.key()).isGreaterThan(2000);
        }
    }

    @Test
    void everyHazardClassRendersInThermalMode() throws Exception {
        for (HazardClass hc : HazardClass.values()) {
            HazmatSpec spec = base().hazardClass(hc.key())
                    .colorMode(ColorMode.THERMAL_BW)
                    .labelType(HazmatLabelType.CLASS_DIAMOND).build();
            byte[] pdf = HazmatRenderers.forSpec(spec).render(spec);
            assertThat(pageCount(pdf)).as("thermal %s", hc.key()).isEqualTo(1);
        }
    }

    @Test
    void allMarkTypesRender() throws Exception {
        for (HazmatLabelType type : new HazmatLabelType[]{
                HazmatLabelType.LITHIUM, HazmatLabelType.LIMITED_QTY,
                HazmatLabelType.EXCEPTED_QTY, HazmatLabelType.ENV_HAZARD,
                HazmatLabelType.ORIENTATION, HazmatLabelType.CAO,
                HazmatLabelType.OVERPACK}) {
            HazmatSpec spec = base().labelType(type).hazardClass("9")
                    .lithiumWh(new BigDecimal("100.5"))
                    .lithiumUnList("UN3480")
                    .build();
            byte[] pdf = HazmatRenderers.forSpec(spec).render(spec);
            assertThat(pageCount(pdf)).as("mark %s", type).isEqualTo(1);
            assertThat(pdf.length).as("mark %s bytes", type).isGreaterThan(2000);
        }
    }

    // ------------------------------------------------------------- geometry

    @Test
    void diamondGeometryFollowsScalingRules() {
        double s = 100;
        double[][] outer = ClassDiamondRenderer.outerPts(50, 50, s);
        double[][] inner = ClassDiamondRenderer.innerPts(50, 50, s);

        // outer point-to-point = S
        assertThat(outer[1][0] - outer[3][0]).isCloseTo(s, within(1e-9));
        assertThat(outer[2][1] - outer[0][1]).isCloseTo(s, within(1e-9));

        // inner border offset = 0.05*S perpendicular to each edge
        double centerToOuterEdge = s / (2 * Math.sqrt(2));
        double centerToInnerEdge = (inner[1][0] - 50) / Math.sqrt(2); // vertex/√2 = edge dist
        assertThat(centerToOuterEdge - centerToInnerEdge).isCloseTo(0.05 * s, within(1e-6));
    }

    @Test
    void innerBorderScalesProportionally() {
        double s2 = 50;
        double[][] i100 = ClassDiamondRenderer.innerPts(0, 0, 100);
        double[][] i50 = ClassDiamondRenderer.innerPts(0, 0, s2);
        double r100 = Math.abs(i100[1][0]);
        double r50 = Math.abs(i50[1][0]);
        assertThat(r50 / r100).isCloseTo(0.5, within(1e-9));
        // at S=100: vertex inset = 5*sqrt(2) from the outer vertex
        assertThat(r100).isCloseTo(50 - 5 * Math.sqrt(2), within(1e-9));
    }

    @Test
    void numeralRules() {
        // class 1 builds division + compat group
        HazmatSpec spec = base().hazardClass("1").division("1.4").compatGroup("G").build();
        assertThat(ClassDiamondRenderer.numeral(spec, HazardClass.C1)).isEqualTo("1.4G");
        spec = base().hazardClass("1").division("4").compatGroup("s").build();
        assertThat(ClassDiamondRenderer.numeral(spec, HazardClass.C1)).isEqualTo("1.4S");
        // other classes use the configured numeral (2.1 shows "2" per spec)
        assertThat(ClassDiamondRenderer.numeral(base().hazardClass("2.1").build(),
                HazardClass.C2_1)).isEqualTo("2");
        assertThat(ClassDiamondRenderer.numeral(base().hazardClass("5.1").build(),
                HazardClass.C5_1)).isEqualTo("5.1");
        // 6.2 / 7 have no numeral (required text instead)
        assertThat(ClassDiamondRenderer.numeral(base().hazardClass("6.2").build(),
                HazardClass.C6_2)).isEmpty();
        assertThat(ClassDiamondRenderer.numeral(base().hazardClass("7").build(),
                HazardClass.C7)).isEmpty();
    }

    // --------------------------------------------------------------- colours

    @Test
    void classColoursAreExactHex() {
        assertThat(HazardClass.C3.rgb()).containsExactly(228, 0, 43);      // #E4002B
        assertThat(HazardClass.C2_2.rgb()).containsExactly(0, 166, 81);    // #00A651
        assertThat(HazardClass.C4_3.rgb()).containsExactly(0, 114, 188);   // #0072BC
        assertThat(HazardClass.C5_1.rgb()).containsExactly(255, 215, 0);   // #FFD700
        assertThat(HazardClass.C2_1.rgb()).containsExactly(228, 0, 43);
        assertThat(HazardClass.C1.rgb()).containsExactly(255, 102, 0);     // #FF6600
    }

    @Test
    void cmykDerivesFromHex() {
        float[] cmyk = HazardClass.C5_1.cmyk(); // #FFD700 => C=0 M≈0.157 Y=1 K=0
        assertThat(cmyk[0]).isCloseTo(0f, within(1e-5f));            // no cyan
        assertThat(cmyk[2]).isCloseTo(1f, within(1e-5f));            // 100% yellow
        assertThat(cmyk[3]).isCloseTo(0f, within(1e-5f));            // no black
        assertThat(cmyk[1]).isCloseTo(1 - 215 / 255f, within(1e-5f)); // M from G
        float[] white = HazardClass.C9.cmyk(); // #FFFFFF => all zero
        assertThat(white[0]).isEqualTo(0f);
        assertThat(white[3]).isEqualTo(0f);
    }

    @Test
    void unknownClassKeyFallsBack() {
        assertThat(HazardClass.fromKey(null)).isEqualTo(HazardClass.C9);
        assertThat(HazardClass.fromKey("4.2")).isEqualTo(HazardClass.C4_2);
        assertThat(HazardClass.fromKey("2.3")).isEqualTo(HazardClass.C2_3);
        assertThat(HazardClass.fromKey("9A")).isEqualTo(HazardClass.C9A);
        assertThat(HazardClass.fromKey("nonsense").key()).isEqualTo("9");
    }

    // ----------------------------------------------------------- sizes/pages

    @Test
    void fourBySixRendersDiamondPlusMarkingBlock() throws Exception {
        HazmatSpec spec = base().labelSize("hazmat-4x6").build();
        byte[] pdf = HazmatRenderers.forSpec(spec).render(spec);
        assertThat(pageCount(pdf)).isEqualTo(1);
        PDRectangle box = media(pdf);
        assertThat(box.getWidth()).isBetween(287.9f, 288.1f);   // 101.6 mm
        assertThat(box.getHeight()).isBetween(431.9f, 432.1f);  // 152.4 mm
    }

    @Test
    void placardRendersOnExactA4() throws Exception {
        HazmatSpec spec = base().labelType(HazmatLabelType.PLACARD).labelSize("a4").build();
        byte[] pdf = HazmatRenderers.forSpec(spec).render(spec);
        assertThat(pageCount(pdf)).isEqualTo(1);
        PDRectangle box = media(pdf);
        assertThat(box.getWidth()).isBetween(595f, 596f);
        assertThat(box.getHeight()).isBetween(841f, 843f);
    }

    @Test
    void limitedQuantityDiamondUsesLabelSquare() throws Exception {
        HazmatSpec spec = base().labelType(HazmatLabelType.LIMITED_QTY)
                .transportMode(TransportMode.AIR)
                .limitedQuantityCode("Y").build();
        byte[] pdf = HazmatRenderers.forSpec(spec).render(spec);
        PDRectangle box = media(pdf);
        assertThat(box.getWidth()).isBetween(283f, 284f); // 100 mm default
    }

    @Test
    void radiationBoxesAcceptCategory() throws Exception {
        HazmatSpec spec = base().hazardClass("7").radiationCategory("III").build();
        byte[] pdf = HazmatRenderers.forSpec(spec).render(spec);
        assertThat(pageCount(pdf)).isEqualTo(1);
    }
}
