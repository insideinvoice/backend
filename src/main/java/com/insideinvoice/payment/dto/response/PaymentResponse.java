package com.insideinvoice.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;
    private Long businessId;
    private Long invoiceId;
    private String invoiceNumber;
    private String customerName;
    private BigDecimal amount;
    private String paymentMode;
    private String referenceNo;
    private LocalDate paymentDate;
    private String notes;
    private LocalDateTime createdAt;
}
