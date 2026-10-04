package com.insideinvoice.labels.render;

import com.insideinvoice.labels.enums.LabelPreset;

/** Amazon FBA box label: Standard Carrier layout with an emphasised FC block. */
public class AmazonFbaRenderer extends StandardCarrierRenderer {

    @Override
    public LabelPreset preset() {
        return LabelPreset.AMAZON_FBA_4X6;
    }

    @Override
    protected boolean emphasizeFc() {
        return true;
    }
}
