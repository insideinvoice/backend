package com.insideinvoice.labels.render;

import com.insideinvoice.labels.enums.LabelPreset;

/**
 * Return label: the Part B layout with a black "RETURN" banner and the
 * ship-from / ship-to blocks reversed (form captures the original shipment).
 */
public class ReturnLabelRenderer extends StandardCarrierRenderer {

    @Override
    public LabelPreset preset() {
        return LabelPreset.RETURN_LABEL;
    }

    @Override
    public byte[] render(ShippingLabelSpec spec) throws java.io.IOException {
        ShippingLabelSpec reversed = spec.toBuilder()
                .preset(LabelPreset.RETURN_LABEL)
                .shipFrom(spec.getShipTo())
                .shipTo(spec.getShipFrom())
                .build();
        return super.render(reversed);
    }

    @Override
    protected double bannerHeightMm() {
        return 8;
    }

    @Override
    protected String shipToCaption() {
        return "RETURN TO:";
    }
}
