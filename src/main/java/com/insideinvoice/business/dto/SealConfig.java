package com.insideinvoice.business.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configurable company seal/stamp placement.
 *
 * <p>One entry per seal variant. All measurements are millimetres in page
 * coordinates: {@code sizeMm} is the round seal's diameter (or the rubber
 * stamp's width — its height follows the stamp's 2.5:1 artwork aspect),
 * {@code rightMm}/{@code bottomMm} are the gaps between the seal and the
 * page's right/bottom edge, and {@code tilt} is the hand-stamped rotation in
 * degrees. Null fields mean "keep the current value / built-in default".</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SealConfig {

    /**
     * Whether the seal is stamped on every page (true) or only the final page
     * — the one carrying the grand total and the signature band (false).
     * Null means "keep the current value / built-in default (false)".
     */
    private Boolean allPages;

    private Variant round;
    private Variant stamp;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Variant {
        private Double sizeMm;
        private Double tilt;
        private Double rightMm;
        private Double bottomMm;

        /**
         * Ink colour as a #RRGGBB hex string. Null means "keep the current
         * value / built-in default" so older payloads stay compatible.
         */
        private String color;
    }
}
