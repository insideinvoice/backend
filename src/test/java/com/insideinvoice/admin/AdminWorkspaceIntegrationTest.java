package com.insideinvoice.admin;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.invoice.entity.Invoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end contract for the admin per-user workspace: the admin can inspect
 * and operate on ANY user's business data (customers, invoices) and create
 * invoices on their behalf — with the exact same business rules merchants are
 * subject to (FK delete guards, tenant-scoped customer checks, invoice
 * numbering), never a raw bypass.
 */
class AdminWorkspaceIntegrationTest extends BaseIntegrationTest {

    private static String bizPath(Long businessId, String resource) {
        return "/api/admin/businesses/" + businessId + "/" + resource;
    }

    @Test
    @DisplayName("Workspace customer list returns ONLY that business's customers")
    void businessCustomersAreScoped() throws Exception {
        User owner = makeUser("ws-" + runId(), Role.USER);
        User admin = makeUser("wsadm-" + runId(), Role.ADMIN);
        User other = makeUser("wsb-" + runId(), Role.USER);

        com.insideinvoice.customer.entity.Customer mine = seedCustomer(owner.getBusinessId());
        seedCustomer(other.getBusinessId());

        mockMvc.perform(get(bizPath(owner.getBusinessId(), "customers")).header("Authorization", bearerFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(mine.getId()));
    }

    @Test
    @DisplayName("Admin can delete a user's customer that has no invoices")
    void deleteCustomerWithoutInvoices() throws Exception {
        User owner = makeUser("wsd-" + runId(), Role.USER);
        User admin = makeUser("wsdadm-" + runId(), Role.ADMIN);
        com.insideinvoice.customer.entity.Customer customer = seedCustomer(owner.getBusinessId());

        mockMvc.perform(delete("/api/admin/customers/" + customer.getId()).header("Authorization", bearerFor(admin)))
                .andExpect(status().isOk());

        assertThat(customerRepository.findById(customer.getId())).isEmpty();
    }

    @Test
    @DisplayName("Admin customer delete is blocked while invoices reference the customer (same guard as merchants)")
    void deleteCustomerBlockedByInvoices() throws Exception {
        User owner = makeUser("wsdi-" + runId(), Role.USER);
        User admin = makeUser("wsdiadm-" + runId(), Role.ADMIN);
        Invoice invoice = seedInvoice(owner);

        mockMvc.perform(delete("/api/admin/customers/" + invoice.getCustomerId())
                        .header("Authorization", bearerFor(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("invoices exist")));

        assertThat(customerRepository.findById(invoice.getCustomerId())).isPresent();
    }

    @Test
    @DisplayName("Admin can delete any user's invoice")
    void deleteInvoiceAsAdmin() throws Exception {
        User owner = makeUser("wsp-" + runId(), Role.USER);
        User admin = makeUser("wspadm-" + runId(), Role.ADMIN);
        Invoice invoice = seedInvoice(owner);

        mockMvc.perform(delete("/api/admin/invoices/" + invoice.getId()).header("Authorization", bearerFor(admin)))
                .andExpect(status().isOk());

        assertThat(invoiceRepository.findById(invoice.getId())).isEmpty();
    }

    @Test
    @DisplayName("Admin invoice delete respects the payments guard with an actionable 400")
    void deleteInvoiceBlockedByPayments() throws Exception {
        User owner = makeUser("wsq-" + runId(), Role.USER);
        User admin = makeUser("wsqadm-" + runId(), Role.ADMIN);
        Invoice invoice = seedInvoice(owner);
        seedPayment(invoice, owner.getBusinessId(), new BigDecimal("100.00"));

        mockMvc.perform(delete("/api/admin/invoices/" + invoice.getId())
                        .header("Authorization", bearerFor(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("payments")));

        assertThat(invoiceRepository.findById(invoice.getId())).isPresent();
    }

    @Test
    @DisplayName("Admin can create an invoice on behalf of a user (numbered in that business's series)")
    void createInvoiceOnBehalf() throws Exception {
        User owner = makeUser("wsc-" + runId(), Role.USER);
        User admin = makeUser("wscadm-" + runId(), Role.ADMIN);
        com.insideinvoice.customer.entity.Customer customer = seedCustomer(owner.getBusinessId());

        String body = String.format("""
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-19",
                  "dueDate": "2026-11-18",
                  "placeOfSupply": "Karnataka",
                  "items": [
                    {
                      "itemName": "Service call",
                      "qty": 2,
                      "rate": 500,
                      "gstPercentage": 18
                    }
                  ]
                }
                """, customer.getId());

        mockMvc.perform(post(bizPath(owner.getBusinessId(), "invoices"))
                        .header("Authorization", bearerFor(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.grandTotal").value(1180.00));
    }

    @Test
    @DisplayName("On-behalf creation for an unknown business is a 404")
    void createInvoiceUnknownBusiness() throws Exception {
        User admin = makeUser("wsa-" + runId(), Role.ADMIN);

        mockMvc.perform(post("/api/admin/businesses/999999/invoices")
                        .header("Authorization", bearerFor(admin))
                        .contentType("application/json")
                        .content("""
                                {
                                  "customerId": 1,
                                  "invoiceType": "TAX_INVOICE",
                                  "invoiceDate": "2026-10-19",
                                  "dueDate": "2026-11-18",
                                  "items": [{"itemName": "x", "qty": 1, "rate": 10, "gstPercentage": 18}]
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Non-admin tokens are forbidden from the workspace admin operations")
    void nonAdminForbidden() throws Exception {
        User user = makeUser("wsu-" + runId(), Role.USER);

        mockMvc.perform(delete("/api/admin/invoices/1").header("Authorization", bearerFor(user)))
                .andExpect(status().isForbidden());
    }

    private static String runId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
