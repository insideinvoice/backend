package com.insideinvoice.invoice.share;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.business.entity.Business;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public share PDF honours the persisted tax mode — Phase 3 of
 * PLAN -> TESTS -> IMPLEMENT -> VERIFY (written first, expected to fail).
 *
 * <p>The share PDF previously always drew CGST/SGST halves. Contract:
 * IGST invoices print a single IGST row and never the split columns, split
 * invoices keep CGST+SGST exactly as before, and a legacy NULL mode falls
 * back to the existing place-of-supply heuristic (inter-state -> IGST).
 * Grand totals are identical in every case.</p>
 */
class PublicInvoicePdfTaxModeTest extends BaseIntegrationTest {

    private void setBusinessState(User owner, String state) {
        Business business = businessRepository.findById(owner.getBusinessId()).orElseThrow();
        business.setState(state);
        businessRepository.save(business);
    }

    private String createInvoice(User owner, String placeOfSupply, String taxMode) throws Exception {
        String taxField = taxMode == null ? "" : ", \"taxMode\": \"" + taxMode + "\"";
        String json = """
                {
                  "customerId": %d,
                  "invoiceType": "TAX_INVOICE",
                  "invoiceDate": "2026-10-10",
                  "dueDate": "2026-10-24",
                  "placeOfSupply": %s%s,
                  "items": [{"sno":1,"itemName":"Service","qty":1,"rate":100,"gstPercentage":18}]
                }
                """.formatted(seedCustomer(owner.getBusinessId()).getId(),
                placeOfSupply == null ? "null" : "\"" + placeOfSupply + "\"", taxField);
        String created = mockMvc.perform(post("/api/invoices")
                        .header("Authorization", bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).path("data").path("id").asText();
    }

    /** Creates a share link and returns the extracted text of the share PDF. */
    private String sharePdfText(User owner, String invoiceId) throws Exception {
        String share = mockMvc.perform(post("/api/invoices/" + invoiceId + "/share")
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(share).path("data").path("token").asText();

        byte[] pdf = mockMvc.perform(get("/api/public/invoices/" + token + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    @DisplayName("Stored IGST: PDF prints the IGST row, never CGST/SGST — totals unchanged")
    void igstInvoicePrintsIgstOnly() throws Exception {
        User owner = makeUser("pdfi-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setBusinessState(owner, "Karnataka");
        // deliberately intra-state supply: the stored selection must still win
        String text = sharePdfText(owner, createInvoice(owner, "Karnataka", "IGST"));

        assertThat(text).contains("IGST");
        assertThat(text).doesNotContain("CGST");
        assertThat(text).doesNotContain("SGST");
        assertThat(text).contains("118.00");
        assertThat(text).contains("18.00"); // the full tax on one row
    }

    @Test
    @DisplayName("Stored CGST_SGST: PDF keeps both split legs and shows no IGST")
    void splitInvoicePrintsBothLegs() throws Exception {
        User owner = makeUser("pdfs-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setBusinessState(owner, "Karnataka");
        String text = sharePdfText(owner, createInvoice(owner, "Karnataka", "CGST_SGST"));

        assertThat(text).contains("CGST");
        assertThat(text).contains("SGST");
        assertThat(text).doesNotContain("IGST");
        assertThat(text).contains("9.00"); // 9.00 per leg
        assertThat(text).contains("118.00");
    }

    @Test
    @DisplayName("Legacy NULL mode (inter-state supply): heuristic keeps rendering IGST like the email does")
    void legacyNullModeFallsBackToHeuristic() throws Exception {
        User owner = makeUser("pdfl-" + UUID.randomUUID().toString().substring(0, 8), Role.USER);
        setBusinessState(owner, "Karnataka");
        String text = sharePdfText(owner, createInvoice(owner, "Maharashtra", null));

        assertThat(text).contains("IGST");
        assertThat(text).doesNotContain("CGST");
        assertThat(text).doesNotContain("SGST");
        assertThat(text).contains("118.00");
    }
}
