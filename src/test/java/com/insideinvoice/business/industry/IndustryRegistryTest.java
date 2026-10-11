package com.insideinvoice.business.industry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure configuration tests — no Spring context, no database.
 *
 * <p>These guard the two promises that matter most for this feature: the
 * catalogue covers every published profile, and no profile can ever remove a
 * GST/accounting field or break the fallback for legacy businesses.</p>
 */
class IndustryRegistryTest {

    @Test
    @DisplayName("Every published industry profile has a resolved configuration")
    void allIndustriesHaveConfig() {
        assertEquals(Industry.values().length, IndustryRegistry.snapshot().size());
        for (Industry industry : Industry.values()) {
            IndustryConfig config = IndustryRegistry.forIndustry(industry);
            assertNotNull(config, "missing config for " + industry);
            assertEquals(industry, config.industry());
            assertEquals(industry.getId(), config.industry().getId());
            assertFalse(config.industry().getDisplayName().isBlank());
            assertFalse(config.industry().getDescription().isBlank());
        }
    }

    @Test
    @DisplayName("The 13 onboarding profiles are all present with the expected ids")
    void expectedProfileIds() {
        Set<String> expected = Set.of(
                "TRADING", "CONSTRUCTION", "MANUFACTURING", "PROFESSIONAL_SERVICES",
                "REPAIR", "TRANSPORT", "FOOD", "RENTAL", "TELECOM_IT",
                "HEALTHCARE", "EDUCATION", "AGRICULTURE", "OTHER");
        assertEquals(expected, new HashSet<>(Industry.ids()));
        assertEquals(13, Industry.ids().size());
    }

    @ParameterizedTest
    @EnumSource(Industry.class)
    @DisplayName("No industry can hide a protected GST/accounting/core field")
    void protectedFieldsNeverHidden(Industry industry) {
        IndustryConfig config = IndustryRegistry.forIndustry(industry);
        for (String protectedField : IndustryField.PROTECTED_FIELDS) {
            assertTrue(config.isFieldVisible(protectedField),
                    industry + " must not hide " + protectedField);
        }
        // HSN/SAC and the GST rate are the fields most at risk of being turned
        // off for service industries; assert them explicitly.
        assertTrue(config.isFieldVisible(IndustryField.HSN));
        assertTrue(config.isFieldVisible(IndustryField.GST_PERCENTAGE));
        assertTrue(config.isFieldVisible(IndustryField.PLACE_OF_SUPPLY));
    }

    @ParameterizedTest
    @EnumSource(Industry.class)
    @DisplayName("Hidden fields are always known, configurable catalogue entries")
    void hiddenFieldsAreKnownConfigurableFields(Industry industry) {
        IndustryConfig config = IndustryRegistry.forIndustry(industry);
        for (String hidden : config.hiddenFields()) {
            assertTrue(IndustryField.ALL.contains(hidden),
                    industry + " hides unknown field " + hidden);
            assertTrue(IndustryField.CONFIGURABLE_FIELDS.contains(hidden),
                    industry + " hides non-configurable field " + hidden);
        }
        // visibleFields must be exactly ALL minus hidden.
        assertEquals(IndustryField.ALL.size() - config.hiddenFields().size(),
                config.visibleFields().size());
        for (String visible : config.visibleFields()) {
            assertFalse(config.hiddenFields().contains(visible));
        }
    }

    @Test
    @DisplayName("Label and placeholder keys always address real fields")
    void labelKeysAreRealFields() {
        for (IndustryConfig config : IndustryRegistry.all()) {
            for (String key : config.labels().keySet()) {
                assertTrue(IndustryField.ALL.contains(key)
                                || IndustryExtendedFields.applies(config.industry(), key),
                        config.industry() + " relabels unknown field " + key);
            }
            for (String key : config.placeholders().keySet()) {
                assertTrue(IndustryField.ALL.contains(key)
                                || IndustryExtendedFields.applies(config.industry(), key),
                        config.industry() + " hints unknown field " + key);
            }
        }
    }

