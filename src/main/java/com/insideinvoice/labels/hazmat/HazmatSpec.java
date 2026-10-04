package com.insideinvoice.labels.hazmat;

import com.insideinvoice.labels.enums.ColorMode;
import com.insideinvoice.labels.enums.HazmatLabelType;
import com.insideinvoice.labels.enums.TransportMode;
import com.insideinvoice.labels.render.LabelAddress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure render-time input for hazmat labels. The REST layer maps the persisted
 * {@code HazmatLabel} entity onto this; renderers never touch the database.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HazmatSpec {

    private String labelNumber;
    @Builder.Default
    private HazmatLabelType labelType = HazmatLabelType.CLASS_DIAMOND;
    /** Size key from LabelSizes; null = 4x4 in. */
    private String labelSize;
    @Builder.Default
    private ColorMode colorMode = ColorMode.COLOR;
    @Builder.Default
    private TransportMode transportMode = TransportMode.ROAD;

    private String unNumber;
    private String properShippingName;
    private String technicalName;
    /** Raw class key: 1, 2.1, 2.2, 2.3, 3, 4.1 ... 9 (also 9A, LQ, EQ marks). */
    private String hazardClass;
    /** Division for class 1 (1.1-1.6). */
    private String division;
    /** Compatibility group letter A-S for class 1 (e.g. 1.4G). */
    private String compatGroup;
    private String packingGroup;
    /** JSON array string, e.g. ["6.1"]. */
    private String subsidiaryRisks;
    private String netQuantity;
    private Integer packageCount;

    private LabelAddress consignor;
    private LabelAddress consignee;
    private String emergencyPhone;
    private String graCode;
    private String ergGuide;
    private BigDecimal lithiumWh;
    private String notes;
    @Builder.Default
    private Boolean overpack = false;
    @Builder.Default
    private Boolean marinePollutant = false;

    /** Radiation: I, II or III (roman numeral shown in the IATA boxes). */
    private String radiationCategory;
    /** Limited quantity mark: "Y" (air) or null/other. */
    private String limitedQuantityCode;
    /** Lithium UN list, e.g. "UN3480 / UN3481". */
    private String lithiumUnList;
    /** IATA packing instruction for lithium (965-970). */
    private String lithiumPackingInstruction;

    /** Fixed timestamp for deterministic output when provided. */
    private LocalDateTime printTimestamp;
    /** Footer identity; null = "Inside Invoice". */
    private String generatedBy;

    public HazmatLabelType effectiveType() {
        return labelType == null ? HazmatLabelType.CLASS_DIAMOND : labelType;
    }

    public ColorMode effectiveColorMode() {
        return colorMode == null ? ColorMode.COLOR : colorMode;
    }

    public boolean isThermal() {
        return effectiveColorMode() == ColorMode.THERMAL_BW;
    }

    public String size() {
        if (labelSize != null && !labelSize.isBlank()) {
            return labelSize;
        }
        return switch (effectiveType()) {
            case LITHIUM, LIMITED_QTY, ENV_HAZARD, ORIENTATION, CAO, EXCEPTED_QTY, OVERPACK
                    -> "100x100mm";
            case PLACARD -> "a4";
            default -> "hazmat-4x4";
        };
    }

    public String footerIdentity() {
        return generatedBy == null || generatedBy.isBlank() ? "insideinvoice.com" : generatedBy;
    }

    public List<String> subsidiaryList() {
        List<String> out = new ArrayList<>();
        if (subsidiaryRisks != null && !subsidiaryRisks.isBlank()) {
            for (String part : subsidiaryRisks.replaceAll("[\\[\\]\"]", "").split(",")) {
                String p = part.trim();
                if (!p.isEmpty()) {
                    out.add(p);
                }
            }
        }
        return out;
    }
}
