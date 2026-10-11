package com.insideinvoice.invoice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate result of an invoice import (dry-run or commit). `dryRun` is true
 * when the request only validated without writing. `imported` counts rows
 * actually created (0 on a dry-run); `skipped` counts duplicate invoice numbers
 * left untouched; `blocked` counts rows with blocking errors. Per-row detail is
 * in `rows`.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportReport {

    private boolean dryRun;

    private int totalRows;

    private int imported;

    private int skipped;

    private int blocked;

    @Builder.Default
    private List<ImportRowReport> rows = new ArrayList<>();
}
