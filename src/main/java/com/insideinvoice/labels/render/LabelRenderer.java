package com.insideinvoice.labels.render;

import com.insideinvoice.labels.enums.LabelPreset;

import java.io.IOException;

/**
 * One shipping-label preset = one layout class (spec Part B).
 * Implementations are stateless; the same input must yield the same PDF
 * (apart from document dates, which callers control via the spec).
 */
public interface LabelRenderer {

    LabelPreset preset();

    /** Renders a complete PDF: one page per label (or sheet per cell batch). */
    byte[] render(ShippingLabelSpec spec) throws IOException;
}