    @Test
    @DisplayName("OTHER — the legacy fallback — hides nothing and enables every document")
    void otherIsTheBackwardCompatibleBaseline() {
        IndustryConfig other = IndustryRegistry.forIndustry(Industry.OTHER);
        assertTrue(other.hiddenFields().isEmpty());
        assertTrue(other.labels().isEmpty());
        assertEquals(IndustryField.ALL.size(), other.visibleFields().size());
        assertTrue(other.isReferencesSectionVisible());
        assertTrue(other.documents().deliveryChallan());
        assertTrue(other.documents().shippingLabel());
        assertTrue(other.documents().hazmatLabel());
        for (String field : IndustryField.ALL) {
            assertTrue(other.isFieldVisible(field), "OTHER must keep " + field);
        }
    }

    @Test
    @DisplayName("Missing, blank or legacy industry values resolve to OTHER")
    void resolveFallsBackSafely() {
        assertEquals(Industry.OTHER, Industry.resolve(null));
        assertEquals(Industry.OTHER, Industry.resolve(""));
        assertEquals(Industry.OTHER, Industry.resolve("   "));
        assertEquals(Industry.OTHER, Industry.resolve("not-an-industry"));
        assertEquals(Industry.OTHER, Industry.resolve("trading-tokens"));
        assertEquals(Industry.TRADING, Industry.resolve("trading"));
        assertEquals(Industry.TRADING, Industry.resolve("  TRADING  "));
        assertEquals(Industry.OTHER, IndustryRegistry.forRawIndustry(null).industry());
        assertEquals(Industry.TRADING, IndustryRegistry.forRawIndustry("trading").industry());
    }

    @Test
    @DisplayName("Strict validation rejects unknown ids but accepts every real one")
    void strictValidation() {
        assertTrue(Industry.isValid("TRADING"));
        assertTrue(Industry.isValid("other"));
        assertFalse(Industry.isValid(null));
        assertFalse(Industry.isValid(""));
        assertFalse(Industry.isValid("HACK"));
        for (String id : Industry.ids()) {
            assertTrue(Industry.isValid(id), "valid id rejected: " + id);
        }
    }

    @Test
    @DisplayName("The bean-validation pattern can never drift from the enum")
    void validationPatternMatchesEnum() {
        Pattern pattern = Pattern.compile(Industry.ID_PATTERN);
        for (String id : Industry.ids()) {
            assertTrue(pattern.matcher(id).matches(), "pattern rejects " + id);
        }
        assertFalse(pattern.matcher("").matches());
        assertFalse(pattern.matcher("OTHER ").matches());
        assertFalse(pattern.matcher("DROP TABLE").matches());
        assertFalse(pattern.matcher("trading").matches(), "ids are stored uppercase");
    }

    @Test
    @DisplayName("Service industries hide the dispatch/delivery cluster")
    void serviceIndustriesHideDispatchDetails() {
        Set<Industry> serviceIndustries = Set.of(
                Industry.PROFESSIONAL_SERVICES, Industry.TELECOM_IT,
                Industry.HEALTHCARE, Industry.EDUCATION);
        for (Industry industry : serviceIndustries) {
            IndustryConfig config = IndustryRegistry.forIndustry(industry);
            assertFalse(config.isFieldVisible(IndustryField.DELIVERY_NOTE), industry + " deliveryNote");
            assertFalse(config.isFieldVisible(IndustryField.DELIVERY_NOTE_DATE), industry + " deliveryNoteDate");
            assertFalse(config.isFieldVisible(IndustryField.DISPATCH_DOC_NUMBER), industry + " dispatchDocNumber");
            assertFalse(config.isFieldVisible(IndustryField.DISPATCHED_THROUGH), industry + " dispatchedThrough");
            assertFalse(config.isFieldVisible(IndustryField.TERMS_OF_DELIVERY), industry + " termsOfDelivery");
            assertFalse(config.isFieldVisible(IndustryField.DESTINATION), industry + " destination");
            // Reference and P.O. fields stay: they carry the project/account
            // reference the service industries actually need, which also keeps
            // the References card populated instead of collapsing to nothing.
            assertTrue(config.isFieldVisible(IndustryField.REFERENCE_NUMBER));
            assertTrue(config.isFieldVisible(IndustryField.OTHER_REFERENCES));
            assertTrue(config.isReferencesSectionVisible());
        }
    }

