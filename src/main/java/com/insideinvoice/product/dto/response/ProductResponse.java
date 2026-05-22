package com.insideinvoice.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private String description;
    private String hsn;
    private String unit;
    private BigDecimal rate;
    private BigDecimal gstPercentage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
