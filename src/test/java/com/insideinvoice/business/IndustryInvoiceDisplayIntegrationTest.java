package com.insideinvoice.business;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
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
 * Contract for the construction billing extras: the display-only HSN/SAC switch
 * (showHnSac), the COMPLETE_PROJECT / ITEMIZED billingMode round-trip, the
 * per-line unit defaulting to "Piece", and the guarantee that hiding the column
 * never deletes stored HSN values or changes totals.
 */
class IndustryInvoiceDisplayIntegrationTest extends BaseIntegrationTest {

    // ---------------------------------------------------------------- HSN toggle

    @Test
    @DisplayName("showHnSac defaults to ON and the toggle persists through the profile")
    void hsnToggleDefaultsOnAndPersists() throws Exception {
        User owner = makeUser("hsn-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);

        // Fresh business: field absent from older rows -> ON (null-safe in renderers)
        mockMvc.perform(get("/api/business/me").header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showHnSac").value(true));

        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showHnSac\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showHnSac").value(false));

        mockMvc.perform(get("/api/business/me").header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showHnSac").value(false));

        // Null leaves the stored value untouched (partial updates stay partial)
        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessName\":\"Renamed Co\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showHnSac").value(false))
                .andExpect(jsonPath("$.data.businessName").value("Renamed Co"));
    }

    @Test
    @DisplayName("Invoice-settings endpoint carries showHnSac the same way")
    void hsnToggleThroughInvoiceSettings() throws Exception {
        User owner = makeUser("is-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);

        mockMvc.perform(put("/api/business/invoice-settings")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showHnSac\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showHnSac").value(false));

        mockMvc.perform(get("/api/business/me").header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showHnSac").value(false));
    }

    @Test
    @DisplayName("Hiding HSN never deletes stored HSN values or changes totals")
    void hidingHsnKeepsDataAndTotals() throws Exception {
        User owner = makeUser("keep-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showHnSac\":false}"))
                .andExpect(status().isOk());

        var customer = seedCustomer(owner.getBusinessId());
        String body = """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-24",
                  "placeOfSupply": "Karnataka",
                  "items": [{"sno":1,"itemName":"Bricks","hsn":"69072100","qty":100,"rate":8,"gstPercentage":5,"unit":"Nos"}]
                }
                """.formatted(customer.getId());

        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items[0].hsn").value("69072100"))
                .andExpect(jsonPath("$.data.items[0].unit").value("Nos"))
                .andExpect(jsonPath("$.data.subtotal").value(800.00))
                .andExpect(jsonPath("$.data.taxAmount").value(40.00))
                .andExpect(jsonPath("$.data.grandTotal").value(840.00));
    }

    // -------------------------------------------------------------- billingMode

    @Test
    @DisplayName("billingMode COMPLETE_PROJECT round-trips on create and survives an update")
    void billingModeRoundTrips() throws Exception {
        User contractor = makeUser("con-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        var customer = seedCustomer(contractor.getBusinessId());

        String create = """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-24",
                  "placeOfSupply": "Karnataka",
                  "billingMode": "COMPLETE_PROJECT",
                  "deliveryNote": "WO-77",
                  "destination": "Site 4, Mysuru",
                  "items": [{"sno":1,"itemName":"Pile foundation \u2014 RCC","qty":1,"rate":250000,"gstPercentage":18,"unit":"Lot"}]
                }
                """.formatted(customer.getId());

        String created = mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(contractor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.billingMode").value("COMPLETE_PROJECT"))
                .andExpect(jsonPath("$.data.items[0].unit").value("Lot"))
                .andReturn().getResponse().getContentAsString();
        long invoiceId = objectMapper.readTree(created).path("data").path("id").asLong();

        // An update that re-sends billingMode keeps it; totals stay line-item driven
        String update = """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-31",
                  "status": "PENDING",
                  "billingMode": "COMPLETE_PROJECT",
                  "items": [{"sno":1,"itemName":"Pile foundation \u2014 RCC","qty":1,"rate":250000,"gstPercentage":18,"unit":"Lot"}]
                }
                """.formatted(customer.getId());
        mockMvc.perform(put("/api/invoices/" + invoiceId)
                        .header("Authorization", bearerFor(contractor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.billingMode").value("COMPLETE_PROJECT"))
                .andExpect(jsonPath("$.data.items[0].unit").value("Lot"))
                .andExpect(jsonPath("$.data.grandTotal").value(295000.00));
    }

    @Test
    @DisplayName("ITEMIZED mode round-trips; NULL billingMode maps to null (legacy invoices)")
    void itemizedAndLegacyBillingMode() throws Exception {
        User contractor = makeUser("itm-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        var customer = seedCustomer(contractor.getBusinessId());

        String create = """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-24",
                  "billingMode": "ITEMIZED",
                  "items": [{"sno":1,"itemName":"Labour","qty":3,"rate":1000,"gstPercentage":18}]
                }
                """.formatted(customer.getId());

        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(contractor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.billingMode").value("ITEMIZED"))
                // Older clients omit unit -> mapper defaults to what templates hard-coded
                .andExpect(jsonPath("$.data.items[0].unit").value("Piece"));

        // Legacy seeded invoice (billingMode never set) exposes null, not ITEMIZED
        User legacyOwner = makeUser("lgc-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        var legacy = seedInvoice(legacyOwner);
        String read = mockMvc.perform(get("/api/invoices/" + legacy.getId())
                        .header("Authorization", bearerFor(legacyOwner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(read)
                .contains("\"billingMode\":null")
                .doesNotContain("\"billingMode\":\"ITEMIZED\"");
    }

    @Test
    @DisplayName("Unknown or blank billingMode is stored as NULL, never as ITEMIZED")
    void unknownBillingModeStoresNull() throws Exception {
        User contractor = makeUser("bad-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        var customer = seedCustomer(contractor.getBusinessId());

        String create = """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-24",
                  "billingMode": "NOT_A_MODE",
                  "items": [{"sno":1,"itemName":"Slab","qty":1,"rate":100,"gstPercentage":18}]
                }
                """.formatted(customer.getId());

        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(contractor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.billingMode").value(nullValue()));
    }

    // ------------------------------------------------------- public share payload

    @Test
    @DisplayName("Public share payload carries seller.showHnSac and item.unit for renderers")
    void publicShareCarriesDisplayFlags() throws Exception {
        User owner = makeUser("pub-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showHnSac\":false,\"industry\":\"CONSTRUCTION\",\"businessName\":\"Site Works\"}"))
                .andExpect(status().isOk());

        String created = mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": %d,
                                  "invoiceType": "TAX_INVOICE",
                                  "invoiceDate": "2026-10-10",
                                  "dueDate": "2026-10-24",
                                  "billingMode": "COMPLETE_PROJECT",
                                  "items": [{"sno":1,"itemName":"Compound wall","qty":1,"rate":1000,"gstPercentage":18,"unit":"Lot"}]
                                }
                                """.formatted(seedCustomer(owner.getBusinessId()).getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn().getResponse().getContentAsString();
        long invoiceId = objectMapper.readTree(created).path("data").path("id").asLong();

        String shareToken = mockMvc.perform(post("/api/invoices/" + invoiceId + "/share")
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(shareToken).path("data").path("token").asText();

        mockMvc.perform(get("/api/public/invoices/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.billingMode").value("COMPLETE_PROJECT"))
                .andExpect(jsonPath("$.data.seller.showHnSac").value(false))
                .andExpect(jsonPath("$.data.seller.industry").value("CONSTRUCTION"))
                .andExpect(jsonPath("$.data.items[0].unit").value("Lot"));
    }
}
