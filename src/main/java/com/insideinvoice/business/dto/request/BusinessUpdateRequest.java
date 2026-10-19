package com.insideinvoice.business.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessUpdateRequest {

    @Size(max = 255, message = "Business name must not exceed 255 characters")
    private String businessName;

    private String gstIn;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    private String email;

    private String website;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String country;

    private String pincode;

    @Size(max = 50, message = "Invoice convention must not exceed 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9._/-]*$", message = "Invoice convention may only contain letters, numbers and - . / _")
    private String invoicePrefix;

    private String bankName;

    private String accountNo;

    private String branch;

    private String ifsc;

    private String bankAddress;

    private String upiId;

    /**
     * Stable industry profile id. Null means "leave unchanged", so older
     * clients that never send the field keep working.
     */
    @Pattern(regexp = com.insideinvoice.business.industry.Industry.ID_PATTERN,
            message = "Unknown industry")
    private String industry;

    @Size(max = 500, message = "Specialist in must not exceed 500 characters")
    private String specialistIn;

    private Boolean specialistInEnabled;

    /**
     * Show (TRUE) or hide (FALSE) the HSN/SAC column on invoices. Display only:
     * stored codes and GST calculations are unaffected. Null = leave unchanged.
     */
    private Boolean showHnSac;

    /**
     * Enable (TRUE) or disable (FALSE) the company seal/stamp on invoice
     * documents and public share pages. Null = leave unchanged.
     */
    private Boolean sealEnabled;

    /**
     * Seal variant: "round" (round seal) or "stamp" (rubber stamp).
     * Null = leave unchanged.
     */
    @Size(max = 20, message = "Seal type must not exceed 20 characters")
    private String sealType;

    /**
     * Show (TRUE) or hide (FALSE) the "SUBJECT TO {CITY} JURISDICTION" footer
     * line on invoice documents. The Computer Generated notice is independent
     * of this switch. Null = leave unchanged.
     */
    private Boolean jurisdictionEnabled;

    /**
     * Selected Indian state or Union Territory (must match
     * {@link com.insideinvoice.common.IndianRegions#ALL}). Null = leave
     * unchanged; the service validates membership and rejects unknown values.
     */
    @Size(max = 120, message = "Jurisdiction region must not exceed 120 characters")
    private String jurisdictionRegion;

    /**
     * City/location printed in the jurisdiction footer, e.g. Bengaluru.
     * Editable independently of the region; required (non-blank) whenever the
     * footer is enabled. Null = leave unchanged.
     */
    @Size(max = 120, message = "Jurisdiction city must not exceed 120 characters")
    private String jurisdictionCity;
}
