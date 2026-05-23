package com.insideinvoice.invoice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class InvoiceItemRequest {

    private Long productId;

    private Integer sno;

    @NotBlank(message = "Item name is required")
    @Size(max = 255, message = "Item name must not exceed 255 characters")
    private String itemName;

    private String hsn;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be greater than 0")
    private BigDecimal qty;

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.01", message = "Rate must be greater than 0")
    private BigDecimal rate;

    @NotNull(message = "GST percentage is required")
    @DecimalMin(value = "0.00", message = "GST percentage must be at least 0")
    @DecimalMax(value = "100.00", message = "GST percentage must not exceed 100")
    private BigDecimal gstPercentage;
}
