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
}
