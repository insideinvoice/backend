package com.insideinvoice.labels.hazmat;

/** Renders a hazmat spec to a print-ready PDF. */
public interface HazmatRenderer {

    byte[] render(HazmatSpec spec) throws Exception;
}