    @Test
    @DisplayName("Goods and transport industries keep the full dispatch toolkit")
    void goodsIndustriesKeepDispatchFields() {
        for (Industry industry : List.of(Industry.TRADING, Industry.TRANSPORT,
                Industry.CONSTRUCTION, Industry.MANUFACTURING, Industry.AGRICULTURE)) {
            IndustryConfig config = IndustryRegistry.forIndustry(industry);
            assertTrue(config.isFieldVisible(IndustryField.DELIVERY_NOTE), industry.name());
            assertTrue(config.isFieldVisible(IndustryField.DISPATCH_DOC_NUMBER), industry.name());
            assertTrue(config.isFieldVisible(IndustryField.DISPATCHED_THROUGH), industry.name());
            assertTrue(config.isFieldVisible(IndustryField.TERMS_OF_DELIVERY), industry.name());
            assertTrue(config.isFieldVisible(IndustryField.DESTINATION), industry.name());
        }
    }

    @Test
    @DisplayName("Document defaults follow the published profiles")
    void documentDefaults() {
        IndustryConfig.DocumentAccess trading =
                IndustryRegistry.forIndustry(Industry.TRADING).documents();
        assertTrue(trading.deliveryChallan() && trading.shippingLabel() && trading.hazmatLabel());

        IndustryConfig.DocumentAccess food =
                IndustryRegistry.forIndustry(Industry.FOOD).documents();
        assertTrue(food.deliveryChallan(), "caterers still deliver");
        assertFalse(food.shippingLabel());
        assertFalse(food.hazmatLabel());

        IndustryConfig.DocumentAccess transport =
                IndustryRegistry.forIndustry(Industry.TRANSPORT).documents();
        assertTrue(transport.deliveryChallan() && transport.shippingLabel() && transport.hazmatLabel());

        IndustryConfig.DocumentAccess services =
                IndustryRegistry.forIndustry(Industry.PROFESSIONAL_SERVICES).documents();
        assertFalse(services.deliveryChallan());
        assertFalse(services.shippingLabel());
        assertFalse(services.hazmatLabel());
    }

    @Test
    @DisplayName("Required fields are the same conservative base set everywhere")
    void requiredFieldsAreConservative() {
        for (IndustryConfig config : IndustryRegistry.all()) {
            assertEquals(IndustryField.BASE_REQUIRED, config.requiredFields());
            assertFalse(config.requiredFields().contains(IndustryField.HSN),
                    "HSN must never be mandatory — SAC applies only where it applies");
            // Every required field must be visible for that industry.
            for (String required : config.requiredFields()) {
                assertTrue(config.isFieldVisible(required),
                        config.industry() + " requires hidden field " + required);
            }
        }
    }

    @Test
    @DisplayName("Optional fields are visible fields minus the required set")
    void optionalFieldsDerived() {
        IndustryConfig config = IndustryRegistry.forIndustry(Industry.TRADING);
        List<String> optional = config.optionalFields();
        assertFalse(optional.isEmpty());
        for (String field : optional) {
            assertFalse(config.requiredFields().contains(field));
            assertTrue(config.isFieldVisible(field));
        }
        assertEquals(config.visibleFields().size() - config.requiredFields().size(),
                optional.size());
    }

    @Test
    @DisplayName("The references section collapses only when every field in it is hidden")
    void referencesSectionVisibility() {
        IndustryConfig services = IndustryRegistry.forIndustry(Industry.PROFESSIONAL_SERVICES);
        // referenceNumber and otherReferences are protected, so the card stays.
        assertTrue(services.isReferencesSectionVisible());

        IndustryConfig other = IndustryRegistry.forIndustry(Industry.OTHER);
        assertTrue(other.isReferencesSectionVisible());
        assertTrue(other.isDestinationVisible());
        assertFalse(services.isDestinationVisible());
    }

    @ParameterizedTest
    @EnumSource(Industry.class)
    @DisplayName("Configuration is immutable for every profile")
    void configsAreImmutable(Industry industry) {
        IndustryConfig config = IndustryRegistry.forIndustry(industry);
        assertEquals(config, IndustryRegistry.forIndustry(industry), "config must be stable");
        assertTrue(java.util.Collections.emptyList().isEmpty());
        Map<String, String> labels = config.labels();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> labels.put("hsn", "mutated"));
        Set<String> hidden = config.hiddenFields();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> hidden.add("notes"));
    }
}
