package com.insideinvoice.invoice;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.business.entity.Business;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invoice-level tax mode (CGST_SGST vs IGST) — Phase 3 of
 * PLAN -> TESTS -> IMPLEMENT -> VERIFY (written first, expected to fail).
 *
 * <p>Proves: both modes persist and round-trip through the API with IDENTICAL
 * totals (the split is presentation; the total tax is counted exactly once),
 * an absent mode is derived from supplier state vs place of supply or stays
 * NULL when undeterminable (legacy rows are never force-assigned), updates
 * switch modes without touching sibling invoices, invalid values are
 * rejected, tenant isolation still holds, and the anonymous public-share
 * payload carries the saved mode for the shared renderers.</p>
 */
class InvoiceTaxModeIntegrationTest extends BaseIntegrationTest {

    private void setBusinessState(User owner, String state) {
        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        business.setState(state);
        businessRepository.save(business);
    }

    private String createJson(long customerId, String placeOfSupply, String taxMode) {
        String taxField = taxMode == null ? "" : ",\n                  \"taxMode\": \"" + taxMode + "\"";
        return """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-24",
                  "placeOfSupply": %s%s,
                  "items": [{"sno":1,"itemName":"Service","qty":1,"rate":%s,"gstPercentage":%s}]
                }
                """.formatted(customerId,
                placeOfSupply == null ? "null" : "\"" + placeOfSupply + "\"",
                taxField, "100", "18");
    }

    private String createJsonWithRate(long customerId, String placeOfSupply, String taxMode,
                                      String rate, String gstPercent) {
        String taxField = taxMode == null ? "" : ",\n                  \"taxMode\": \"" + taxMode + "\"";
        return """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-24",
                  "placeOfSupply": %s%s,
                  "items": [{"sno":1,"itemName":"Service","qty":1,"rate":%s,"gstPercentage":%s}]
                }
                """.formatted(customerId,
                placeOfSupply == null ? "null" : "\"" + placeOfSupply + "\"",
                taxField, rate, gstPercent);
    }

