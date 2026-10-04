package com.insideinvoice.labels.hazmat;

import com.insideinvoice.labels.enums.HazmatLabelType;

/** Maps a {@link HazmatLabelType} to its renderer. */
public final class HazmatRenderers {

    private HazmatRenderers() {
    }

    public static HazmatRenderer forType(HazmatLabelType type) {
        return switch (type == null ? HazmatLabelType.CLASS_DIAMOND : type) {
            case CLASS_DIAMOND, PLACARD -> new ClassDiamondRenderer();
            case LITHIUM, LIMITED_QTY, EXCEPTED_QTY, ENV_HAZARD, ORIENTATION, CAO, OVERPACK
                    -> new MarkLabelRenderer();
        };
    }

    public static HazmatRenderer forSpec(HazmatSpec spec) {
        return forType(spec.effectiveType());
    }
}
