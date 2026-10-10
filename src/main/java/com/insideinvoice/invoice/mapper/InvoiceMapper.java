package com.insideinvoice.invoice.mapper;

import com.insideinvoice.invoice.dto.request.CreateInvoiceRequest;
import com.insideinvoice.invoice.dto.request.InvoiceItemRequest;
import com.insideinvoice.invoice.dto.response.InvoiceItemResponse;
import com.insideinvoice.invoice.dto.response.InvoiceResponse;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceItem;
import com.insideinvoice.invoice.entity.InvoiceStatus;
import com.insideinvoice.invoice.entity.InvoiceType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class InvoiceMapper {

    public Invoice toEntity(CreateInvoiceRequest request, String invoiceNumber, Long businessId, Long userId) {
        Invoice invoice = Invoice.builder()
                .businessId(businessId)
                .invoiceNumber(invoiceNumber)
                .invoiceType(InvoiceType.valueOf(request.getInvoiceType()))
                .customerId(request.getCustomerId())
                .invoiceDate(request.getInvoiceDate())
                .dueDate(request.getDueDate())
                .paymentTerms(request.getPaymentTerms())
                .notes(request.getNotes())
                .status(InvoiceStatus.DRAFT)
                .placeOfSupply(request.getPlaceOfSupply())
                .deliveryNote(request.getDeliveryNote())
                .deliveryNoteDate(request.getDeliveryNoteDate())
                .referenceNumber(request.getReferenceNumber())
                .buyerOrderNumber(request.getBuyerOrderNumber())
                .dispatchDocNumber(request.getDispatchDocNumber())
                .dispatchedThrough(request.getDispatchedThrough())
                .termsOfDelivery(request.getTermsOfDelivery())
                .otherReferences(request.getOtherReferences())
                .destination(request.getDestination())
                .paymentMode(request.getPaymentMode())
                .discountPercent(request.getDiscountPercent() != null ? request.getDiscountPercent() : BigDecimal.ZERO)
                .createdBy(userId)
                .build();

        List<InvoiceItem> items = request.getItems().stream()
                .map(itemRequest -> toInvoiceItem(itemRequest, invoice))
                .toList();

        invoice.setItems(items);
        calculateInvoiceTotals(invoice);

        return invoice;
    }

    public InvoiceItem toInvoiceItem(InvoiceItemRequest request, Invoice invoice) {
        BigDecimal qty = request.getQty();
        BigDecimal rate = request.getRate();
        BigDecimal gstPercentage = request.getGstPercentage();

        BigDecimal taxableValue = qty.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = taxableValue.multiply(gstPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = taxableValue.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        return InvoiceItem.builder()
                .invoice(invoice)
                .productId(request.getProductId())
                .sno(request.getSno())
                .itemName(request.getItemName())
                .hsn(request.getHsn())
                .qty(qty)
                .rate(rate)
                .gstPercentage(gstPercentage)
                .taxableValue(taxableValue)
                .taxAmount(taxAmount)
                .total(total)
                .build();
    }

    /**
     * Single source of truth for the invoice-level discount amount: the same
     * percentage the invoice stores and the totals engine applies. Rendering
     * layers (email, PDF) must call this instead of inventing their own formula.
     */
    public static BigDecimal discountAmount(BigDecimal subtotal, BigDecimal discountPercent) {
        BigDecimal pct = discountPercent != null ? discountPercent : BigDecimal.ZERO;
        pct = pct.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        return nvl(subtotal).multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public void calculateInvoiceTotals(Invoice invoice) {
        BigDecimal subtotal = invoice.getItems().stream()
                .map(InvoiceItem::getTaxableValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal discountAmount = discountAmount(subtotal, invoice.getDiscountPercent());
        BigDecimal taxableAmount = subtotal.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);

        // GST must be computed on the discounted taxable value, not the full
        // amount — otherwise a discounted invoice shows a higher tax than due.
        BigDecimal ratio = subtotal.compareTo(BigDecimal.ZERO) > 0
                ? taxableAmount.divide(subtotal, 10, RoundingMode.HALF_UP)
                : BigDecimal.ONE;

        BigDecimal taxAmount = invoice.getItems().stream()
                .map(item -> item.getTaxableValue()
                        .multiply(ratio)
                        .multiply(item.getGstPercentage())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal grandTotal = taxableAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        invoice.setSubtotal(subtotal);
        invoice.setTaxAmount(taxAmount);
        invoice.setGrandTotal(grandTotal);
    }

    public InvoiceResponse toResponse(Invoice invoice, String customerName) {
        return toResponse(invoice, customerName, invoice.getItems());
    }

    /**
     * Maps an invoice using an already-loaded item list. The list screen batch-loads
     * items for the whole page in one query and passes them here, so the lazy
     * collection is never touched (no N+1).
     */
    public InvoiceResponse toResponse(Invoice invoice, String customerName, List<InvoiceItem> items) {
        List<InvoiceItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();

        return InvoiceResponse.builder()
                .id(invoice.getId())
                .businessId(invoice.getBusinessId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .invoiceType(invoice.getInvoiceType().name())
                .customerId(invoice.getCustomerId())
                .customerName(customerName)
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .subtotal(invoice.getSubtotal())
                .taxAmount(invoice.getTaxAmount())
                .grandTotal(invoice.getGrandTotal())
                .discountPercent(invoice.getDiscountPercent())
                .paymentTerms(invoice.getPaymentTerms())
                .notes(invoice.getNotes())
                .status(invoice.getStatus().name())
                .placeOfSupply(invoice.getPlaceOfSupply())
                .deliveryNote(invoice.getDeliveryNote())
                .deliveryNoteDate(invoice.getDeliveryNoteDate())
                .referenceNumber(invoice.getReferenceNumber())
                .buyerOrderNumber(invoice.getBuyerOrderNumber())
                .dispatchDocNumber(invoice.getDispatchDocNumber())
                .dispatchedThrough(invoice.getDispatchedThrough())
                .termsOfDelivery(invoice.getTermsOfDelivery())
                .otherReferences(invoice.getOtherReferences())
                .destination(invoice.getDestination())
                .paymentMode(invoice.getPaymentMode())
                .discountPercent(invoice.getDiscountPercent())
                .createdBy(invoice.getCreatedBy())
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .items(itemResponses)
                .build();
    }

    public InvoiceItemResponse toItemResponse(InvoiceItem item) {
        return InvoiceItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .sno(item.getSno())
                .itemName(item.getItemName())
                .hsn(item.getHsn())
                .qty(item.getQty())
                .rate(item.getRate())
                .gstPercentage(item.getGstPercentage())
                .taxableValue(item.getTaxableValue())
                .taxAmount(item.getTaxAmount())
                .total(item.getTotal())
                .build();
    }
}
