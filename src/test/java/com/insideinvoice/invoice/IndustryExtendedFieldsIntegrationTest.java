package com.insideinvoice.invoice;

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
 * V29 contract for the Rental & Leasing and Healthcare & Wellness extended
 * invoice fields: they round-trip (create, read, update), never touch totals,
 * reject inverted periods, stay null on legacy invoices and are carried on the
 * anonymous public-share payload for the shared renderers.
 */
class IndustryExtendedFieldsIntegrationTest extends BaseIntegrationTest {

    private void setIndustry(User owner, String industry) throws Exception {
        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"" + industry + "\"}"))
                .andExpect(status().isOk());
    }

    // --------------------------------------------------------------- rental

    @Test
    @DisplayName("Rental fields round-trip on create and survive an update; totals untouched")
    void rentalFieldsRoundTrip() throws Exception {
        User owner = makeUser("rt-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setIndustry(owner, "RENTAL");
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        String create = """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-24",
                  "placeOfSupply": "Karnataka",
                  "agreementNumber": "RA-5566",
                  "assetNumber": "CRANE-12",
                  "serialNumber": "SN-8899",
                  "vehicleNumber": "KA-01-AB-1234",
                  "periodStart": "2026-10-12",
                  "periodEnd": "2026-10-16",
                  "billingPeriodStart": "2026-10-01",
                  "billingPeriodEnd": "2026-10-31",
                  "expectedReturnDate": "2026-10-17",
                  "depositReference": "DEP-100",
                  "items": [{"sno":1,"itemName":"Crane hire","qty":5,"rate":5000,"gstPercentage":18,"unit":"Day"}]
                }
                """.formatted(customerId);

        String created = mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.agreementNumber").value("RA-5566"))
                .andExpect(jsonPath("$.data.assetNumber").value("CRANE-12"))
                .andExpect(jsonPath("$.data.serialNumber").value("SN-8899"))
                .andExpect(jsonPath("$.data.vehicleNumber").value("KA-01-AB-1234"))
                .andExpect(jsonPath("$.data.periodStart").value("2026-10-12"))
                .andExpect(jsonPath("$.data.periodEnd").value("2026-10-16"))
                .andExpect(jsonPath("$.data.billingPeriodStart").value("2026-10-01"))
                .andExpect(jsonPath("$.data.billingPeriodEnd").value("2026-10-31"))
                .andExpect(jsonPath("$.data.expectedReturnDate").value("2026-10-17"))
                .andExpect(jsonPath("$.data.depositReference").value("DEP-100"))
                // extended fields are display-only: totals remain line-item driven
                .andExpect(jsonPath("$.data.subtotal").value(25000.00))
                .andExpect(jsonPath("$.data.grandTotal").value(29500.00))
                .andReturn().getResponse().getContentAsString();
        long invoiceId = objectMapper.readTree(created).path("data").path("id").asLong();

        // Update round-trip: client re-sends the loaded values, they persist
        String update = """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-31",
                  "status": "PENDING",
                  "agreementNumber": "RA-5566",
                  "assetNumber": "CRANE-12",
                  "serialNumber": "SN-8899",
                  "vehicleNumber": "KA-01-AB-1234",
                  "periodStart": "2026-10-12",
                  "periodEnd": "2026-10-16",
                  "billingPeriodStart": "2026-10-01",
                  "billingPeriodEnd": "2026-10-31",
                  "expectedReturnDate": "2026-10-17",
                  "depositReference": "DEP-100",
                  "items": [{"sno":1,"itemName":"Crane hire","qty":5,"rate":5000,"gstPercentage":18,"unit":"Day"}]
                }
                """.formatted(customerId);
        mockMvc.perform(put("/api/invoices/" + invoiceId)
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agreementNumber").value("RA-5566"))
                .andExpect(jsonPath("$.data.periodEnd").value("2026-10-16"))
                .andExpect(jsonPath("$.data.depositReference").value("DEP-100"));

        mockMvc.perform(get("/api/invoices/" + invoiceId)
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetNumber").value("CRANE-12"))
                .andExpect(jsonPath("$.data.expectedReturnDate").value("2026-10-17"));
    }

    @Test
    @DisplayName("Inverted rental or billing period is rejected with 400")
    void invertedPeriodRejected() throws Exception {
        User owner = makeUser("rtv-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setIndustry(owner, "RENTAL");
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        String invertedPeriod = """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-24",
                  "placeOfSupply": "Karnataka",
                  "periodStart": "2026-10-16",
                  "periodEnd": "2026-10-12",
                  "items": [{"sno":1,"itemName":"Crane hire","qty":1,"rate":1000,"gstPercentage":18}]
                }
                """.formatted(customerId);
        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invertedPeriod))
                .andExpect(status().isBadRequest());

        String invertedBilling = """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-24",
                  "placeOfSupply": "Karnataka",
                  "billingPeriodStart": "2026-10-31",
                  "billingPeriodEnd": "2026-10-01",
                  "items": [{"sno":1,"itemName":"Crane hire","qty":1,"rate":1000,"gstPercentage":18}]
                }
                """.formatted(customerId);
        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invertedBilling))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Only one end of a period: stored as-is (no inference from invoice/due dates)")
    void oneSidedPeriodAccepted() throws Exception {
        User owner = makeUser("rt1-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": %d,
                                  "invoiceType": "TAX_INVOICE",
                                  "invoiceDate": "2026-10-10",
                                  "dueDate": "2026-10-24",
                                  "placeOfSupply": "Karnataka",
                                  "periodStart": "2026-10-12",
                                  "items": [{"sno":1,"itemName":"Service","qty":1,"rate":1000,"gstPercentage":18}]
                                }
                                """.formatted(customerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.periodStart").value("2026-10-12"))
                .andExpect(jsonPath("$.data.periodEnd").value(nullValue()));
    }

    // ----------------------------------------------------------- healthcare

    @Test
    @DisplayName("Healthcare fields round-trip; rental-only fields are not required and stay null")
    void healthcareFieldsRoundTrip() throws Exception {
        User owner = makeUser("hc-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setIndustry(owner, "HEALTHCARE");
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": %d,
                                  "invoiceType": "TAX_INVOICE",
                                  "invoiceDate": "2026-10-10",
                                  "dueDate": "2026-10-24",
                                  "placeOfSupply": "Karnataka",
                                  "patientReference": "PAT-2211",
                                  "serviceDate": "2026-10-10",
                                  "treatmentReference": "Sess-14",
                                  "referringDoctor": "Dr. Iyer",
                                  "billingPeriodStart": "2026-10-01",
                                  "billingPeriodEnd": "2026-10-31",
                                  "referenceNumber": "VISIT-77",
                                  "items": [{"sno":1,"itemName":"Consultation","qty":1,"rate":800,"gstPercentage":18,"unit":"Nos"}]
                                }
                                """.formatted(customerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.patientReference").value("PAT-2211"))
                .andExpect(jsonPath("$.data.serviceDate").value("2026-10-10"))
                .andExpect(jsonPath("$.data.treatmentReference").value("Sess-14"))
                .andExpect(jsonPath("$.data.referringDoctor").value("Dr. Iyer"))
                .andExpect(jsonPath("$.data.billingPeriodStart").value("2026-10-01"))
                .andExpect(jsonPath("$.data.billingPeriodEnd").value("2026-10-31"))
                .andExpect(jsonPath("$.data.referenceNumber").value("VISIT-77"))
                // rental-only field never supplied -> null, not blank
                .andExpect(jsonPath("$.data.agreementNumber").value(nullValue()))
                .andExpect(jsonPath("$.data.grandTotal").value(944.00));
    }

    @Test
    @DisplayName("Legacy invoice: extended fields are null (never defaulted)")
    void legacyInvoiceFieldsNull() throws Exception {
        User owner = makeUser("lgx-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        var legacy = seedInvoice(owner);

        mockMvc.perform(get("/api/invoices/" + legacy.getId())
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agreementNumber").value(nullValue()))
                .andExpect(jsonPath("$.data.patientReference").value(nullValue()))
                .andExpect(jsonPath("$.data.periodStart").value(nullValue()))
                .andExpect(jsonPath("$.data.depositReference").value(nullValue()));
    }

    @Test
    @DisplayName("Omitted extended fields on update clear them (all-or-nothing contract)")
    void updateWithoutFieldsClearsThem() throws Exception {
        User owner = makeUser("clr-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setIndustry(owner, "RENTAL");
        long customerId = seedCustomer(owner.getBusinessId()).getId();

        String created = mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": %d,
                                  "invoiceType": "TAX_INVOICE",
                                  "invoiceDate": "2026-10-10",
                                  "dueDate": "2026-10-24",
                                  "placeOfSupply": "Karnataka",
                                  "agreementNumber": "RA-9",
                                  "items": [{"sno":1,"itemName":"Hire","qty":1,"rate":1000,"gstPercentage":18}]
                                }
                                """.formatted(customerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long invoiceId = objectMapper.readTree(created).path("data").path("id").asLong();

        mockMvc.perform(put("/api/invoices/" + invoiceId)
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": %d,
                                  "invoiceType": "TAX_INVOICE",
                                  "invoiceDate": "2026-10-10",
                                  "dueDate": "2026-10-24",
                                  "status": "PENDING",
                                  "placeOfSupply": "Karnataka",
                                  "items": [{"sno":1,"itemName":"Hire","qty":1,"rate":1000,"gstPercentage":18}]
                                }
                                """.formatted(customerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agreementNumber").value(nullValue()));
    }

    // --------------------------------------------------------- public payload

    @Test
    @DisplayName("Public share payload carries the rental extended fields for shared renderers")
    void publicShareCarriesExtendedFields() throws Exception {
        User owner = makeUser("px-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setIndustry(owner, "RENTAL");

        String created = mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": %d,
                                  "invoiceType": "TAX_INVOICE",
                                  "invoiceDate": "2026-10-10",
                                  "dueDate": "2026-10-24",
                                  "placeOfSupply": "Karnataka",
                                  "agreementNumber": "RA-5566",
                                  "periodStart": "2026-10-12",
                                  "periodEnd": "2026-10-16",
                                  "depositReference": "DEP-100",
                                  "items": [{"sno":1,"itemName":"Crane hire","qty":1,"rate":5000,"gstPercentage":18,"unit":"Day"}]
                                }
                                """.formatted(seedCustomer(owner.getBusinessId()).getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn().getResponse().getContentAsString();
        long invoiceId = objectMapper.readTree(created).path("data").path("id").asLong();

        String shareToken = mockMvc.perform(post("/api/invoices/" + invoiceId + "/share")
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(shareToken).path("data").path("token").asText();

        mockMvc.perform(get("/api/public/invoices/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agreementNumber").value("RA-5566"))
                .andExpect(jsonPath("$.data.periodStart").value("2026-10-12"))
                .andExpect(jsonPath("$.data.periodEnd").value("2026-10-16"))
                .andExpect(jsonPath("$.data.depositReference").value("DEP-100"))
                .andExpect(jsonPath("$.data.patientReference").value(nullValue()));
    }
}
