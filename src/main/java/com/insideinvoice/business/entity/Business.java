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

    @Column(name = "invoice_prefix")
    private String invoicePrefix;

    @Column(name = "next_invoice_sequence", nullable = false)
    @Builder.Default
    private Long nextInvoiceSequence = 1L;

    @Column(name = "signature", columnDefinition = "TEXT")
    private String signature;
}
