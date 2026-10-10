package com.insideinvoice.business.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.insideinvoice.business.industry.IndustryConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wire shape of an industry profile: the option list for the dropdowns plus
 * the resolved field/document configuration the UI applies.
 *
 * <p>Built from {@link com.insideinvoice.business.industry.IndustryRegistry};
 * never persisted and never trusted from the client.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndustryConfigResponse {

    private String id;
    private String name;
    private String description;

    /** Fields shown by default for this industry (catalogue order). */
    private List<String> visibleFields;

    /** Fields not shown by default. Values already stored are never deleted. */
    private List<String> hiddenFields;

    /** Fields the backend validates as required. */
    private Set<String> requiredFields;

    /** Visible fields that are not required. */
    private List<String> optionalFields;

    /** Industry wording for existing form fields. */
    private Map<String, String> labels;

    /** Suggested input hints for the same fields. */
    private Map<String, String> placeholders;

    /** Default availability of each document workflow. */
    private Documents documents;

    /** Maps the resolved application configuration onto the wire shape. */
    public static IndustryConfigResponse from(com.insideinvoice.business.industry.IndustryConfig config) {
        IndustryConfig.DocumentAccess docs = config.documents();
        return IndustryConfigResponse.builder()
                .id(config.industry().getId())
                .name(config.industry().getDisplayName())
                .description(config.industry().getDescription())
                .visibleFields(config.visibleFields())
                .hiddenFields(List.copyOf(config.hiddenFields()))
                .requiredFields(config.requiredFields())
                .optionalFields(config.optionalFields())
                .labels(config.labels())
                .placeholders(config.placeholders())
                .documents(Documents.builder()
                        .deliveryChallan(docs.deliveryChallan())
                        .shippingLabel(docs.shippingLabel())
                        .hazmatLabel(docs.hazmatLabel())
                        .build())
                .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Documents {
        private Boolean deliveryChallan;
        private Boolean shippingLabel;
        private Boolean hazmatLabel;
    }
}
