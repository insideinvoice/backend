package com.insideinvoice.einvoice;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.einvoice.dto.EInvoiceConfigRequest;
import com.insideinvoice.einvoice.service.EInvoiceConfigService;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auto-registration: a business that enabled e-invoicing with auto-register
 * gets the IRN fetched during invoice CREATION itself — the create response
 * already carries the provider's IRN/Ack. Mock + Sandbox proves the pipeline;
 * a Mock-only trial needs zero credential typing (placeholders are defaults).
 */
class EInvoiceAutoRegisterIntegrationTest extends BaseIntegrationTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private EInvoiceConfigService configService;

    private static String runId() {
        return Long.toHexString(System.currentTimeMillis() % 1000000);
    }

    private void enableAutoRegister(User owner) {
        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        business.setGstIn("29ABCDE1234F1Z5");
        businessRepository.save(business);
        EInvoiceConfigRequest request = new EInvoiceConfigRequest();
        request.setGstin("");
        request.setProvider("MOCK");
        request.setEnvironment("SANDBOX");
        request.setEnabled(true);
        request.setAutoRegister(true);
        request.setClientId("mock-sandbox");
        request.setClientSecret("mock-sandbox");
        configService.saveConfig(owner.getId(), owner.getBusinessId(), request);
    }

    @Test
    @DisplayName("Invoice CREATE auto-registers: response carries the mock IRN/Ack and the invoice is stamped")
    void createAutoRegistersWhenEnabled() throws Exception {
        User owner = makeUser("einv-auto-" + runId(), Role.USER);
        enableAutoRegister(owner);
        String auth = bearerFor(owner);
        Long customerId = seedCustomer(owner.getBusinessId()).getId();

        String body = "{"
                + "\"customerId\":" + customerId + ","
                + "\"invoiceType\":\"TAX_INVOICE\","
                + "\"invoiceDate\":\"2026-10-25\","
                + "\"dueDate\":\"2026-11-09\","
                + "\"placeOfSupply\":\"Karnataka\","
                + "\"paymentMode\":\"CASH\","
                + "\"items\":[{\"sno\":1,\"itemName\":\"Pipe\",\"qty\":1,\"rate\":1000,\"gstPercentage\":18}]"
                + "}";

        mockMvc.perform(post("/api/invoices").header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.einvoiceStatus").value("REGISTERED_MOCK"))
                .andExpect(jsonPath("$.data.irn").value(
                        org.hamcrest.Matchers.startsWith("MOCK-IRN-")))
                .andExpect(jsonPath("$.data.ackNo").value(
                        org.hamcrest.Matchers.startsWith("MOCK-ACK-")));

        // One registration only — auto and manual never double-register.
        long rows = registrationRepo.findAll().stream()
                .filter(r -> r.getBusinessId().equals(owner.getBusinessId())).count();
        assertEquals(1, rows);
    }

    @Test
    @DisplayName("Without auto-register: creation stays registration-free (manual flow untouched)")
    void createWithoutAutoRegisterSkips() throws Exception {
        User owner = makeUser("einv-noauto-" + runId(), Role.USER);
        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        business.setGstIn("29ABCDE1234F1Z5");
        businessRepository.save(business);
        EInvoiceConfigRequest request = new EInvoiceConfigRequest();
        request.setGstin("");
        request.setProvider("MOCK");
        request.setEnvironment("SANDBOX");
        request.setEnabled(true);
        request.setAutoRegister(false); // the difference
        request.setClientId("c");
        request.setClientSecret("s");
        configService.saveConfig(owner.getId(), owner.getBusinessId(), request);

        String auth = bearerFor(owner);
        Long customerId = seedCustomer(owner.getBusinessId()).getId();
        String body = "{"
                + "\"customerId\":" + customerId + ","
                + "\"invoiceType\":\"TAX_INVOICE\","
                + "\"invoiceDate\":\"2026-10-25\","
                + "\"dueDate\":\"2026-11-09\","
                + "\"placeOfSupply\":\"Karnataka\","
                + "\"items\":[{\"sno\":1,\"itemName\":\"Pipe\",\"qty\":1,\"rate\":1000,\"gstPercentage\":18}]"
                + "}";

        mockMvc.perform(post("/api/invoices").header("Authorization", auth)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.einvoiceStatus").doesNotExist());

        assertTrue(registrationRepo.findAll().stream()
                .noneMatch(r -> r.getBusinessId().equals(owner.getBusinessId())));
    }

    @Autowired
    private com.insideinvoice.einvoice.repository.EInvoiceRegistrationRepository registrationRepo;
}
