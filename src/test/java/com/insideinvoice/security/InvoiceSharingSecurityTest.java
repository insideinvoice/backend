package com.insideinvoice.security;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.share.dto.ShareLinkResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization, sharing-lifecycle and exposure tests for invoice data.
 * Every scenario asserts the HTTP contract an attacker would actually observe.
 */
class InvoiceSharingSecurityTest extends BaseIntegrationTest {

    private User alice;
    private User bob;
    private Invoice aliceInvoice;
    private Invoice bobInvoice;

    @BeforeEach
    void seedTwoBusinesses() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        alice = makeUser("alice-" + run, Role.USER);
        bob = makeUser("bob-" + run, Role.USER);
        aliceInvoice = seedInvoice(alice);
        bobInvoice = seedInvoice(bob);
    }

    // ---------- existing surfaces: anonymous + cross-tenant ----------

    @Test
    @DisplayName("Anonymous access to private invoice list is 401")
    void anonymousInvoiceListIs401() throws Exception {
        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Anonymous access to private invoice detail is 401")
    void anonymousInvoiceDetailIs401() throws Exception {
        mockMvc.perform(get("/api/invoices/" + aliceInvoice.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cross-tenant invoice detail returns uniform 404 with no data leak")
    void crossTenantInvoiceDetailIs404() throws Exception {
        mockMvc.perform(get("/api/invoices/" + bobInvoice.getId())
                        .header("Authorization", bearerFor(alice)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message", not(containsString(bobInvoice.getInvoiceNumber()))));
    }

    @Test
    @DisplayName("Regression (BOLA): payments-by-invoice enforces invoice ownership")
    void crossTenantPaymentsByInvoiceIs404() throws Exception {
        seedPayment(bobInvoice, bob.getBusinessId(), new BigDecimal("50.00"));

        mockMvc.perform(get("/api/payments/by-invoice/" + bobInvoice.getId())
                        .header("Authorization", bearerFor(alice)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        // and no payment can be planted onto someone else's invoice either
        mockMvc.perform(post("/api/payments")
                        .header("Authorization", bearerFor(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\":" + bobInvoice.getId() + ",\"amount\":10.00,"
                                + "\"paymentMode\":\"UPI\",\"paymentDate\":\"2026-01-01\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cross-tenant share management returns uniform 404")
    void crossTenantShareManagementIs404() throws Exception {
        String token = invoiceShareService
                .createOrRetrieve(bobInvoice.getId(), bob.getBusinessId()).getToken();

        mockMvc.perform(post("/api/invoices/" + bobInvoice.getId() + "/share")
                        .header("Authorization", bearerFor(alice)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/invoices/" + bobInvoice.getId() + "/share")
                        .header("Authorization", bearerFor(alice)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/invoices/" + bobInvoice.getId() + "/share/regenerate")
                        .header("Authorization", bearerFor(alice)))
                .andExpect(status().isNotFound());

        // bob's link must be untouched by alice's attempts
        mockMvc.perform(get("/api/public/invoices/" + token))
                .andExpect(status().isOk());
    }

    // ---------- share lifecycle ----------

    @Test
    @DisplayName("Create -> revoke -> create rotates the token; revoked token never resolves again")
    void revokeInvalidatesTokenAndNextCreateMintsNew() throws Exception {
        String token1 = createShare(alice, aliceInvoice);

        publicGet(token1).andExpect(status().isOk());

        mockMvc.perform(delete("/api/invoices/" + aliceInvoice.getId() + "/share")
                        .header("Authorization", bearerFor(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        publicGet(token1).andExpect(status().isNotFound());

        String token2 = createShare(alice, aliceInvoice);
        assertThat(token2).isNotEqualTo(token1);
        publicGet(token1).andExpect(status().isNotFound()); // revoked token stays dead
        publicGet(token2).andExpect(status().isOk());

        // second create on an active link returns the SAME token (stable URL)
        String tokenAgain = createShare(alice, aliceInvoice);
        assertThat(tokenAgain).isEqualTo(token2);
    }

    @Test
    @DisplayName("Regenerate issues a new token and kills the previous one")
    void regenerateRotatesToken() throws Exception {
        String token1 = createShare(alice, aliceInvoice);

        mockMvc.perform(post("/api/invoices/" + aliceInvoice.getId() + "/share/regenerate")
                        .header("Authorization", bearerFor(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.enabled").value(true));

        publicGet(token1).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Concurrent share creation converges to exactly one token")
    void concurrentCreateYieldsSingleToken() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<ShareLinkResponse>> futures = java.util.stream.IntStream.range(0, 4)
                    .<Future<ShareLinkResponse>>mapToObj(i -> pool.submit(() ->
                            invoiceShareService.createOrRetrieve(aliceInvoice.getId(), alice.getBusinessId())))
                    .collect(Collectors.toList());
            Set<String> tokens = futures.stream()
                    .map(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(ShareLinkResponse::getToken)
                    .collect(Collectors.toSet());
            assertThat(tokens).hasSize(1);
            publicGet(tokens.iterator().next()).andExpect(status().isOk());
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- public endpoint hardening ----------

    @Test
    @DisplayName("Public detail returns allowlisted fields only - no ids, no share token, no audit fields")
    void publicDetailExposesNoInternalIdentifiers() throws Exception {
        String token = createShare(alice, aliceInvoice);

        mockMvc.perform(get("/api/public/invoices/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.invoiceNumber").value(aliceInvoice.getInvoiceNumber()))
                .andExpect(jsonPath("$.data.buyer").exists())
                .andExpect(jsonPath("$.data.seller").exists())
                .andExpect(jsonPath("$.data.items[0].itemName").value("Test Item"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.shareToken").doesNotExist())
                .andExpect(jsonPath("$.data.createdBy").doesNotExist())
                .andExpect(jsonPath("$.data.businessId").doesNotExist())
                .andExpect(jsonPath("$.data.customerId").doesNotExist())
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());

        String body = mockMvc.perform(get("/api/public/invoices/" + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(token);   // response must not echo the secret back
        assertThat(body).doesNotContain("businessId");
        assertThat(body).doesNotContain("shareToken");
    }

    @Test
    @DisplayName("Malformed, junk and unknown tokens are indistinguishable 404s")
    void invalidTokensAreUniform404() throws Exception {
        String[] junk = {
                "short",
                "AAAAAAAAAAAAAAAAAAAAAA",                       // 22 chars, never issued
                "not-a-real-token-but-long-enough-to-be-22",     // charset-valid, unknown
                "0123456789012345678901234567890123456789012",  // 43 chars, unknown
        };
        String referenceMessage = null;
        for (String t : junk) {
            String message = mockMvc.perform(get("/api/public/invoices/" + t))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andReturn().getResponse().getContentAsString();
            assertThat(message).doesNotContain(t);
            String parsed = com.jayway.jsonpath.JsonPath.read(message, "$.message");
            if (referenceMessage == null) {
                referenceMessage = parsed;
            } else {
                assertThat(parsed).isEqualTo(referenceMessage);
            }
        }
        // revoked endpoint: same message as unknown token (detail + pdf)
        String token = createShare(alice, aliceInvoice);
        mockMvc.perform(delete("/api/invoices/" + aliceInvoice.getId() + "/share")
                        .header("Authorization", bearerFor(alice)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/invoices/" + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(referenceMessage));
        mockMvc.perform(get("/api/public/invoices/" + token + "/pdf"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(referenceMessage));
    }

    @Test
    @DisplayName("Privacy/cache headers present on both 200 and 404 public responses")
    void publicResponsesCarryPrivacyHeaders() throws Exception {
        String token = createShare(alice, aliceInvoice);

        mockMvc.perform(get("/api/public/invoices/" + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        mockMvc.perform(get("/api/public/invoices/" + "AAAAAAAAAAAAAAAAAAAAAA"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    @DisplayName("Public PDF endpoint returns a real, non-cached PDF stream")
    void publicPdfEndpointServesRealPdf() throws Exception {
        String token = createShare(alice, aliceInvoice);

        byte[] body = mockMvc.perform(get("/api/public/invoices/" + token + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body.length).isGreaterThan(500);
        String magic = new String(body, 0, 4);
        assertThat(magic).isEqualTo("%PDF");
    }

    // ---------- companion controls (not authorization, verified as such) ----------

    @Test
    @DisplayName("CORS: unknown origins rejected, configured frontend origin allowed")
    void corsIsRestrictedToConfiguredOrigins() throws Exception {
        String token = createShare(alice, aliceInvoice);

        mockMvc.perform(options("/api/public/invoices/" + token)
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/public/invoices/" + token)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    @DisplayName("Function-level: non-admin gets 403 on admin API; actuator limited to health/info")
    void functionLevelAccessIsRestricted() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearerFor(alice)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String createShare(User owner, Invoice invoice) throws Exception {
        String body = mockMvc.perform(post("/api/invoices/" + invoice.getId() + "/share")
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.token");
    }

    private org.springframework.test.web.servlet.ResultActions publicGet(String token) throws Exception {
        return mockMvc.perform(get("/api/public/invoices/" + token));
    }
}
