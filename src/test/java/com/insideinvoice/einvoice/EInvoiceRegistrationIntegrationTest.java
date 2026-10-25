package com.insideinvoice.einvoice;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.einvoice.dto.EInvoiceConfigRequest;
import com.insideinvoice.einvoice.service.EInvoiceConfigService;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end IRN registration against the sandbox MOCK provider: enable →
 * register → the ACTUAL provider response (mock-stamped IRN + ack) persists
 * on the registration AND stamps the invoice; double submission is a no-op;
 * disabled businesses are refused; mock IRNs are visibly flagged.
 */
class EInvoiceRegistrationIntegrationTest extends BaseIntegrationTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private EInvoiceConfigService configService;

    private static String runId() {
        return Long.toHexString(System.currentTimeMillis() % 1000000);
    }

    private void enableMockSandbox(User owner) {
        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        business.setGstIn("29ABCDE1234F1Z5");
        businessRepository.save(business);
        EInvoiceConfigRequest request = new EInvoiceConfigRequest();
        request.setGstin("");
        request.setProvider("MOCK");
        request.setEnvironment("SANDBOX");
        request.setEnabled(true);
        request.setAutoRegister(false);
        request.setClientId("cid");
        request.setClientSecret("secret");
        configService.saveConfig(owner.getId(), owner.getBusinessId(), request);
    }

    @Test
    @DisplayName("Register: mock IRP responds, IRN + ack persist and stamp the invoice; repeat click is a no-op")
    void registerPersistsActualResponseAndIsIdempotent() throws Exception {
        User owner = makeUser("einv-reg-" + runId(), Role.USER);
        enableMockSandbox(owner);
        Invoice invoice = seedInvoice(owner);
        String auth = bearerFor(owner);

        mockMvc.perform(get("/api/einvoice/status/" + invoice.getId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.derivedStatus").value("READY_TO_REGISTER"));

        mockMvc.perform(post("/api/einvoice/register/" + invoice.getId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.derivedStatus").value("REGISTERED"))
                .andExpect(jsonPath("$.data.registration.irn").value(
                        org.hamcrest.Matchers.startsWith("MOCK-IRN-")))
                .andExpect(jsonPath("$.data.registration.ackNumber").value(
                        org.hamcrest.Matchers.startsWith("MOCK-ACK-")))
                .andExpect(jsonPath("$.data.registration.mock").value(true));

        // The invoice itself is stamped from the provider response.
        Invoice stamped = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertNotNull(stamped.getIrn());
        assertTrue(stamped.getIrn().startsWith("MOCK-IRN-"));
        assertNotNull(stamped.getAckNo());
        assertEquals("REGISTERED_MOCK", stamped.getEinvoiceStatus());

        // Double-click / retry: same registration, same IRN, no duplicate row.
        mockMvc.perform(post("/api/einvoice/register/" + invoice.getId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registration.irn").value(stamped.getIrn()));

        assertEquals(1, registrationCount(invoice.getId()));
    }

    @Test
    @DisplayName("Disabled business: registration is refused with 400 — nothing is submitted")
    void registerRefusedWhenDisabled() throws Exception {
        User owner = makeUser("einv-regoff-" + runId(), Role.USER);
        Invoice invoice = seedInvoice(owner);
        mockMvc.perform(post("/api/einvoice/register/" + invoice.getId())
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Enable e-invoicing")));
    }

    @Autowired
    private com.insideinvoice.einvoice.repository.EInvoiceRegistrationRepository registrationRepo;

    private long registrationCount(Long invoiceId) {
        return registrationRepo.findAll().stream().filter(r -> r.getInvoiceId().equals(invoiceId)).count();
    }
}
