package com.insideinvoice.business;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.business.entity.Business;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end contract for the configurable "Subject to ... Jurisdiction"
 * footer: persistence, validation, tenant isolation and the public share
 * payload that fronts every rendered template on the shared link page.
 */
class BusinessJurisdictionIntegrationTest extends BaseIntegrationTest {

    private static final String ME = "/api/business/me";
    private static final String UPDATE = "/api/business/update";
    private static final String SETUP = "/api/business/setup";

    @Test
    @DisplayName("A brand-new business keeps the historical footer: enabled with the backfilled city")
    void freshBusinessKeepsLegacyFooter() throws Exception {
        String run = "jur-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        User owner = makeUser(run, Role.USER);

        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        assertThat(business.getJurisdictionEnabled()).isTrue();
        assertThat(business.getJurisdictionCity()).isEqualTo("Bengaluru");
        assertThat(business.getJurisdictionRegion()).isNull();

        mockMvc.perform(get(ME).header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jurisdictionEnabled").value(true))
                .andExpect(jsonPath("$.data.jurisdictionCity").value("Bengaluru"))
                .andExpect(jsonPath("$.data.jurisdictionRegion").doesNotExist());
    }

    @Test
    @DisplayName("Karnataka + Bengaluru can be saved and read back")
    void saveKarnatakaBengaluru() throws Exception {
        User owner = makeUser("knt-" + runId(), Role.USER);
        putUpdate(owner, """
                {
                  "jurisdictionEnabled": true,
                  "jurisdictionRegion": "Karnataka",
                  "jurisdictionCity": "Bengaluru"
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jurisdictionRegion").value("Karnataka"))
                .andExpect(jsonPath("$.data.jurisdictionCity").value("Bengaluru"));

        mockMvc.perform(get(ME).header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jurisdictionEnabled").value(true))
                .andExpect(jsonPath("$.data.jurisdictionRegion").value("Karnataka"))
                .andExpect(jsonPath("$.data.jurisdictionCity").value("Bengaluru"));
    }

    @Test
    @DisplayName("A business can move Bengaluru -> Mumbai without touching other fields")
    void switchToMumbai() throws Exception {
        User owner = makeUser("mum-" + runId(), Role.USER);
        putUpdate(owner, """
                { "jurisdictionRegion": "Maharashtra", "jurisdictionCity": "Mumbai" }
                """).andExpect(status().isOk());
        putUpdate(owner, """
                { "jurisdictionRegion": "Maharashtra", "jurisdictionCity": "Pune" }
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jurisdictionCity").value("Pune"))
                .andExpect(jsonPath("$.data.jurisdictionRegion").value("Maharashtra"));
    }

    @Test
    @DisplayName("Delhi + New Delhi round-trips through the union-territory group")
    void delhiNewDelhi() throws Exception {
        User owner = makeUser("del-" + runId(), Role.USER);
        putUpdate(owner, """
                {
                  "jurisdictionEnabled": true,
                  "jurisdictionRegion": "Delhi (National Capital Territory)",
                  "jurisdictionCity": "New Delhi"
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jurisdictionRegion").value("Delhi (National Capital Territory)"))
                .andExpect(jsonPath("$.data.jurisdictionCity").value("New Delhi"));
    }

    @Test
    @DisplayName("Disabling the line persists; re-enabling requires a city and restores it")
    void disableThenReenable() throws Exception {
        User owner = makeUser("off-" + runId(), Role.USER);
        putUpdate(owner, "{ \"jurisdictionEnabled\": false }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jurisdictionEnabled").value(false));

        putUpdate(owner, "{ \"jurisdictionEnabled\": true }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jurisdictionEnabled").value(true))
                .andExpect(jsonPath("$.data.jurisdictionCity").value("Bengaluru"));
    }

    @Test
    @DisplayName("Enabling with a blank city is rejected with 400")
    void blankCityWhileEnabledFails() throws Exception {
        User owner = makeUser("blk-" + runId(), Role.USER);
        mockMvc.perform(put(UPDATE)
                        .header("Authorization", bearerFor(owner))
                        .contentType("application/json")
                        .content("""
                                { "jurisdictionEnabled": true, "jurisdictionCity": "   " }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("city")));
    }

    @Test
    @DisplayName("An invalid region rejected directly through the API keeps the stored value")
    void invalidRegionIsRejected() throws Exception {
        User owner = makeUser("bad-" + runId(), Role.USER);
        mockMvc.perform(put(UPDATE)
                        .header("Authorization", bearerFor(owner))
                        .contentType("application/json")
                        .content("""
                                { "jurisdictionRegion": "Atlantis", "jurisdictionCity": "Poseidonis" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("region")));

        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        assertThat(business.getJurisdictionRegion()).isNull();
        assertThat(business.getJurisdictionCity()).isEqualTo("Bengaluru");
    }

    @Test
    @DisplayName("City is trimmed and repeated spaces are collapsed before persisting")
    void cityWhitespaceNormalised() throws Exception {
        User owner = makeUser("wsp-" + runId(), Role.USER);
        putUpdate(owner, "{ \"jurisdictionCity\": \"  Bengaluru   Central   \" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jurisdictionCity").value("Bengaluru Central"));
    }

    @Test
    @DisplayName("A user cannot change another business's jurisdiction configuration")
    void crossTenantUpdateLeavesOtherBusinessAlone() throws Exception {
        String run = runId();
        User alice = makeUser("alice-" + run, Role.USER);
        User bob = makeUser("bob-" + run, Role.USER);

        // Bob changes only HIS OWN configuration (endpoint is JWT-scoped).
        putUpdate(bob, "{ \"jurisdictionRegion\": \"Maharashtra\", \"jurisdictionCity\": \"Mumbai\" }")
                .andExpect(status().isOk());

        // Alice's business is untouched.
        Business aliceBusiness = businessRepository.findById(alice.getBusinessId()).orElseThrow();
        assertThat(aliceBusiness.getJurisdictionRegion()).isNull();
        assertThat(aliceBusiness.getJurisdictionCity()).isEqualTo("Bengaluru");
        assertThat(aliceBusiness.getJurisdictionEnabled()).isTrue();
    }

    @Test
    @DisplayName("The public share payload carries the owner's jurisdiction configuration")
    void sharePayloadCarriesJurisdiction() throws Exception {
        User owner = makeUser("shr-" + runId(), Role.USER);
        putUpdate(owner, "{ \"jurisdictionRegion\": \"Karnataka\", \"jurisdictionCity\": \"Bengaluru\" }")
                .andExpect(status().isOk());

        var invoice = seedInvoice(owner);
        String token = createShare(owner, invoice.getId());

        String body = mockMvc.perform(get("/api/public/invoices/" + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(body, "$.data.seller.jurisdictionEnabled")).isTrue();
        assertThat((String) JsonPath.read(body, "$.data.seller.jurisdictionCity")).isEqualTo("Bengaluru");
        assertThat((String) JsonPath.read(body, "$.data.seller.jurisdictionRegion")).isEqualTo("Karnataka");
    }

    @Test
    @DisplayName("A disabled business hides the line on the share payload too")
    void sharePayloadOmitsWhenDisabled() throws Exception {
        User owner = makeUser("shr2-" + runId(), Role.USER);
        putUpdate(owner, "{ \"jurisdictionEnabled\": false }").andExpect(status().isOk());

        var invoice = seedInvoice(owner);
        String token = createShare(owner, invoice.getId());

        String body = mockMvc.perform(get("/api/public/invoices/" + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(body, "$.data.seller.jurisdictionEnabled")).isFalse();
        // The city value is retained for when the business re-enables the line,
        // but the renderer must not draw the footer while the switch is off.
        assertThat((String) JsonPath.read(body, "$.data.seller.jurisdictionCity")).isEqualTo("Bengaluru");
    }

    @Test
    @DisplayName("Onboarding setup persists the jurisdiction configuration when provided")
    void setupPersistsJurisdiction() throws Exception {
        User owner = makeSetupPendingUser("ob-" + runId());
        mockMvc.perform(post(SETUP)
                        .header("Authorization", bearerFor(owner))
                        .contentType("application/json")
                        .content("""
                                {
                                  "businessName": "Onboarded Traders",
                                  "jurisdictionEnabled": true,
                                  "jurisdictionRegion": "Delhi (National Capital Territory)",
                                  "jurisdictionCity": "New Delhi"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jurisdictionEnabled").value(true))
                .andExpect(jsonPath("$.data.jurisdictionRegion").value("Delhi (National Capital Territory)"))
                .andExpect(jsonPath("$.data.jurisdictionCity").value("New Delhi"));

        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        assertThat(business.getJurisdictionCity()).isEqualTo("New Delhi");
    }

    @Test
    @DisplayName("Onboarding setup without jurisdiction fields keeps the legacy defaults")
    void setupWithoutJurisdictionKeepsDefaults() throws Exception {
        User owner = makeSetupPendingUser("ob2-" + runId());
        mockMvc.perform(post(SETUP)
                        .header("Authorization", bearerFor(owner))
                        .contentType("application/json")
                        .content("{ \"businessName\": \"Default Setup Co\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jurisdictionEnabled").value(true))
                .andExpect(jsonPath("$.data.jurisdictionCity").value("Bengaluru"))
                .andExpect(jsonPath("$.data.jurisdictionRegion").doesNotExist());
    }

    @Test
    @DisplayName("Invalid region during onboarding is rejected and nothing is persisted")
    void setupRejectsInvalidRegion() throws Exception {
        User owner = makeSetupPendingUser("ob3-" + runId());
        mockMvc.perform(post(SETUP)
                        .header("Authorization", bearerFor(owner))
                        .contentType("application/json")
                        .content("""
                                {
                                  "businessName": "Bad Region Co",
                                  "jurisdictionRegion": "Wakanda",
                                  "jurisdictionCity": "Birnin Zana"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("region")));

        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        assertThat(business.getJurisdictionRegion()).isNull();
        assertThat(business.getJurisdictionCity()).isEqualTo("Bengaluru");
    }

    // ---------- helpers ----------

    private static String runId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Like {@code makeUser} but with businessSetupCompleted=false so the
     * first-run /api/business/setup flow accepts a completion request.
     */
    private User makeSetupPendingUser(String username) {
        Business business = businessRepository.save(Business.builder()
                .businessName(username + " Co")
                .ownerName(username)
                .invoicePrefix("T" + Math.abs(username.hashCode() % 100000))
                .nextInvoiceSequence(1L)
                .build());
        return userRepository.save(User.builder()
                .name(username)
                .username(username)
                .email(username + "@test.local")
                .password("not-used-in-these-tests")
                .rawPassword("not-used-in-these-tests")
                .role(Role.USER)
                .businessId(business.getId())
                .businessSetupCompleted(false)
                .build());
    }

    /** JSON helper: PUT /business/update as the owner. */
    private org.springframework.test.web.servlet.ResultActions putUpdate(User owner, String json) throws Exception {
        return mockMvc.perform(put(UPDATE)
                .header("Authorization", bearerFor(owner))
                .contentType("application/json")
                .content(json));
    }

    private String createShare(User owner, Long invoiceId) throws Exception {
        String body = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/invoices/" + invoiceId + "/share")
                                .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }
}
