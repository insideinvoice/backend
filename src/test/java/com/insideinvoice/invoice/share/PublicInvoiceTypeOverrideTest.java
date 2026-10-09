package com.insideinvoice.invoice.share;

import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceType;
import com.insideinvoice.invoice.share.dto.PublicInvoiceResponse;
import com.insideinvoice.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for the {@code ?type=} document override on public share links: one token
 * can render either document, blank falls back to the stored type and a typo is rejected
 * rather than silently showing the wrong document.
 */
class PublicInvoiceTypeOverrideTest {

    // ---------- PublicInvoiceService.normalizeType ----------

    @Test
    void blankOverrideMeansUseTheStoredType() {
        assertThat(PublicInvoiceService.normalizeType(null)).isNull();
        assertThat(PublicInvoiceService.normalizeType("")).isNull();
        assertThat(PublicInvoiceService.normalizeType("   ")).isNull();
    }

    @Test
    void acceptedValuesAreCanonicalisedUppercase() {
        assertThat(PublicInvoiceService.normalizeType("PROFORMA_INVOICE")).isEqualTo("PROFORMA_INVOICE");
        assertThat(PublicInvoiceService.normalizeType("proforma_invoice")).isEqualTo("PROFORMA_INVOICE");
        assertThat(PublicInvoiceService.normalizeType("tax_invoice")).isEqualTo("TAX_INVOICE");
        assertThat(PublicInvoiceService.normalizeType(" Tax_Invoice ")).isEqualTo("TAX_INVOICE");
    }

    @Test
    void unknownValueIsRejectedWithA400() {
        assertThatThrownBy(() -> PublicInvoiceService.normalizeType("INVOICE"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("TAX_INVOICE")
                .hasMessageContaining("PROFORMA_INVOICE");
        assertThatThrownBy(() -> PublicInvoiceService.normalizeType("tax")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PublicInvoiceService.normalizeType("<script>")).isInstanceOf(BadRequestException.class);
    }

    // ---------- PublicInvoiceAssembler ----------

    @Test
    void overrideChangesTheRenderedDocumentNotTheInvoice() {
        Invoice invoice = invoice(InvoiceType.TAX_INVOICE);
        PublicInvoiceResponse withOverride = new PublicInvoiceAssembler()
                .assemble(new ResolvedPublicInvoice(invoice, null, null), "PROFORMA_INVOICE");

        assertThat(withOverride.getInvoiceType()).isEqualTo("PROFORMA_INVOICE");
        assertThat(withOverride.getInvoiceNumber()).isEqualTo("INV-1");
        assertThat(invoice.getInvoiceType()).isEqualTo(InvoiceType.TAX_INVOICE); // stored row untouched
    }

    @Test
    void missingOrUnknownOverrideFallsBackToTheStoredType() {
        Invoice storedProforma = invoice(InvoiceType.PROFORMA_INVOICE);
        ResolvedPublicInvoice resolved = new ResolvedPublicInvoice(storedProforma, null, null);
        PublicInvoiceAssembler assembler = new PublicInvoiceAssembler();

        assertThat(assembler.assemble(resolved).getInvoiceType()).isEqualTo("PROFORMA_INVOICE");
        assertThat(assembler.assemble(resolved, null).getInvoiceType()).isEqualTo("PROFORMA_INVOICE");
        // defence in depth: never trusts a value that normalizeType would have rejected
        assertThat(assembler.assemble(resolved, "something-else").getInvoiceType())
                .isEqualTo("PROFORMA_INVOICE");
    }

    private static Invoice invoice(InvoiceType type) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-1");
        invoice.setInvoiceType(type);
        invoice.setItems(List.of()); // streamed unconditionally by the assembler
        invoice.setSubtotal(BigDecimal.TEN);
        return invoice;
    }
}