    private String create(User owner, String json) throws Exception {
        return mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private long idOf(String responseBody) throws Exception {
        return objectMapper.readTree(responseBody).path("data").path("id").asLong();
    }

    private String updateJson(long customerId, String placeOfSupply, String taxMode) {
        String taxField = taxMode == null ? "" : ",\n                  \"taxMode\": \"" + taxMode + "\"";
        return """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-31",
                  "status": "PENDING",
                  "placeOfSupply": %s%s,
                  "items": [{"sno":1,"itemName":"Service","qty":1,"rate":100,"gstPercentage":18}]
                }
                """.formatted(customerId,
                placeOfSupply == null ? "null" : "\"" + placeOfSupply + "\"",
                taxField);
    }

    // ------------------------------------------------- spec scenarios

    @Test
    @DisplayName("₹100 @ 18%: both modes persist with tax 18.00 / grand 118.00, only the structure differs")
    void scenarioAAndBHundredRupeesAt18() throws Exception {
        User owner = makeUser("tx-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setBusinessState(owner, "Karnataka");
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        String split = create(owner, createJson(customerId, "Karnataka", "CGST_SGST"));
        mockMvc.perform(get("/api/invoices/" + idOf(split))
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxMode").value("CGST_SGST"))
                .andExpect(jsonPath("$.data.subtotal").value(100.00))
                .andExpect(jsonPath("$.data.taxAmount").value(18.00))
                .andExpect(jsonPath("$.data.grandTotal").value(118.00));

        String igst = create(owner, createJson(customerId, "Karnataka", "IGST"));
        mockMvc.perform(get("/api/invoices/" + idOf(igst))
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxMode").value("IGST"))
                .andExpect(jsonPath("$.data.subtotal").value(100.00))
                .andExpect(jsonPath("$.data.taxAmount").value(18.00))
                .andExpect(jsonPath("$.data.grandTotal").value(118.00));
    }

    @Test
    @DisplayName("₹9 @ 18% baseline: tax 1.62 and grand 10.62 in BOTH modes (from real line-item maths)")
    void scenarioBaselineNineRupeesAt18() throws Exception {
        User owner = makeUser("tx9-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setBusinessState(owner, "Karnataka");
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        for (String mode : new String[]{"CGST_SGST", "IGST"}) {
            String created = create(owner, createJsonWithRate(customerId, "Karnataka", mode, "9", "18"));
            mockMvc.perform(get("/api/invoices/" + idOf(created))
                            .header("Authorization", bearerFor(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.taxMode").value(mode))
                    .andExpect(jsonPath("$.data.subtotal").value(9.00))
                    .andExpect(jsonPath("$.data.taxAmount").value(1.62))
                    .andExpect(jsonPath("$.data.grandTotal").value(10.62));
        }
    }

    // -------------------------------------------------- default derivation

    @Test
    @DisplayName("Absent tax mode on create is derived from supplier state vs place of supply and persisted")
    void absentTaxModeIsDerivedAndPersisted() throws Exception {
        User owner = makeUser("drv-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setBusinessState(owner, "Karnataka");
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        String intra = create(owner, createJson(customerId, "Karnataka", null));
        mockMvc.perform(get("/api/invoices/" + idOf(intra))
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxMode").value("CGST_SGST"));

        String inter = create(owner, createJson(customerId, "Maharashtra", null));
        mockMvc.perform(get("/api/invoices/" + idOf(inter))
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxMode").value("IGST"));
    }

    @Test
    @DisplayName("Undeterminable supplier state -> tax mode stays NULL (no blind IGST or split assignment)")
    void absentTaxModeStaysNullWhenUndeterminable() throws Exception {
        User owner = makeUser("nul-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        // business.state intentionally left unset
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        String created = create(owner, createJson(customerId, "Karnataka", null));
        mockMvc.perform(get("/api/invoices/" + idOf(created))
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxMode").value(nullValue()));
    }

    // ------------------------------------------- update / isolation / safety

    @Test
    @DisplayName("Update switches the mode without touching totals or the sibling invoice")
    void updateSwitchesModeWithoutTouchingSibling() throws Exception {
        User owner = makeUser("sw-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setBusinessState(owner, "Karnataka");
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        long invoiceA = idOf(create(owner, createJson(customerId, "Karnataka", "IGST")));
        long invoiceB = idOf(create(owner, createJson(customerId, "Karnataka", "IGST")));

        mockMvc.perform(put("/api/invoices/" + invoiceA)
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(customerId, "Karnataka", "CGST_SGST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxMode").value("CGST_SGST"))
                // mode is presentation: totals stay exactly as computed from the lines
                .andExpect(jsonPath("$.data.taxAmount").value(18.00))
                .andExpect(jsonPath("$.data.grandTotal").value(118.00));

        mockMvc.perform(get("/api/invoices/" + invoiceA)
                        .header("Authorization", bearerFor(owner)))
                .andExpect(jsonPath("$.data.taxMode").value("CGST_SGST"));

        // the other invoice of the same business is untouched — each invoice owns its mode
        // (it was IGST, so a wiped/derived-wrong mode would show CGST_SGST and fail here)
        mockMvc.perform(get("/api/invoices/" + invoiceB)
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxMode").value("IGST"));
    }

    @Test
    @DisplayName("Update without the taxMode field keeps the stored selection (absent = keep contract)")
    void updateWithoutTaxModeKeepsStored() throws Exception {
        User owner = makeUser("kee-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setBusinessState(owner, "Karnataka");
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        long invoiceId = idOf(create(owner, createJson(customerId, "Karnataka", "IGST")));

        mockMvc.perform(put("/api/invoices/" + invoiceId)
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(customerId, "Karnataka", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxMode").value("IGST"));

        mockMvc.perform(get("/api/invoices/" + invoiceId)
                        .header("Authorization", bearerFor(owner)))
                .andExpect(jsonPath("$.data.taxMode").value("IGST"));
    }

    @Test
    @DisplayName("Invalid tax mode values are rejected with 400 on create and update")
    void invalidTaxModeRejected() throws Exception {
        User owner = makeUser("bad-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setBusinessState(owner, "Karnataka");
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(customerId, "Karnataka", "HST")))
                .andExpect(status().isBadRequest());

        long invoiceId = idOf(create(owner, createJson(customerId, "Karnataka", "IGST")));
        mockMvc.perform(put("/api/invoices/" + invoiceId)
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(customerId, "Karnataka", "UTGST")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Legacy invoice with no stored tax mode exposes null (compatibility, never rewritten)")
    void legacyInvoiceExposesNullTaxMode() throws Exception {
        User owner = makeUser("leg-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        var legacy = seedInvoice(owner);

        mockMvc.perform(get("/api/invoices/" + legacy.getId())
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxMode").value(nullValue()))
                .andExpect(jsonPath("$.data.taxAmount").value(36.00))
                .andExpect(jsonPath("$.data.grandTotal").value(236.00));
    }

    // ------------------------------------------------------ totals invariants

    @Test
    @DisplayName("Discount + mixed rates: totals identical in both modes (tax counted once)")
    void totalsAreModeIndependentWithDiscount() throws Exception {
        User owner = makeUser("dis-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setBusinessState(owner, "Karnataka");
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        String body = """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-24",
                  "placeOfSupply": "Karnataka",
                  "discountPercent": 10,
                  "taxMode": "%s",
                  "items": [
                    {"sno":1,"itemName":"A","qty":1,"rate":100,"gstPercentage":18},
                    {"sno":2,"itemName":"B","qty":1,"rate":50,"gstPercentage":12}
                  ]
                }
                """;

        for (String mode : new String[]{"CGST_SGST", "IGST"}) {
            String created = create(owner, body.formatted(customerId, mode));
            mockMvc.perform(get("/api/invoices/" + idOf(created))
                            .header("Authorization", bearerFor(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.taxMode").value(mode))
                    .andExpect(jsonPath("$.data.subtotal").value(150.00))
                    .andExpect(jsonPath("$.data.grandTotal").value(156.60))
                    .andExpect(jsonPath("$.data.taxAmount").value(21.60));
        }
    }

    @Test
    @DisplayName("PROFORMA_INVOICE round-trips the tax mode like a tax invoice")
    void proformaRoundTripsTaxMode() throws Exception {
        User owner = makeUser("pro-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setBusinessState(owner, "Karnataka");
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        String created = create(owner, createJson(customerId, "Karnataka", "IGST")
                .replace("\"invoiceType\": \"TAX_INVOICE\"", "\"invoiceType\": \"PROFORMA_INVOICE\""));
        long invoiceId = idOf(created);

        mockMvc.perform(get("/api/invoices/" + invoiceId)
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceType").value("PROFORMA_INVOICE"))
                .andExpect(jsonPath("$.data.taxMode").value("IGST"));
    }

    // ------------------------------------------------------------ sharing

    @Test
    @DisplayName("Public share payload carries the saved tax mode for the shared renderers")
    void publicShareCarriesTaxMode() throws Exception {
        User owner = makeUser("shx-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setBusinessState(owner, "Karnataka");
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        long invoiceId = idOf(create(owner, createJson(customerId, "Karnataka", "IGST")));

        String share = mockMvc.perform(post("/api/invoices/" + invoiceId + "/share")
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(share).path("data").path("token").asText();

        mockMvc.perform(get("/api/public/invoices/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxMode").value("IGST"));
    }

    // ------------------------------------------------------ tenant isolation

    @Test
    @DisplayName("Another business cannot read or change the tax mode of someone else's invoice")
    void otherTenantCannotTouchTaxMode() throws Exception {
        User owner = makeUser("iso-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setBusinessState(owner, "Karnataka");
        long customerId = seedCustomer(owner.getBusinessId()).getId();
        long invoiceId = idOf(create(owner, createJson(customerId, "Karnataka", "IGST")));

        User attacker = makeUser("atk-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);

        mockMvc.perform(get("/api/invoices/" + invoiceId)
                        .header("Authorization", bearerFor(attacker)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/invoices/" + invoiceId)
                        .header("Authorization", bearerFor(attacker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(customerId, "Maharashtra", "CGST_SGST")))
                .andExpect(status().isNotFound());

        // victim's stored selection is intact after the rejected write
        mockMvc.perform(get("/api/invoices/" + invoiceId)
                        .header("Authorization", bearerFor(owner)))
                .andExpect(jsonPath("$.data.taxMode").value("IGST"));
    }
}
