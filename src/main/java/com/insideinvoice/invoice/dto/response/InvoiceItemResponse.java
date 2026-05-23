package com.insideinvoice.invoice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceItemResponse {

    private Long id;
    private Long productId;
    private Integer sno;
    private String itemName;
    private String hsn;
    private BigDecimal qty;
    private BigDecimal rate;
    private BigDecimal gstPercentage;
    private BigDecimal taxableValue;
    private BigDecimal taxAmount;
    private BigDecimal total;
}
