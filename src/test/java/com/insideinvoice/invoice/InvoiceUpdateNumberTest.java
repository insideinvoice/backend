package com.insideinvoice.invoice;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.invoice.entity.Invoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for the invoice-update contract: both frontend save flows
 * (InvoiceForm buildPayload, InvoiceView handleSave) only send invoiceNumber in
 * ghost mode, so an omitted number must keep the stored value instead of
 * overwriting it with null (historically a NOT NULL 500, then a guard 400).
 */
class InvoiceUpdateNumberTest extends BaseIntegrationTest {

    private User alice;
    private Invoice invoice;

    @BeforeEach
    void seed() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        alice = makeUser("alice-" + run, Role.USER);
        invoice = seedInvoice(alice);
    }

    private String updateBody(Long customerId, String invoiceNumber) {
        return "{"
                + (invoiceNumber == null ? "" : "\"invoiceNumber\":\"" + invoiceNumber + "\",")
                + "\"customerId\":" + customerId + ","
                + "\"invoiceType\":\"TAX_INVOICE\","
                + "\"invoiceDate\":\"2026-10-01\","
                + "\"dueDate\":\"2026-10-15\","
                + "\"status\":\"PENDING\","
                + "\"paymentMode\":\"CASH\","
                + "\"items\":[{\"sno\":1,\"itemName\":\"Test Item\",\"hsn\":\"998877\","
                + "\"qty\":2,\"rate\":100,\"gstPercentage\":18}]"
                + "}";
    }

    @Test
    @DisplayName("PUT without invoiceNumber keeps the stored number (normal-mode save)")
    void omittedInvoiceNumberKeepsStoredValue() throws Exception {
        String original = invoice.getInvoiceNumber();

        mockMvc.perform(put("/api/invoices/" + invoice.getId())
                        .header("Authorization", bearerFor(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(invoice.getCustomerId(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").value(original));

        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getInvoiceNumber())
                .isEqualTo(original);
    }

    @Test
    @DisplayName("PUT with blank invoiceNumber also keeps the stored number")
    void blankInvoiceNumberKeepsStoredValue() throws Exception {
        String original = invoice.getInvoiceNumber();

        mockMvc.perform(put("/api/invoices/" + invoice.getId())
                        .header("Authorization", bearerFor(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(invoice.getCustomerId(), "   ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").value(original));

        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getInvoiceNumber())
                .isEqualTo(original);
    }

    @Test
    @DisplayName("PUT with a new invoiceNumber (ghost mode) changes it")
    void providedInvoiceNumberUpdatesValue() throws Exception {
        String fresh = "INV-NEW-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(put("/api/invoices/" + invoice.getId())
                        .header("Authorization", bearerFor(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(invoice.getCustomerId(), fresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").value(fresh));

        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getInvoiceNumber())
                .isEqualTo(fresh);
    }

    @Test
    @DisplayName("PUT with a duplicate invoice number is 400 and changes nothing")
    void duplicateInvoiceNumberIsRejected() throws Exception {
        Invoice other = seedInvoice(alice);

        mockMvc.perform(put("/api/invoices/" + invoice.getId())
                        .header("Authorization", bearerFor(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(invoice.getCustomerId(), other.getInvoiceNumber())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already exists")));

        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getInvoiceNumber())
                .isEqualTo(invoice.getInvoiceNumber());
    }

    @Test
    @DisplayName("PUT with an over-length invoice number is 400 (column is VARCHAR(50))")
    void overlongInvoiceNumberIsRejected() throws Exception {
        String tooLong = "X".repeat(51);

        mockMvc.perform(put("/api/invoices/" + invoice.getId())
                        .header("Authorization", bearerFor(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(invoice.getCustomerId(), tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must not exceed 50")));

        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getInvoiceNumber())
                .isEqualTo(invoice.getInvoiceNumber());
    }
}
