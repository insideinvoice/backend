package com.insideinvoice.product.entity;

import com.insideinvoice.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_business_id", columnList = "business_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "hsn")
    private String hsn;

    @Column(name = "unit")
    private String unit;

    @Column(name = "rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal rate;

    @Column(name = "gst_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstPercentage;
}
