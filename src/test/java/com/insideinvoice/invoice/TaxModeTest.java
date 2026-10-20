package com.insideinvoice.invoice;

import com.insideinvoice.invoice.entity.TaxMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for the invoice tax mode (CGST_SGST vs IGST) — Phase 3 of
 * PLAN -> TESTS -> IMPLEMENT -> VERIFY: written before TaxMode ships.
 *
 * <p>Covers parsing, the legal default derived from supplier state vs the
 * legally applicable place of supply, display resolution for legacy rows
 * (stored selection wins, heuristic fills gaps), the save-time default that
 * never guesses, and the consistency check the UI/validation flow uses.</p>
 */
class TaxModeTest {

    // ------------------------------------------------------------- parsing

    @Test
    @DisplayName("fromRaw accepts the two legal values in any case/spacing, rejects the rest")
    void parsesLegalValuesOnly() {
        assertThat(TaxMode.fromRaw("IGST")).isEqualTo(TaxMode.IGST);
        assertThat(TaxMode.fromRaw("cgst_sgst")).isEqualTo(TaxMode.CGST_SGST);
        assertThat(TaxMode.fromRaw(" IgSt ")).isEqualTo(TaxMode.IGST);
        assertThat(TaxMode.fromRaw(null)).isNull();
        assertThat(TaxMode.fromRaw("")).isNull();
        assertThat(TaxMode.fromRaw("   ")).isNull();
        assertThat(TaxMode.fromRaw("GST")).isNull();
        assertThat(TaxMode.fromRaw("UTGST")).isNull();
        assertThat(TaxMode.fromRaw("HST")).isNull();
    }

    // ------------------------------------------------------- determination

    @Test
    @DisplayName("Intra-state (same state, incl. a UT vs itself) -> CGST_SGST")
    void intraStateDefaultsToSplit() {
        assertThat(TaxMode.determine("Karnataka", "Karnataka")).isEqualTo(TaxMode.CGST_SGST);
        assertThat(TaxMode.determine("karnataka ", "KARNATAKA")).isEqualTo(TaxMode.CGST_SGST);
        // Union Territory: the state leg simply prints as UTGST; the mode stays split.
        assertThat(TaxMode.determine("Delhi", "Delhi")).isEqualTo(TaxMode.CGST_SGST);
        assertThat(TaxMode.determine("Puducherry", "Puducherry")).isEqualTo(TaxMode.CGST_SGST);
    }

    @Test
    @DisplayName("Inter-state -> IGST")
    void interStateDefaultsToIgst() {
        assertThat(TaxMode.determine("Karnataka", "Maharashtra")).isEqualTo(TaxMode.IGST);
        assertThat(TaxMode.determine("Uttar Pradesh", "Delhi")).isEqualTo(TaxMode.IGST);
    }

    @Test
    @DisplayName("Missing supplier state or place of supply -> indeterminate, never a guess")
    void indeterminateWhenInformationMissing() {
        assertThat(TaxMode.determine(null, "Karnataka")).isNull();
        assertThat(TaxMode.determine("Karnataka", null)).isNull();
        assertThat(TaxMode.determine("", "Karnataka")).isNull();
        assertThat(TaxMode.determine("Karnataka", "  ")).isNull();
        assertThat(TaxMode.determine(null, null)).isNull();
    }

    // -------------------------------------------------- display resolution

    @Test
    @DisplayName("Stored selection always wins for rendering — never silently changed")
    void storedModeWinsOverHeuristic() {
        assertThat(TaxMode.resolveForDisplay(TaxMode.IGST, "Karnataka", "Karnataka")).isEqualTo(TaxMode.IGST);
        assertThat(TaxMode.resolveForDisplay(TaxMode.CGST_SGST, "Karnataka", "Maharashtra")).isEqualTo(TaxMode.CGST_SGST);
    }

    @Test
    @DisplayName("Legacy rows (no stored mode) fall back to the legal heuristic, else split")
    void legacyRowsUseHeuristicThenSplit() {
        assertThat(TaxMode.resolveForDisplay(null, "Karnataka", "Maharashtra")).isEqualTo(TaxMode.IGST);
        assertThat(TaxMode.resolveForDisplay(null, "Karnataka", "Karnataka")).isEqualTo(TaxMode.CGST_SGST);
        assertThat(TaxMode.resolveForDisplay(null, null, null)).isEqualTo(TaxMode.CGST_SGST);
        assertThat(TaxMode.resolveForDisplay(null, "Karnataka", null)).isEqualTo(TaxMode.CGST_SGST);
    }

    // -------------------------------------------------------- save defaults

    @Test
    @DisplayName("Create default derives the mode, or stays null when undeterminable")
    void saveDefaultNeverGuesses() {
        assertThat(TaxMode.defaultForSave("Karnataka", "Karnataka")).isEqualTo(TaxMode.CGST_SGST);
        assertThat(TaxMode.defaultForSave("Karnataka", "Maharashtra")).isEqualTo(TaxMode.IGST);
        assertThat(TaxMode.defaultForSave(null, "Karnataka")).isNull();
        assertThat(TaxMode.defaultForSave("Karnataka", null)).isNull();
    }

    // --------------------------------------------------------- consistency

    @Test
    @DisplayName("Consistency: match or indeterminate is OK, conflict is not")
    void consistencyCheck() {
        assertThat(TaxMode.isConsistent(TaxMode.CGST_SGST, "Karnataka", "Karnataka")).isTrue();
        assertThat(TaxMode.isConsistent(TaxMode.IGST, "Karnataka", "Maharashtra")).isTrue();
        assertThat(TaxMode.isConsistent(TaxMode.IGST, "Karnataka", "Karnataka")).isFalse();
        assertThat(TaxMode.isConsistent(TaxMode.CGST_SGST, "Karnataka", "Maharashtra")).isFalse();
        assertThat(TaxMode.isConsistent(TaxMode.IGST, null, null)).isTrue();
        assertThat(TaxMode.isConsistent(TaxMode.IGST, "Karnataka", null)).isTrue();
    }
}
