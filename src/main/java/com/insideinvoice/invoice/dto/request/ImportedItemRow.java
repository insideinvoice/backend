package com.insideinvoice.invoice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One line item as read from the Invoice Items sheet of an import workbook.
 * Only the inputs a human would type (qty, rate, GST%) are trusted; money
 * columns (taxable / tax / total) are recomputed server-side by
 * {@code InvoiceMapper} so a hand-edited or stale spreadsheet can never skew
 * totals.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportedItemRow {

    /** 1-based Excel row number, echoed in the validation report. */
    private int rowNumber;

    private String itemName;

    private String hsn;

    private String unit;

    private BigDecimal qty;

    private BigDecimal rate;

    private BigDecimal gstPercentage;

    /** Errors collected for this item row (empty when valid). */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public boolean isValid() {
        return errors == null || errors.isEmpty();
    }
}
