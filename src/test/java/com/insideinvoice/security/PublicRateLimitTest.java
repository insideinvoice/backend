package com.insideinvoice.security;

import com.insideinvoice.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Own Spring context with deliberately tiny limits so throttling can be observed
 * deterministically within a single test method (windows are time-based, so ordering
 * across methods would be fragile). Tokens here are charset-valid but unknown - the
 * guard runs before the controller, so every request counts the same.
 */
@TestPropertySource(properties = {
        "app.public.rate-limit.requests-per-minute-per-ip=6",
        "app.public.rate-limit.requests-per-minute-per-token=3"
})
class PublicRateLimitTest extends BaseIntegrationTest {

    @Test
    @DisplayName("429 after per-token and per-IP budget exhaustion, with privacy headers intact")
    void rateLimitReturnsGeneric429() throws Exception {
        String tokenA = "R".repeat(24);

        // 3 requests inside the per-token budget: normal 404s (unknown token)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/public/invoices/" + tokenA))
                    .andExpect(status().isNotFound());
        }

        // 4th request: per-token window (3) exhausted -> 429 (fresh tokens used below
        // prove the rejection is token-scoped, not IP-scoped, at this point)
        String limitedBody = mockMvc.perform(get("/api/public/invoices/" + tokenA))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."))
                .andReturn().getResponse().getContentAsString();
        assertThat(limitedBody).doesNotContain(tokenA); // never echo the token in throttle responses

        // ip count so far: 4 of 6. Fresh tokens: two allowed (5th, 6th request), 7th hits the IP window.
        mockMvc.perform(get("/api/public/invoices/" + "S".repeat(24)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/invoices/" + "T".repeat(24)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/invoices/" + "U".repeat(24)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));
    }
}
