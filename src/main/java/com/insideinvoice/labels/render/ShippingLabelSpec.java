package com.insideinvoice.labels.render;

import com.insideinvoice.labels.enums.LabelPreset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure render-time input for shipping label presets. The REST layer maps the
 * persisted entity onto this; renderers never touch the database.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ShippingLabelSpec {

    private String labelNumber;
    private LabelPreset preset;
    /** Size key from {@link LabelSizes}; null = 4x6in default. */
    private String sizeKey;
    /** null = 203 DPI default. */
    private Integer dpi;
    /** Thermal B/W output (pure black); null = true (thermal is the default target). */
    private Boolean thermalMode;
    /** Outer 0.25 mm keyline for laser printing; default off. */
    @Builder.Default
    private Boolean printBorder = false;

    private LabelAddress shipFrom;
    private LabelAddress shipTo;

    private String carrier;
    private String serviceLevel;
    /** Short service indicator for the inverse box, e.g. "GND", "ND", "2D". */
    private String serviceCode;
    private String trackingNumber;
    private LocalDate shipDate;
    private String weightKg;
    /** e.g. "40x30x20". */
    private String dimsCm;

    /** Number of cartons -> that many labels ("CARTON i OF N"); null/0 = 1. */
    private Integer cartonCount;

    /** PREPAID | COD | THIRD_PARTY. */
    private String billingType;
    private String codAmount;

    private String invoiceNo;
    private String poNumber;
    private String ref1;
    private String ref2;
    private String notes;

    @Builder.Default
    private Boolean thisWayUp = false;
    @Builder.Default
    private Boolean fragile = false;
    @Builder.Default
    private Boolean keepDry = false;
    @Builder.Default
    private Boolean doNotStack = false;
    @Builder.Default
    private Boolean handleWithCare = false;

    private FbaFields fba;
    private Gs1Fields gs1;

    /** Footer identity; null = "Inside Invoice". */
    private String generatedBy;
    /** Fixed timestamp for deterministic output when provided. */
    private LocalDateTime printTimestamp;
    /** Copies per FNSKU item on the 30-up sheet; null = 1. */
    private Integer fnskuCopies;
    @Builder.Default
    private List<FnskuItem> fnskuItems = new ArrayList<>();

    public int cartons() {
        return cartonCount == null || cartonCount < 1 ? 1 : Math.min(cartonCount, 999);
    }

    public int effectiveDpi() {
        return dpi == null || dpi != 300 ? 203 : 300;
    }

    public boolean isThermal() {
        return thermalMode == null || thermalMode;
    }

    public boolean isBorder() {
        return Boolean.TRUE.equals(printBorder);
    }

    public LabelPreset effectivePreset() {
        return preset == null ? LabelPreset.STANDARD_CARRIER_4X6 : preset;
    }

    public String size() {
        return sizeKey == null || sizeKey.isBlank() ? LabelSizes.DEFAULT_SHIPPING : sizeKey;
    }

    public String footerIdentity() {
        return generatedBy == null || generatedBy.isBlank() ? "Inside Invoice" : generatedBy;
    }

    /** Short service indicator for the inverse box; never blank. */
    public String serviceOrDefault() {
        if (serviceCode != null && !serviceCode.isBlank()) {
            return serviceCode.trim();
        }
        if (serviceLevel != null && !serviceLevel.isBlank()) {
            return serviceLevel.trim();
        }
        if (carrier != null && !carrier.isBlank()) {
            return carrier.trim();
        }
        return "SVC";
    }

    /** Tracking (or label number) used for the hero Code 128; never blank. */
    public String barcodePayload() {
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            return trackingNumber.trim();
        }
        if (labelNumber != null && !labelNumber.isBlank()) {
            return labelNumber.trim();
        }
        return "NO-TRACKING";
    }

    /**
     * Tracking for carton {@code index} (1-based). Multiple comma/semicolon
     * separated tracking numbers map to successive cartons; otherwise the same
     * value is reused for every label.
     */
    public String trackingForIndex(int index) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return barcodePayload();
        }
        String[] parts = trackingNumber.split("[,;]");
        if (parts.length >= index && index >= 1) {
            return parts[index - 1].trim();
        }
        return trackingNumber.trim();
    }

    // -------------------------------------------------------------- FBA / GS1

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FbaFields {
        private String shipmentId;
        private String boxId;
        private String fromWarehouse;
        private String shipToFc;
        private Integer productUnits;
        private String createdDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Gs1Fields {
        /** 17-digit SSCC (with or without check digit); serial incremented per carton. */
        private String sscc;
        /** GTIN-14 without check digit or full; validated via CheckDigits. */
        private String gtin;
        private String count;
        private String batchLot;
        /** yyMMdd expiry (AI 17) and production date (AI 11). */
        private String expiryDate;
        private String productionDate;
        private String poNumber;
        private String postalCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FnskuItem {
        private String title;
        private String fnsku;
        private String condition;
    }
}
