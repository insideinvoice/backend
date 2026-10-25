package com.insideinvoice.duplicate;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Duplicate guard: phone/email/product-name uniqueness is PER BUSINESS.
 *
 * Two businesses (tenants) may each hold the same phone, email, product name or
 * HSN — the same person or product can exist on both sides. A repeat INSIDE one
 * business is rejected with 409 instead of silently merging or creating a twin.
 */
class DuplicateGuardIntegrationTest extends BaseIntegrationTest {

    private static String runId() {
        return Long.toHexString(System.currentTimeMillis() % 1000000);
    }

    private static String customer(String phone, String email) {
        return "{\"name\":\"Dup Guard Co\",\"phone\":\"" + phone + "\",\"email\":\"" + email + "\"}";
    }

    private static String product(String name, String hsn) {
        return "{\"name\":\"" + name + "\",\"hsn\":\"" + hsn + "\",\"rate\":100,\"gstPercentage\":18}";
    }

    @Test
    @DisplayName("Same business: second customer with the same phone -> 409")
    void sameBusinessRejectsDuplicatePhone() throws Exception {
        String id = runId();
        User owner = makeUser("dup-phone-" + id, Role.USER);
        String auth = bearerFor(owner);

        mockMvc.perform(post("/api/customers").header("Authorization", auth)
                        .contentType("application/json").content(customer("9876500001", "a-" + id + "@t.local")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/customers").header("Authorization", auth)
                        .contentType("application/json").content(customer("9876500001", "b-" + id + "@t.local")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already used by customer")));
    }

    @Test
    @DisplayName("Same business: same email in different case -> 409")
    void sameBusinessRejectsDuplicateEmailIgnoringCase() throws Exception {
        String id = runId();
        User owner = makeUser("dup-email-" + id, Role.USER);
        String auth = bearerFor(owner);

        mockMvc.perform(post("/api/customers").header("Authorization", auth)
                        .contentType("application/json").content(customer("9876500002", "Mixed.Case-" + id + "@t.local")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/customers").header("Authorization", auth)
                        .contentType("application/json").content(customer("9876500003", "mixed.case-" + id + "@T.LOCAL")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Multi-tenant: ANOTHER business may reuse the same phone and email")
    void anotherBusinessMayReusePhoneAndEmail() throws Exception {
        String id = runId();
        User first = makeUser("dup-a-" + id, Role.USER);
        User second = makeUser("dup-b-" + id, Role.USER);

        mockMvc.perform(post("/api/customers").header("Authorization", bearerFor(first))
                        .contentType("application/json").content(customer("9876500004", "shared-" + id + "@t.local")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/customers").header("Authorization", bearerFor(second))
                        .contentType("application/json").content(customer("9876500004", "shared-" + id + "@t.local")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Same business: update cannot take another customer's phone/email")
    void updateCannotStealAnotherCustomersIdentity() throws Exception {
        String id = runId();
        User owner = makeUser("dup-upd-" + id, Role.USER);
        String auth = bearerFor(owner);

        mockMvc.perform(post("/api/customers").header("Authorization", auth)
                        .contentType("application/json").content(customer("9876500005", "one-" + id + "@t.local")))
                .andExpect(status().isCreated());
        String created = mockMvc.perform(post("/api/customers").header("Authorization", auth)
                        .contentType("application/json").content(customer("9876500006", "two-" + id + "@t.local")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long secondId = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.data.id")).longValue();

        // Taking the first customer's phone is a conflict…
        mockMvc.perform(put("/api/customers/" + secondId).header("Authorization", auth)
                        .contentType("application/json").content("{\"name\":\"Dup Guard Co\",\"phone\":\"9876500005\"}"))
                .andExpect(status().isConflict());
        // …but keeping its OWN phone is fine.
        mockMvc.perform(put("/api/customers/" + secondId).header("Authorization", auth)
                        .contentType("application/json").content("{\"name\":\"Dup Guard Co\",\"phone\":\"9876500006\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Same business: duplicate product name and HSN -> 409 (formatting ignored)")
    void sameBusinessRejectsDuplicateProductNameAndHsn() throws Exception {
        String id = runId();
        User owner = makeUser("dup-prod-" + id, Role.USER);
        String auth = bearerFor(owner);

        mockMvc.perform(post("/api/products").header("Authorization", auth)
                        .contentType("application/json").content(product("Steel Table " + id, "9403 2090")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products").header("Authorization", auth)
                        .contentType("application/json").content(product("steel  table " + id, "111111")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already exists")));

        mockMvc.perform(post("/api/products").header("Authorization", auth)
                        .contentType("application/json").content(product("Other Item " + id, "9403-2090")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("HSN/SAC")));
    }

    @Test
    @DisplayName("Multi-tenant: another business may reuse the product name and HSN, and by-name lookup stays scoped")
    void anotherBusinessMayReuseProductNameAndHsn() throws Exception {
        String id = runId();
        User first = makeUser("dup-pa-" + id, Role.USER);
        User second = makeUser("dup-pb-" + id, Role.USER);
        String name = "Shared Widget " + id;

        mockMvc.perform(post("/api/products").header("Authorization", bearerFor(first))
                        .contentType("application/json").content(product(name, "94032090")))
                .andExpect(status().isCreated());

        String created = mockMvc.perform(post("/api/products").header("Authorization", bearerFor(second))
                        .contentType("application/json").content(product(name, "94032090")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long otherId = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.data.id")).longValue();

        // The lookup endpoint is business-scoped: it returns THIS business's product…
        mockMvc.perform(get("/api/products/by-name").param("name", name)
                        .header("Authorization", bearerFor(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(otherId));
    }

    @Test
    @DisplayName("Frontend pre-check: /customers/check finds the owner's record and 404s for the other business")
    void checkEndpointIsBusinessScoped() throws Exception {
        String id = runId();
        User first = makeUser("dup-ca-" + id, Role.USER);
        User second = makeUser("dup-cb-" + id, Role.USER);

        mockMvc.perform(post("/api/customers").header("Authorization", bearerFor(first))
                        .contentType("application/json").content(customer("9876500007", "chk-" + id + "@t.local")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/customers/check").header("Authorization", bearerFor(first))
                        .contentType("application/json").content("{\"phone\":\"9876500007\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("9876500007"));

        mockMvc.perform(post("/api/customers/check").header("Authorization", bearerFor(second))
                        .contentType("application/json").content("{\"phone\":\"9876500007\"}"))
                .andExpect(status().isNotFound());
    }
}
