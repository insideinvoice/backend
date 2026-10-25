package com.insideinvoice.einvoice;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Settings slice of GST e-invoicing: the enable/disable toggle, provider
 * configuration, credential handling and the connection test. Credentials are
 * write-only (never echoed), enabling without credentials is rejected, MOCK
 * can never run in production, and tenant isolation holds — one business can
 * never read another business's configuration.
 */
class EInvoiceConfigIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BusinessRepository businessRepository;

    private static String runId() {
        return Long.toHexString(System.currentTimeMillis() % 1000000);
    }

    private static String configBody(String gstin, String provider, String environment,
                                     boolean enabled, String clientId, String clientSecret) {
        return "{"
                + "\"gstin\":\"" + gstin + "\","
                + "\"provider\":\"" + provider + "\","
                + "\"environment\":\"" + environment + "\","
                + "\"enabled\":" + enabled + ","
                + "\"autoRegister\":false,"
                + "\"clientId\":\"" + clientId + "\","
                + "\"clientSecret\":\"" + clientSecret + "\""
                + "}";
    }

    @Test
    @DisplayName("No configuration yet: GET returns null data and the app keeps working")
    void emptyConfigReturnsNull() throws Exception {
        User owner = makeUser("einv-empty-" + runId(), Role.USER);
        mockMvc.perform(get("/api/einvoice/config").header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("Enable with credentials: saved, GSTIN defaulted from the business, credentials never echoed")
    void enableWithCredentials() throws Exception {
        User owner = makeUser("einv-on-" + runId(), Role.USER);
        // The business profile carries the GSTIN the config should default to.
        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        business.setGstIn("29ABCDE1234F1Z5");
        businessRepository.save(business);

        mockMvc.perform(put("/api/einvoice/config").header("Authorization", bearerFor(owner))
                        .contentType("application/json")
                        .content(configBody("", "MOCK", "SANDBOX", true, "cid-1", "secret-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.gstin").value("29ABCDE1234F1Z5"))
                .andExpect(jsonPath("$.data.provider").value("MOCK"))
                .andExpect(jsonPath("$.data.hasCredentials").value(true))
                // secrets / ciphertext must never appear anywhere in the response
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.data.credentialsEncrypted").doesNotExist());
    }

    @Test
    @DisplayName("Enabling without credentials is rejected — no half-configured automation")
    void enableWithoutCredentialsRejected() throws Exception {
        User owner = makeUser("einv-nocred-" + runId(), Role.USER);
        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        business.setGstIn("29ABCDE1234F1Z5");
        businessRepository.save(business);

        mockMvc.perform(put("/api/einvoice/config").header("Authorization", bearerFor(owner))
                        .contentType("application/json")
                        .content(configBody("", "IRIS_IRP", "SANDBOX", true, "", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("before enabling")));
    }

    @Test
    @DisplayName("MOCK provider can never be enabled in PRODUCTION")
    void mockInProductionRejected() throws Exception {
        User owner = makeUser("einv-mock-" + runId(), Role.USER);
        mockMvc.perform(put("/api/einvoice/config").header("Authorization", bearerFor(owner))
                        .contentType("application/json")
                        .content(configBody("29ABCDE1234F1Z5", "MOCK", "PRODUCTION", true, "c", "s")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("MOCK provider enables with NO credentials — it never talks to an IRP")
    void mockEnablesWithoutCredentials() throws Exception {
        User owner = makeUser("einv-mocknocred-" + runId(), Role.USER);
        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        business.setGstIn("29ABCDE1234F1Z5");
        businessRepository.save(business);

        mockMvc.perform(put("/api/einvoice/config").header("Authorization", bearerFor(owner))
                        .contentType("application/json")
                        .content(configBody("", "MOCK", "SANDBOX", true, "", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.hasCredentials").value(false));
    }

    @Test
    @DisplayName("Invalid GSTIN is rejected")
    void invalidGstinRejected() throws Exception {
        User owner = makeUser("einv-gstin-" + runId(), Role.USER);
        mockMvc.perform(put("/api/einvoice/config").header("Authorization", bearerFor(owner))
                        .contentType("application/json")
                        .content(configBody("NOT-A-GSTIN", "IRIS_IRP", "SANDBOX", false, "", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Connection test: MOCK sandbox reports CONNECTED (mock) and the status persists")
    void connectionTestPersistsStatus() throws Exception {
        User owner = makeUser("einv-test-" + runId(), Role.USER);
        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        business.setGstIn("29ABCDE1234F1Z5");
        businessRepository.save(business);
        String auth = bearerFor(owner);

        mockMvc.perform(put("/api/einvoice/config").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("", "MOCK", "SANDBOX", true, "cid", "sec")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/einvoice/config/test").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connectionStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.data.mock").value(true));

        mockMvc.perform(get("/api/einvoice/config").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connectionStatus").value("CONNECTED"));
    }

    @Test
    @DisplayName("Audit history records the configuration changes")
    void auditHistoryRecorded() throws Exception {
        User owner = makeUser("einv-audit-" + runId(), Role.USER);
        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        business.setGstIn("29ABCDE1234F1Z5");
        businessRepository.save(business);
        String auth = bearerFor(owner);

        mockMvc.perform(put("/api/einvoice/config").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("", "MOCK", "SANDBOX", true, "cid", "sec")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/einvoice/audit").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventType").value("CONFIG_SAVED"))
                .andExpect(jsonPath("$.data[0].providerResult").value("ENABLED"));
    }

    @Test
    @DisplayName("Tenant isolation: another business sees only its own configuration")
    void tenantIsolation() throws Exception {
        String id = runId();
        User first = makeUser("einv-t1-" + id, Role.USER);
        User second = makeUser("einv-t2-" + id, Role.USER);
        Business firstBusiness = businessRepository.findById(first.getBusinessId()).orElseThrow();
        firstBusiness.setGstIn("29ABCDE1234F1Z5");
        businessRepository.save(firstBusiness);

        mockMvc.perform(put("/api/einvoice/config").header("Authorization", bearerFor(first))
                        .contentType("application/json")
                        .content(configBody("", "MOCK", "SANDBOX", true, "cid", "sec")))
                .andExpect(status().isOk());

        // The second tenant finds NOTHING from the first tenant's setup.
        mockMvc.perform(get("/api/einvoice/config").header("Authorization", bearerFor(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/einvoice/audit").header("Authorization", bearerFor(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("Disable keeps the stored configuration (toggle only — nothing is deleted)")
    void disableKeepsConfiguration() throws Exception {
        User owner = makeUser("einv-off-" + runId(), Role.USER);
        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        business.setGstIn("29ABCDE1234F1Z5");
        businessRepository.save(business);
        String auth = bearerFor(owner);

        mockMvc.perform(put("/api/einvoice/config").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("", "MOCK", "SANDBOX", true, "cid", "sec")))
                .andExpect(status().isOk());

        // Disable by saving enabled=false — credentials blank keeps stored values.
        mockMvc.perform(put("/api/einvoice/config").header("Authorization", auth)
                        .contentType("application/json")
                        .content(configBody("", "MOCK", "SANDBOX", false, "", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.hasCredentials").value(true));
    }
}
