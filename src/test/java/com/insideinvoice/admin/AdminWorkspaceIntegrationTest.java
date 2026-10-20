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
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(mine.getId()));
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
    @DisplayName("Customer delete cascades: payments and the customer's invoices go with it")
    void deleteCustomerBlockedByInvoices() throws Exception {
        User owner = makeUser("wsdi-" + runId(), Role.USER);
        User admin = makeUser("wsdiadm-" + runId(), Role.ADMIN);
        Invoice invoice = seedInvoice(owner);
        seedPayment(invoice, owner.getBusinessId(), new BigDecimal("50.00"));

        mockMvc.perform(delete("/api/admin/customers/" + invoice.getCustomerId())
                        .header("Authorization", bearerFor(admin)))
                .andExpect(status().isOk());

        // Nothing dangling: cascade removed the invoices and their payments too.
        assertThat(invoiceRepository.findById(invoice.getId())).isEmpty();
        assertThat(paymentRepository.findByInvoiceId(invoice.getId())).isEmpty();
        assertThat(customerRepository.findById(invoice.getCustomerId())).isEmpty();
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

    @Test
    @DisplayName("Stats window activity: seeding an invoice raises users/invoices-in-last-30-days and daily rows stay 30 long")
    void statsDailyActivityTracksInvoiceActivity() throws Exception {
        User admin = makeUser("wsadm2-" + runId(), Role.ADMIN);

        double baseInvoices = extractStat(mockMvc.perform(get("/api/admin/stats")
                .header("Authorization", bearerFor(admin)))
                .andExpect(status().isOk())
                .andReturn(), "invoicesLast30Days");
        double baseUsers = extractStat(mockMvc.perform(get("/api/admin/stats")
                .header("Authorization", bearerFor(admin)))
                .andExpect(status().isOk())
                .andReturn(), "activeUsersLast30Days");

        User owner = makeUser("wsact-" + runId(), Role.USER);
        seedInvoice(owner);

        String body = mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", bearerFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyActivity.length()").value(30))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).path("data").path("invoicesLast30Days").asDouble())
                .isGreaterThanOrEqualTo(baseInvoices + 1);
        assertThat(objectMapper.readTree(body).path("data").path("activeUsersLast30Days").asDouble())
                .isGreaterThanOrEqualTo(baseUsers + 1);
        // today's bucket exists and includes the new invoice
        assertThat(objectMapper.readTree(body).path("data").path("dailyActivity").get(29).path("date").asText())
                .isEqualTo(java.time.LocalDate.now().toString());
        assertThat(objectMapper.readTree(body).path("data").path("dailyActivity").get(29).path("invoices").asInt())
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("User list flags who is active: generated an invoice in the last 30 days")
    void userListExposesActivityStatus() throws Exception {
        User active = makeUser("wsact1-" + runId(), Role.USER);
        User idle = makeUser("wsidle-" + runId(), Role.USER);
        User admin = makeUser("wsadm3-" + runId(), Role.ADMIN);
        seedInvoice(active);

        String body = mockMvc.perform(get("/api/admin/users")
                        // Paged endpoint: pull the whole first window so the
                        // freshly-fixture users above are always inside it.
                        .param("size", "1000")
                        .header("Authorization", bearerFor(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode users = objectMapper.readTree(body).path("data").path("content");
        com.fasterxml.jackson.databind.JsonNode activeNode = findUserBy(users, active.getUsername());
        com.fasterxml.jackson.databind.JsonNode idleNode = findUserBy(users, idle.getUsername());

        assertThat(activeNode.path("active").asBoolean()).isTrue();
        assertThat(activeNode.path("lastActivityAt").isNull()).isFalse();
        assertThat(idleNode.path("active").asBoolean()).isFalse();
        assertThat(idleNode.path("lastActivityAt").isNull()).isTrue();
    }

    private com.fasterxml.jackson.databind.JsonNode findUserBy(com.fasterxml.jackson.databind.JsonNode users, String username) {
        for (com.fasterxml.jackson.databind.JsonNode u : users) {
            if (username.equals(u.path("username").asText())) return u;
        }
        throw new AssertionError("user not found: " + username);
    }

    private double extractStat(org.springframework.test.web.servlet.MvcResult result, String field) {
        try {
            return objectMapper.readTree(result.getResponse().getContentAsString())
                    .path("data").path(field).asDouble();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String runId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
