package com.insideinvoice.deliverychallan.dto.response;

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
public class DeliveryChallanItemResponse {

    private Long id;
    private Integer sno;
    private String description;
    private BigDecimal quantity;
}
