package com.insideinvoice.deliverychallan.entity;

import com.insideinvoice.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "delivery_challans", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"business_id", "challan_number"})
}, indexes = {
        @Index(name = "idx_delivery_challans_business_id", columnList = "business_id"),
        @Index(name = "idx_delivery_challans_customer_id", columnList = "customer_id"),
        @Index(name = "idx_delivery_challans_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryChallan extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "challan_number", nullable = false, length = 50)
    private String challanNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "challan_date", nullable = false)
    private LocalDate challanDate;

    @Column(name = "po_number", length = 100)
    private String poNumber;

    @Column(name = "po_date")
    private LocalDate poDate;

    @Column(name = "created_by")
    private Long createdBy;

    @OneToMany(mappedBy = "challan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DeliveryChallanItem> items = new ArrayList<>();
}
