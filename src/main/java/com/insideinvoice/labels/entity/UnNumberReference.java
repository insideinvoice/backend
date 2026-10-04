package com.insideinvoice.labels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "un_numbers_reference")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnNumberReference {

    @Id
    @Column(name = "un_number", length = 10)
    private String unNumber;

    @Column(name = "proper_shipping_name", nullable = false, length = 200)
    private String properShippingName;

    @Column(name = "hazard_class", nullable = false, length = 10)
    private String hazardClass;

    @Column(name = "division", length = 4)
    private String division;

    @Column(name = "compat_group", length = 4)
    private String compatGroup;

    @Column(name = "packing_group", length = 4)
    private String packingGroup;

    @Column(name = "subsidiary_risks", length = 120)
    private String subsidiaryRisks;

    @Column(name = "erg_guide", length = 10)
    private String ergGuide;

    @Column(name = "ltd_qty", length = 40)
    private String ltdQty;

    @Column(name = "pax_allowed", nullable = false)
    private Boolean paxAllowed;

    @Column(name = "cao_allowed", nullable = false)
    private Boolean caoAllowed;

    @Column(name = "special_provisions", length = 200)
    private String specialProvisions;
}
