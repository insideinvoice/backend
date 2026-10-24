package com.insideinvoice.business.entity;

import com.insideinvoice.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Update only the columns actually changed: without this, Hibernate's full-row UPDATE
// wrote stale nextInvoiceSequence values from other sessions back to the DB and could
// roll the sequence backwards (duplicate invoice numbers under concurrency).
@org.hibernate.annotations.DynamicUpdate
public class Business extends BaseEntity {

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "gst_in")
    private String gstIn;

    @Column(name = "phone")
    private String phone;

    /** Optional second phone number (e.g. a landline next to the mobile). */
    @Column(name = "additional_phone", length = 20)
    private String additionalPhone;

    @Column(name = "email")
    private String email;

    @Column(name = "website")
    private String website;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "country")
    private String country;

    @Column(name = "pincode")
    private String pincode;

    @Column(name = "invoice_prefix", length = 50)
    private String invoicePrefix;

    @Column(name = "next_invoice_sequence", nullable = false)
    @Builder.Default
    private Long nextInvoiceSequence = 1L;

    @Column(name = "signature", columnDefinition = "TEXT")
    private String signature;

    /** Base64 company logo (Settings upload), printed top-left on template-32. */
    @Column(name = "logo", columnDefinition = "TEXT")
    private String logo;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "account_no")
    private String accountNo;

    @Column(name = "branch")
    private String branch;

    @Column(name = "ifsc")
    private String ifsc;

    @Column(name = "bank_address")
    private String bankAddress;

    @Column(name = "upi_id", length = 100)
    private String upiId;

    @Column(name = "specialist_in", columnDefinition = "TEXT")
    private String specialistIn;

    @Column(name = "specialist_in_enabled", nullable = false)
    @Builder.Default
    private Boolean specialistInEnabled = false;

    @Column(name = "invoice_template", length = 30)
    private String invoiceTemplate;

    @Column(name = "print_settings", columnDefinition = "TEXT")
    private String printSettings;

    /**
     * Stable industry profile id (Industry enum). DEFAULT 'OTHER' at the DB
     * level, so every business created before this feature resolves to the
     * backward-compatible profile that hides nothing.
     */
    @Column(name = "industry", length = 40, nullable = false)
    @Builder.Default
    private String industry = "OTHER";

    /**
     * Display-only switch for the HSN/SAC column on invoice documents. TRUE
     * (DB default) keeps the historical rendering; FALSE hides the column.
     * Stored codes and GST calculations are never affected by this switch.
     */
    @Column(name = "show_hsn_sac", nullable = false)
    @Builder.Default
    private Boolean showHnSac = true;

    /**
     * Company seal/stamp preference. Stored on the business (not the browser)
     * so the emailed invoice and the public share page — both rendered by the
     * server, the latter on the customer's device — show the same seal the
     * owner sees when downloading. FALSE (DB default) keeps every existing
     * business seal-free until explicitly enabled.
     */
    @Column(name = "seal_enabled", nullable = false)
    @Builder.Default
    private Boolean sealEnabled = false;

    /** Seal variant: "round" (round seal) or "stamp" (rubber stamp). */
    @Column(name = "seal_type", length = 20, nullable = false)
    @Builder.Default
    private String sealType = "round";

    /**
     * Configurable seal placement as JSON (see V38): per-variant sizeMm, tilt,
     * rightMm and bottomMm. NULL means the built-in defaults, so businesses
     * that never opened the setting keep the historical rendering.
     */
    @Column(name = "seal_config", columnDefinition = "TEXT")
    private String sealConfig;

    /**
     * Default Terms & Conditions as a JSON array of up to five strings (see
     * V40), printed in the footer of every invoice. NULL = no default terms.
     */
    @Column(name = "terms_conditions", columnDefinition = "TEXT")
    private String termsConditions;

    /**
     * Configurable "Subject to ... Jurisdiction" footer. TRUE (DB default)
     * preserves the historical rendering, where the line was hardcoded and
     * always shown. FALSE removes the line from newly rendered documents; the
     * separate "This is a Computer Generated Invoice" notice is unaffected.
     */
    @Column(name = "jurisdiction_enabled", nullable = false)
    @Builder.Default
    private Boolean jurisdictionEnabled = true;

    /**
     * Selected Indian state or Union Territory for the jurisdiction (see
     * {@link com.insideinvoice.common.IndianRegions}). Optional and
     * presentation-only: no historical "best guess" is written for existing
     * businesses; null stays null until the owner picks one in Settings.
     */
    @Column(name = "jurisdiction_region", length = 120)
    private String jurisdictionRegion;

    /**
     * City/location printed in the jurisdiction footer. DB default is the
     * historical hardcoded value so every existing business keeps rendering
     * exactly what it did before this column existed.
     */
    @Column(name = "jurisdiction_city", length = 120, nullable = false)
    @Builder.Default
    private String jurisdictionCity = "Bengaluru";
}
