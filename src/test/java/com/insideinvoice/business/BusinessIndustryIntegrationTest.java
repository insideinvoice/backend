package com.insideinvoice.business;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.industry.Industry;
import com.insideinvoice.invoice.entity.Invoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end contract for industry selection: onboarding persistence, settings
 * changes, validation, tenant isolation and the guarantee that hiding a field
 * never deletes data.
 */
class BusinessIndustryIntegrationTest extends BaseIntegrationTest {

    private User newbie;

    @BeforeEach
    void seedUserNeedingSetup() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        newbie = makeUserRequiringSetup("setup-" + run);
    }

    /** A freshly signed-up user whose onboarding is still pending. */
    private User makeUserRequiringSetup(String username) {
        Business business = businessRepository.save(Business.builder()
                .businessName(username + " Co")
                .ownerName(username)
                .invoicePrefix("S" + Math.abs(username.hashCode() % 100000))
                .nextInvoiceSequence(1L)
                .build());
        return userRepository.save(User.builder()
                .name(username)
                .username(username)
                .email(username + "@test.local")
                .password("pw")
                .rawPassword("pw")
                .role(Role.USER)
                .businessId(business.getId())
                .businessSetupCompleted(false)
                .build());
    }

    // ---------------------------------------------------------------- onboarding

    @Test
    @DisplayName("Onboarding persists the selected industry against the business")
    void setupPersistsSelectedIndustry() throws Exception {
        mockMvc.perform(post("/api/business/setup")
                        .header("Authorization", bearerFor(newbie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessName\":\"Acme Consulting\",\"industry\":\"PROFESSIONAL_SERVICES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("PROFESSIONAL_SERVICES"))
                .andExpect(jsonPath("$.data.industryConfig.id").value("PROFESSIONAL_SERVICES"))
                .andExpect(jsonPath("$.data.industryConfig.hiddenFields", hasItem("deliveryNote")))
                .andExpect(jsonPath("$.data.industryConfig.documents.deliveryChallan").value(false));

        // ...and it survives a reload (login → profile fetch path)
        mockMvc.perform(get("/api/business/me")
                        .header("Authorization", bearerFor(newbie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("PROFESSIONAL_SERVICES"));
    }

    @Test
    @DisplayName("Onboarding without an industry falls back to OTHER (legacy path)")
    void setupWithoutIndustryFallsBackToOther() throws Exception {
        mockMvc.perform(post("/api/business/setup")
                        .header("Authorization", bearerFor(newbie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessName\":\"Legacy Traders\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("OTHER"))
                .andExpect(jsonPath("$.data.industryConfig.hiddenFields", hasSize(0)))
                .andExpect(jsonPath("$.data.industryConfig.documents.deliveryChallan").value(true))
                .andExpect(jsonPath("$.data.industryConfig.documents.shippingLabel").value(true))
                .andExpect(jsonPath("$.data.industryConfig.documents.hazmatLabel").value(true));
    }

    @Test
    @DisplayName("A business created before this feature resolves to the OTHER profile")
    void legacyBusinessStillReadable() {
        // makeUser() builds a business without an industry, exactly like every
        // row that existed before V27.
        User legacy = makeUser("legacy-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        Business business = businessRepository.findById(legacy.getBusinessId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(business.getIndustry()).isEqualTo("OTHER");
    }

    // ------------------------------------------------------------------ settings

    @Test
    @DisplayName("Settings can change the industry without recreating the business")
    void settingsCanChangeIndustry() throws Exception {
        Long businessId = newbie.getBusinessId();

        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(newbie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessName\":\"Same Biz\",\"industry\":\"TRANSPORT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(businessId))
                .andExpect(jsonPath("$.data.industry").value("TRANSPORT"))
                .andExpect(jsonPath("$.data.industryConfig.documents.hazmatLabel").value(true))
                .andExpect(jsonPath("$.data.industryConfig.labels['deliveryNote']").value("Consignment / LR No."));

        // no new business, same id, config follows
        mockMvc.perform(get("/api/business/me")
                        .header("Authorization", bearerFor(newbie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(businessId))
                .andExpect(jsonPath("$.data.industry").value("TRANSPORT"))
                .andExpect(jsonPath("$.data.industryConfig.id").value("TRANSPORT"));
    }

    @Test
    @DisplayName("Rental and healthcare configs expose labels for V29 extended fields")
    void extendedFieldLabelsAreServedWithTheConfig() throws Exception {
        // The frontend prefers business.industryConfig over its local profile,
        // so the served labels/placeholders must cover the extended fields too.
        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(newbie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"RENTAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industryConfig.labels['agreementNumber']").value("Rental Agreement No."))
                .andExpect(jsonPath("$.data.industryConfig.labels['periodStart']").value("Rental Period Start"))
                .andExpect(jsonPath("$.data.industryConfig.placeholders['agreementNumber']").value("e.g. RA-2026-5566"));

        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(newbie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"HEALTHCARE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industryConfig.labels['patientReference']").value("Patient / Customer ID"))
                .andExpect(jsonPath("$.data.industryConfig.labels['referringDoctor']").value("Referring Doctor"));
    }

    @Test
    @DisplayName("An update that omits industry leaves the current one untouched")
    void updateWithoutIndustryKeepsCurrentSelection() throws Exception {
        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(newbie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"FOOD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("FOOD"));

        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(newbie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessName\":\"Renamed Only\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("FOOD"))
                .andExpect(jsonPath("$.data.businessName").value("Renamed Only"));
    }

    @Test
    @DisplayName("Unknown industry values are rejected with a field error")
    void unknownIndustryIsRejected() throws Exception {
        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(newbie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"BITCOIN_MINING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.fieldErrors.industry").exists());

        mockMvc.perform(post("/api/business/setup")
                        .header("Authorization", bearerFor(newbie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessName\":\"X\",\"industry\":\"<script>\"}"))
                .andExpect(status().isBadRequest());

        // and the stored value never changes
        mockMvc.perform(get("/api/business/me")
                        .header("Authorization", bearerFor(newbie)))
                .andExpect(jsonPath("$.data.industry").value("OTHER"));
    }

    // ----------------------------------------------------------- tenant isolation

    @Test
    @DisplayName("One tenant changing industry never affects another tenant")
    void industryChangeIsTenantScoped() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        User alice = makeUser("alice-" + run, Role.USER);
        User bob = makeUser("bob-" + run, Role.USER);

        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"CONSTRUCTION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("CONSTRUCTION"));

        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"EDUCATION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("EDUCATION"));

        // alice still sees her own selection and her own configuration
        mockMvc.perform(get("/api/business/me")
                        .header("Authorization", bearerFor(alice)))
                .andExpect(jsonPath("$.data.industry").value("CONSTRUCTION"))
                .andExpect(jsonPath("$.data.industryConfig.documents.deliveryChallan").value(true));

        mockMvc.perform(get("/api/business/me")
                        .header("Authorization", bearerFor(bob)))
                .andExpect(jsonPath("$.data.industry").value("EDUCATION"))
                .andExpect(jsonPath("$.data.industryConfig.documents.deliveryChallan").value(false));
    }

    @Test
    @DisplayName("A client cannot inject an industry by id — only its own business is written")
    void industryCannotBeSetByPathOrBodyTrick() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        User alice = makeUser("alice-" + run, Role.USER);
        User bob = makeUser("bob-" + run, Role.USER);
        Long bobBusinessId = bob.getBusinessId();

        // alice's update request has no way to address bob's business
        mockMvc.perform(put("/api/business/update?businessId=" + bobBusinessId)
                        .header("Authorization", bearerFor(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"HEALTHCARE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(alice.getBusinessId()))
                .andExpect(jsonPath("$.data.industry").value("HEALTHCARE"));

        Business bobBusiness = businessRepository.findById(bobBusinessId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(bobBusiness.getIndustry()).isEqualTo("OTHER");
    }

    // ------------------------------------------------------------------ catalogue

    @Test
    @DisplayName("The industry catalogue serves every published profile")
    void industryCatalogue() throws Exception {
        mockMvc.perform(get("/api/industries")
                        .header("Authorization", bearerFor(newbie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(Industry.values().length)))
                .andExpect(jsonPath("$.data[0].id").value("TRADING"))
                .andExpect(jsonPath("$.data[3].id").value("PROFESSIONAL_SERVICES"))
                .andExpect(jsonPath("$.data[12].id").value("OTHER"))
                .andExpect(jsonPath("$.data[0].name").value("Trading, Retail & Distribution"))
                .andExpect(jsonPath("$.data[0].description", not("")));
    }

    @Test
    @DisplayName("The industry catalogue requires authentication")
    void industryCatalogueIsNotAnonymous() throws Exception {
        mockMvc.perform(get("/api/industries"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------- data preservation on hide

    @Test
    @DisplayName("Fields hidden by an industry still persist on invoices (no data stripping)")
    void hiddenFieldsAreNeverStrippedByTheBackend() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        User consultant = makeUser("svc-" + run, Role.USER);
        Long businessId = consultant.getBusinessId();

        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(consultant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"PROFESSIONAL_SERVICES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industryConfig.hiddenFields", hasItem("deliveryNote")));

        var customer = seedCustomer(businessId);
        String body = """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-24",
                  "placeOfSupply": "Karnataka",
                  "deliveryNote": "DN-should-survive",
                  "dispatchDocNumber": "DISP-should-survive",
                  "dispatchedThrough": "Blue Dart",
                  "termsOfDelivery": "Express",
                  "destination": "Maharashtra",
                  "items": [{"sno":1,"itemName":"Advisory","hsn":"998311","qty":1,"rate":5000,"gstPercentage":18}]
                }
                """.formatted(customer.getId());

        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(consultant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deliveryNote").value("DN-should-survive"))
                .andExpect(jsonPath("$.data.destination").value("Maharashtra"));

        // read the stored row back through the API: nothing was dropped
        String list = mockMvc.perform(get("/api/invoices?size=50")
                        .header("Authorization", bearerFor(consultant)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(list)
                .contains("DN-should-survive")
                .contains("DISP-should-survive")
                .contains("Blue Dart");
    }

    @Test
    @DisplayName("Public share responses carry the industry so templates can suppress the same fields")
    void publicShareCarriesIndustry() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        User consultant = makeUser("svc-" + run, Role.USER);
        Invoice invoice = seedInvoice(consultant);

        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(consultant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"PROFESSIONAL_SERVICES\"}"))
                .andExpect(status().isOk());

        String shareToken = mockMvc.perform(post("/api/invoices/" + invoice.getId() + "/share")
                        .header("Authorization", bearerFor(consultant)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/public/invoices/" + shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seller.industry").value("PROFESSIONAL_SERVICES"))
                .andExpect(jsonPath("$.data.seller.businessName").exists());
    }

    @Test
    @DisplayName("Invoice totals and tax are untouched by industry configuration")
    void totalsUnaffectedByIndustry() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        User trader = makeUser("tot-" + run, Role.USER);

        mockMvc.perform(put("/api/business/update")
                        .header("Authorization", bearerFor(trader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"FOOD\"}"))
                .andExpect(status().isOk());

        var customer = seedCustomer(trader.getBusinessId());
        String body = """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-24",
                  "items": [{"sno":1,"itemName":"Cake","hsn":"19059090","qty":2,"rate":100,"gstPercentage":18}]
                }
                """.formatted(customer.getId());

        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(trader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal").value(200.00))
                .andExpect(jsonPath("$.data.taxAmount").value(36.00))
                .andExpect(jsonPath("$.data.grandTotal").value(236.00));
    }
}
