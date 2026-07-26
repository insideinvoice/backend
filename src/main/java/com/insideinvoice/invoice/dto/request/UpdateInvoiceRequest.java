package com.insideinvoice.invoice.dto.request;

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
public class UpdateInvoiceRequest {

    private String invoiceNumber;

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotBlank(message = "Invoice type is required")
    private String invoiceType;

    @NotNull(message = "Invoice date is required")
    private LocalDate invoiceDate;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    private String placeOfSupply;

    private String paymentTerms;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    @NotBlank(message = "Status is required")
    private String status;

    private String deliveryNote;

    private LocalDate deliveryNoteDate;

    private String referenceNumber;

    private String buyerOrderNumber;

    private String dispatchDocNumber;

    private String dispatchedThrough;

    private String termsOfDelivery;

    private String otherReferences;

    private String destination;

    private String paymentMode;

    @Valid
    @NotEmpty(message = "At least one invoice item is required")
    private List<InvoiceItemRequest> items;
}
