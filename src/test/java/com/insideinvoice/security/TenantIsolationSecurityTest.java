package com.insideinvoice.security;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.deliverychallan.repository.DeliveryChallanRepository;
import com.insideinvoice.labels.repository.HazmatLabelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant isolation red-team: a user from business B must NEVER be able to read,
 * update, delete, list, or even detect an invoice belonging to business A — and
 * must get 403 on every admin endpoint. Isolation is enforced server-side from
 * the JWT-resolved user's businessId; no request parameter can move it.
 */
class TenantIsolationSecurityTest extends BaseIntegrationTest {

    @Autowired protected com.insideinvoice.deliverychallan.repository.DeliveryChallanRepository deliveryChallanRepository;
    @Autowired protected com.insideinvoice.labels.repository.HazmatLabelRepository hazmatLabelRepository;

    private static String runId() {
        return Long.toHexString(System.currentTimeMillis() % 1000000);
    }

    @Test
    @DisplayName("User from another business gets 404 (not leak, not 403-detectable) on every invoice op")
    void otherTenantCannotTouchInvoice() throws Exception {
        User owner = makeUser("iso-a-" + runId(), Role.USER);
        User attacker = makeUser("iso-b-" + runId(), Role.USER);
        Invoice invoice = seedInvoice(owner);
        String putBody = "{"
                + "\"invoiceNumber\":\"HACKED-" + runId() + "\","
                + "\"invoiceType\":\"TAX_INVOICE\","
                + "\"customerId\":" + invoice.getCustomerId() + ","
                + "\"invoiceDate\":\"2026-01-01\","
                + "\"dueDate\":\"2026-01-15\","
                + "\"status\":\"PENDING\","
                + "\"items\":[{\"itemName\":\"X\",\"qty\":1,\"rate\":100,\"gstPercentage\":18}]"
                + "}";

        mockMvc.perform(get("/api/invoices/" + invoice.getId()).header("Authorization", bearerFor(attacker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/invoices/" + invoice.getId())
                        .header("Authorization", bearerFor(attacker))
                        .contentType("application/json")
                        .content(putBody))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/invoices/" + invoice.getId()).header("Authorization", bearerFor(attacker)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Another business's invoice never appears in the attacker's invoice list")
    void otherTenantInvoiceAbsentFromList() throws Exception {
        User owner = makeUser("iso-c-" + runId(), Role.USER);
        User attacker = makeUser("iso-d-" + runId(), Role.USER);
        Invoice invoice = seedInvoice(owner);

        String body = mockMvc.perform(get("/api/invoices?page=0&size=50")
                        .header("Authorization", bearerFor(attacker)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(invoice.getInvoiceNumber());
    }

    @Test
    @DisplayName("Attacker gets 403 on admin endpoints — including reading another user's invoice")
    void nonAdminBlockedFromAdminEndpoints() throws Exception {
        User owner = makeUser("iso-e-" + runId(), Role.USER);
        User attacker = makeUser("iso-f-" + runId(), Role.USER);
        Invoice invoice = seedInvoice(owner);

        mockMvc.perform(get("/api/admin/invoices/" + invoice.getId())
                        .header("Authorization", bearerFor(attacker)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearerFor(attacker)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/invoices/" + invoice.getId())
                        .header("Authorization", bearerFor(attacker)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Same-isolation for customers: 404 on another business's customer")
    void otherTenantCannotReadCustomer() throws Exception {
        User owner = makeUser("iso-g-" + runId(), Role.USER);
        User attacker = makeUser("iso-h-" + runId(), Role.USER);
        var customer = seedCustomer(owner.getBusinessId());

        mockMvc.perform(get("/api/customers/" + customer.getId())
                        .header("Authorization", bearerFor(attacker)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Owner still has full access to their own invoice (isolation didn't over-block)")
    void ownerStillAccessesOwnInvoice() throws Exception {
        User owner = makeUser("iso-i-" + runId(), Role.USER);
        Invoice invoice = seedInvoice(owner);

        mockMvc.perform(get("/api/invoices/" + invoice.getId())
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").value(invoice.getInvoiceNumber()));
    }

    @Test
    @DisplayName("Delivery challans are tenant-isolated: other business gets 404 on read and delete")
    void otherTenantCannotTouchDeliveryChallan() throws Exception {
        User owner = makeUser("iso-j-" + runId(), Role.USER);
        User attacker = makeUser("iso-k-" + runId(), Role.USER);
        com.insideinvoice.customer.entity.Customer customer = seedCustomer(owner.getBusinessId());
        com.insideinvoice.deliverychallan.entity.DeliveryChallan challan =
                deliveryChallanRepository.save(com.insideinvoice.deliverychallan.entity.DeliveryChallan.builder()
                        .businessId(owner.getBusinessId())
                        .customerId(customer.getId())
                        .challanNumber("DC-" + runId())
                        .challanDate(java.time.LocalDate.now())
                        .createdBy(owner.getId())
                        .items(new java.util.ArrayList<>())
                        .build());

        mockMvc.perform(get("/api/delivery-challans/" + challan.getId())
                        .header("Authorization", bearerFor(attacker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/delivery-challans/" + challan.getId())
                        .header("Authorization", bearerFor(attacker)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Hazmat labels are tenant-isolated: other business gets 404 on read and delete")
    void otherTenantCannotTouchHazmatLabel() throws Exception {
        User owner = makeUser("iso-l-" + runId(), Role.USER);
        User attacker = makeUser("iso-m-" + runId(), Role.USER);
        com.insideinvoice.labels.entity.HazmatLabel label = hazmatLabelRepository.save(
                com.insideinvoice.labels.entity.HazmatLabel.builder()
                        .businessId(owner.getBusinessId())
                        .labelNumber("HL-" + runId())
                        .properShippingName("Test dangerous good")
                        .hazardClass("3")
                        .packageCount(1)
                        .transportMode(com.insideinvoice.labels.enums.TransportMode.ROAD)
                        .labelType(com.insideinvoice.labels.enums.HazmatLabelType.CLASS_DIAMOND)
                        .overpack(Boolean.FALSE)
                        .marinePollutant(Boolean.FALSE)
                        .labelSize("4X6")
                        .colorMode(com.insideinvoice.labels.enums.ColorMode.COLOR)
                        .status(com.insideinvoice.labels.enums.LabelStatus.DRAFT)
                        .build());

        mockMvc.perform(get("/api/labels/hazmat/" + label.getId())
                        .header("Authorization", bearerFor(attacker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/labels/hazmat/" + label.getId())
                        .header("Authorization", bearerFor(attacker)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Customers are tenant-isolated on update and delete for other businesses")
    void otherTenantCannotUpdateOrDeleteCustomer() throws Exception {
        User owner = makeUser("iso-n-" + runId(), Role.USER);
        User attacker = makeUser("iso-o-" + runId(), Role.USER);
        var customer = seedCustomer(owner.getBusinessId());

        mockMvc.perform(put("/api/customers/" + customer.getId())
                        .header("Authorization", bearerFor(attacker))
                        .contentType("application/json")
                        .content("{\"name\":\"HACKED\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/customers/" + customer.getId())
                        .header("Authorization", bearerFor(attacker)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Admin CAN edit any user's invoice — but only via the admin route (403 for everyone else)")
    void adminCanEditAnyUsersInvoice() throws Exception {
        User owner = makeUser("iso-p-" + runId(), Role.USER);
        User admin = makeUser("iso-q-" + runId(), Role.ADMIN);
        User attacker = makeUser("iso-r-" + runId(), Role.USER);
        Invoice invoice = seedInvoice(owner);
        String stamp = runId();
        String body = "{"
                + "\"invoiceNumber\":\"ADM-" + stamp + "\","
                + "\"invoiceType\":\"TAX_INVOICE\","
                + "\"customerId\":" + invoice.getCustomerId() + ","
                + "\"invoiceDate\":\"2026-01-02\","
                + "\"dueDate\":\"2026-01-16\","
                + "\"status\":\"PENDING\","
                + "\"items\":[{\"itemName\":\"Admin Edited\",\"qty\":1,\"rate\":100,\"gstPercentage\":18}]"
                + "}";

        // Normal user on the ADMIN route: forbidden.
        mockMvc.perform(put("/api/admin/invoices/" + invoice.getId())
                        .header("Authorization", bearerFor(attacker))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());

        // Admin on the SAME route: full edit of another user's invoice.
        mockMvc.perform(put("/api/admin/invoices/" + invoice.getId())
                        .header("Authorization", bearerFor(admin))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/invoices/" + invoice.getId())
                        .header("Authorization", bearerFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").value("ADM-" + stamp));
    }
}
