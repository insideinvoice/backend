package com.insideinvoice.deliverychallan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryChallanResponse {

    private Long id;
    private Long businessId;
    private String challanNumber;
    private Long customerId;
    private String customerName;
    private String customerAddress;
    private String customerPhone;
    private String customerGstIn;
    private LocalDate challanDate;
    private String poNumber;
    private LocalDate poDate;
    private LocalDateTime createdAt;
    private List<DeliveryChallanItemResponse> items;
}
