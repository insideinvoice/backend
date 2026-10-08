package com.insideinvoice.business.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateInvoiceSettingsRequest {

    @Size(max = 30)
    private String invoiceTemplate;

    @Size(max = 4000)
    private String printSettings;
}
