package com.insideinvoice.labels.render;

import com.insideinvoice.labels.enums.LabelPreset;

import java.util.EnumMap;
import java.util.Map;

/** Registry mapping a {@link LabelPreset} to its renderer. */
public final class ShippingRenderers {

    private ShippingRenderers() {
    }

    private static final Map<LabelPreset, LabelRenderer> BY_PRESET =
            new EnumMap<>(LabelPreset.class);

    static {
        register(new StandardCarrierRenderer());
        register(new AmazonFbaRenderer());
        register(new Gs1SsccRenderer());
        register(new SimpleAddressRenderer());
        register(new ReturnLabelRenderer());
        register(new Fnsku30UpRenderer());
    }

    private static void register(LabelRenderer renderer) {
        BY_PRESET.put(renderer.preset(), renderer);
    }

    public static LabelRenderer forPreset(LabelPreset preset) {
        LabelRenderer r = BY_PRESET.get(preset == null
                ? LabelPreset.STANDARD_CARRIER_4X6 : preset);
        if (r == null) {
            throw new IllegalArgumentException("No renderer for preset: " + preset);
        }
        return r;
    }
}
