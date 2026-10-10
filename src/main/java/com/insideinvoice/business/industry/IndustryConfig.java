package com.insideinvoice.business.industry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, resolved configuration for one industry profile.
 *
 * <p>Held in application configuration (a static registry), not in a database
 * table: there is exactly one row per industry and no tenant-specific override
 * is stored. A business only stores which profile it selected.</p>
 *
 * <p>Semantics:</p>
 * <ul>
 *   <li>{@code hiddenFields} — not shown by default for this industry. Values
 *       already persisted on an invoice are never deleted because of this.</li>
 *   <li>{@code labels} — industry wording for existing form fields. Template
 *       typography and layout are untouched.</li>
 *   <li>{@code documents} — whether a workflow is offered by default. It is a
 *       usability default, never a legal determination, and the underlying
 *       routes stay reachable for transaction-specific or historical needs.</li>
 * </ul>
 */
public record IndustryConfig(
        Industry industry,
        Set<String> hiddenFields,
        Map<String, String> labels,
        Map<String, String> placeholders,
        DocumentAccess documents) {

    public IndustryConfig {
        Objects.requireNonNull(industry, "industry");
        hiddenFields = hiddenFields == null ? Set.of() : Set.copyOf(hiddenFields);
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
        documents = documents == null ? DocumentAccess.allEnabled() : documents;
    }

    public boolean isFieldVisible(String field) {
        return !hiddenFields.contains(field);
    }

    public boolean isDocumentEnabled(DocumentType type) {
        return switch (type) {
            case DELIVERY_CHALLAN -> documents.deliveryChallan();
            case SHIPPING_LABEL -> documents.shippingLabel();
            case HAZMAT_LABEL -> documents.hazmatLabel();
        };
    }

    /** Every field minus the hidden ones, in catalogue order. */
    public List<String> visibleFields() {
        List<String> visible = new java.util.ArrayList<>(IndustryField.ALL.size());
        for (String field : IndustryField.ALL) {
            if (!hiddenFields.contains(field)) {
                visible.add(field);
            }
        }
        return List.copyOf(visible);
    }

    public Set<String> requiredFields() {
        return IndustryField.BASE_REQUIRED;
    }

    /** A field is optional when it is visible but not in the required set. */
    public List<String> optionalFields() {
        Set<String> required = IndustryField.BASE_REQUIRED;
        return visibleFields().stream().filter(f -> !required.contains(f)).toList();
    }

    /**
     * Builds a config, silently dropping anything the catalogue does not allow:
     * unknown field ids and attempts to hide protected fields are discarded so a
     * misconfigured profile can never remove GST/HSN/core fields.
     */
    public static IndustryConfig of(Industry industry,
                                    Set<String> hidden,
                                    Map<String, String> labels,
                                    Map<String, String> placeholders,
                                    DocumentAccess documents) {
        Set<String> safeHidden = new LinkedHashSet<>();
        if (hidden != null) {
            for (String field : hidden) {
                if (IndustryField.CONFIGURABLE_FIELDS.contains(field)) {
                    safeHidden.add(field);
                }
            }
        }
        return new IndustryConfig(industry, safeHidden, labels, placeholders, documents);
    }

    /** Whether the whole "References & Delivery" card can be collapsed. */
    public boolean isReferencesSectionVisible() {
        return IndustryField.REFERENCES_SECTION.stream().anyMatch(this::isFieldVisible);
    }

    /** Whether the destination control in the header grid is shown. */
    public boolean isDestinationVisible() {
        return isFieldVisible(IndustryField.DESTINATION);
    }

    /** Default availability of each document workflow for this industry. */
    public record DocumentAccess(boolean deliveryChallan, boolean shippingLabel, boolean hazmatLabel) {
        public static DocumentAccess allEnabled() {
            return new DocumentAccess(true, true, true);
        }

        public static DocumentAccess none() {
            return new DocumentAccess(false, false, false);
        }

        public static DocumentAccess of(boolean dc, boolean shipping, boolean hazmat) {
            return new DocumentAccess(dc, shipping, hazmat);
        }
    }

    public enum DocumentType { DELIVERY_CHALLAN, SHIPPING_LABEL, HAZMAT_LABEL }
}
