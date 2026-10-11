package com.insideinvoice.invoice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The validation report for a single imported invoice, returned by both the
 * dry-run and the commit. `valid` is false when the row has blocking errors;
 * `action` records what the commit did (or would do) with it — CREATE, SKIP
 * (duplicate invoice number), or BLOCKED (invalid). Totals shown are the
 * server-recomputed values, never the spreadsheet's own money cells.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportRowReport {

    private int rowNumber;

    private String invoiceNumber;

    private String customerName;

    private boolean valid;

    /** CREATE | SKIP | BLOCKED */
    private String action;

    private BigDecimal subtotal;

    private BigDecimal taxAmount;

    private BigDecimal grandTotal;

    private int itemCount;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
