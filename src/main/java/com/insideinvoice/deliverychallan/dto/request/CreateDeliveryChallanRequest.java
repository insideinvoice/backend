package com.insideinvoice.deliverychallan.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDeliveryChallanRequest {

    @Size(max = 50, message = "Challan number must not exceed 50 characters")
    private String challanNumber;

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotNull(message = "Challan date is required")
    private LocalDate challanDate;

    @Size(max = 100, message = "P.O. number must not exceed 100 characters")
    private String poNumber;

    private LocalDate poDate;

    @Valid
    @NotEmpty(message = "At least one item is required")
    private List<CreateDeliveryChallanItemRequest> items;
}
