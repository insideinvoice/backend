package com.insideinvoice.invoice.mapper;

import com.insideinvoice.invoice.dto.request.CreateInvoiceRequest;
import com.insideinvoice.invoice.dto.request.InvoiceExtendedFields;
import com.insideinvoice.invoice.dto.request.InvoiceItemRequest;
import com.insideinvoice.invoice.dto.response.InvoiceItemResponse;
import com.insideinvoice.invoice.dto.response.InvoiceResponse;
import com.insideinvoice.invoice.entity.BillingMode;
import com.insideinvoice.invoice.entity.DiscountType;
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
                .discountPercent(normalizeInvoiceDiscount(request.getDiscountType(), request.getDiscountPercent()))
                .discountType(request.getDiscountType())
                .billingMode(BillingMode.fromRaw(request.getBillingMode()))
                .createdBy(userId)
                .build();

        applyExtendedFields(invoice, request);

        List<InvoiceItem> items = request.getItems().stream()
                .map(itemRequest -> toInvoiceItem(itemRequest, invoice))
                .toList();

        invoice.setItems(items);
        calculateInvoiceTotals(invoice);

        return invoice;
    }

    /**
     * Copies the V29 industry-extended reference fields (rental agreement,
     * asset/vehicle identifiers, periods, healthcare references) from a create
     * or update request onto the invoice. Display/reference only — no totals.
     */
    public static void applyExtendedFields(Invoice invoice, InvoiceExtendedFields request) {
        // GST Rule 46 compliance fields (cessPercentage must be set before
        // calculateInvoiceTotals runs — both call sites honour that order).
        invoice.setShippingAddress(request.getShippingAddress());
        invoice.setReverseCharge(Boolean.TRUE.equals(request.getReverseCharge()));
        invoice.setCessPercentage(request.getCessPercentage() != null ? request.getCessPercentage() : BigDecimal.ZERO);
        invoice.setEwayBillNumber(request.getEwayBillNumber());
        invoice.setAgreementNumber(request.getAgreementNumber());
        invoice.setAssetNumber(request.getAssetNumber());
        invoice.setSerialNumber(request.getSerialNumber());
        invoice.setVehicleNumber(request.getVehicleNumber());
        invoice.setPeriodStart(request.getPeriodStart());
        invoice.setPeriodEnd(request.getPeriodEnd());
        invoice.setBillingPeriodStart(request.getBillingPeriodStart());
        invoice.setBillingPeriodEnd(request.getBillingPeriodEnd());
        invoice.setExpectedReturnDate(request.getExpectedReturnDate());
        invoice.setDepositReference(request.getDepositReference());
        invoice.setPatientReference(request.getPatientReference());
        invoice.setServiceDate(request.getServiceDate());
        invoice.setTreatmentReference(request.getTreatmentReference());
        invoice.setReferringDoctor(request.getReferringDoctor());
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
                // Older clients omit unit; 'Piece' is what templates hard-coded, so
                // a missing value renders exactly as before V28.
                .unit(request.getUnit() == null || request.getUnit().isBlank() ? "Piece" : request.getUnit().trim())
                .qty(qty)
                .rate(rate)
                .gstPercentage(gstPercentage)
                .discountPercent(clampPercent(request.getDiscountPercent()))
                .taxableValue(taxableValue)
                .taxAmount(taxAmount)
                .total(total)
                .build();
    }

    /**
     * Per-line discount %, clamped to 0–100 and passed through as null when the
     * client omits it (every legacy row stays NULL — never coerced to 0 so a
     * stored "unset" and a stored "0%" remain distinguishable).
     */
    public static BigDecimal clampPercent(BigDecimal pct) {
        if (pct == null) return null;
        return pct.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }

    /**
     * Invoice-level discount percent to persist: INDIVIDUAL lives entirely on
     * the lines, so the invoice-level column is forced to 0 (the summary and
     * email can never print a phantom "Full" percent on an individual invoice).
     * FULL/null keeps the raw value — legacy rows are byte-identical.
     */
    public static BigDecimal normalizeInvoiceDiscount(DiscountType type, BigDecimal discountPercent) {
        if (type == DiscountType.INDIVIDUAL) return BigDecimal.ZERO;
        return discountPercent != null ? discountPercent : BigDecimal.ZERO;
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

    /**
     * Display discount for a whole invoice, honouring the V45 mode: INDIVIDUAL
     * sums the per-line discounts (each rounded exactly as the totals engine
     * rounds them), FULL/legacy keeps the invoice-level formula. Rendering
     * layers (e-mail, PDF) call this instead of reading discountPercent, which
     * is forced to 0 on individual invoices.
     */
    public static BigDecimal discountAmount(Invoice invoice) {
        if (invoice.getDiscountType() == DiscountType.INDIVIDUAL) {
            BigDecimal sum = invoice.getItems().stream()
                    .map(i -> discountAmount(i.getTaxableValue(), i.getDiscountPercent()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.setScale(2, RoundingMode.HALF_UP);
        }
        return discountAmount(invoice.getSubtotal(), invoice.getDiscountPercent());
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public void calculateInvoiceTotals(Invoice invoice) {
        BigDecimal subtotal = invoice.getItems().stream()
                .map(InvoiceItem::getTaxableValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal discountAmount;
        BigDecimal taxableAmount;
        BigDecimal taxAmount;

        if (invoice.getDiscountType() == DiscountType.INDIVIDUAL) {
            // Per-line discounts (V45): every line's GST applies to ITS OWN
            // net value (CGST Act s.15(3)(b)) — no invoice-level pro-rating.
            // Mirrors invoiceTotals.js line-for-line so the stored invoice and
            // every screen agree to the paisa.
            BigDecimal discountSum = BigDecimal.ZERO;
            BigDecimal taxSum = BigDecimal.ZERO;
            for (InvoiceItem item : invoice.getItems()) {
                BigDecimal gross = item.getTaxableValue() != null ? item.getTaxableValue() : BigDecimal.ZERO;
                BigDecimal pct = clampPercent(item.getDiscountPercent());
                if (pct == null) pct = BigDecimal.ZERO;
                BigDecimal lineDiscount = gross.multiply(pct)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal net = gross.subtract(lineDiscount);
                discountSum = discountSum.add(lineDiscount);
                taxSum = taxSum.add(net.multiply(item.getGstPercentage())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }
            discountAmount = discountSum.setScale(2, RoundingMode.HALF_UP);
            taxableAmount = subtotal.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
            taxAmount = taxSum.setScale(2, RoundingMode.HALF_UP);
        } else {
            // FULL (and every legacy NULL) — the original invoice-level path,
            // unchanged: discount on the subtotal, GST pro-rated by ratio.
            discountAmount = discountAmount(subtotal, invoice.getDiscountPercent());
            taxableAmount = subtotal.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);

            // GST must be computed on the discounted taxable value, not the full
            // amount — otherwise a discounted invoice shows a higher tax than due.
            BigDecimal ratio = subtotal.compareTo(BigDecimal.ZERO) > 0
                    ? taxableAmount.divide(subtotal, 10, RoundingMode.HALF_UP)
                    : BigDecimal.ONE;

            taxAmount = invoice.getItems().stream()
                    .map(item -> item.getTaxableValue()
                            .multiply(ratio)
                            .multiply(item.getGstPercentage())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Compensation cess: invoice-level percentage on the discounted
        // taxable value (same base as GST). Zero/null keeps legacy totals.
        BigDecimal cessPct = invoice.getCessPercentage() != null
                ? invoice.getCessPercentage().max(BigDecimal.ZERO).min(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        BigDecimal cessAmount = taxableAmount.multiply(cessPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal grandTotal = taxableAmount.add(taxAmount).add(cessAmount).setScale(2, RoundingMode.HALF_UP);

        invoice.setCessAmount(cessAmount);
        invoice.setSubtotal(subtotal);
        invoice.setTaxAmount(taxAmount);
        invoice.setGrandTotal(grandTotal);
    }

    public InvoiceResponse toResponse(Invoice invoice, String customerName) {
        return toResponse(invoice, customerName, null, invoice.getItems());
    }

    /** Same as {@link #toResponse(Invoice, String)} with the customer's phone on the row. */
    public InvoiceResponse toResponse(Invoice invoice, String customerName, String customerPhone) {
        return toResponse(invoice, customerName, customerPhone, invoice.getItems());
    }

    /**
     * Maps an invoice using an already-loaded item list. The list screen batch-loads
     * items for the whole page in one query and passes them here, so the lazy
     * collection is never touched (no N+1).
     */
    public InvoiceResponse toResponse(Invoice invoice, String customerName, List<InvoiceItem> items) {
        return toResponse(invoice, customerName, null, items);
    }

    /**
     * Full mapping: batch-loaded items plus the customer's phone (from the customer
     * the caller already loaded — never an extra per-invoice lookup).
     */
    public InvoiceResponse toResponse(Invoice invoice, String customerName, String customerPhone, List<InvoiceItem> items) {
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
                .customerPhone(customerPhone)
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .subtotal(invoice.getSubtotal())
                .taxAmount(invoice.getTaxAmount())
                .grandTotal(invoice.getGrandTotal())
                .discountPercent(invoice.getDiscountPercent())
                .discountType(invoice.getDiscountType())
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
                .shippingAddress(invoice.getShippingAddress())
                .reverseCharge(invoice.getReverseCharge())
                .cessPercentage(invoice.getCessPercentage())
                .cessAmount(invoice.getCessAmount())
                .ewayBillNumber(invoice.getEwayBillNumber())
                .agreementNumber(invoice.getAgreementNumber())
                .assetNumber(invoice.getAssetNumber())
                .serialNumber(invoice.getSerialNumber())
                .vehicleNumber(invoice.getVehicleNumber())
                .periodStart(invoice.getPeriodStart())
                .periodEnd(invoice.getPeriodEnd())
                .billingPeriodStart(invoice.getBillingPeriodStart())
                .billingPeriodEnd(invoice.getBillingPeriodEnd())
                .expectedReturnDate(invoice.getExpectedReturnDate())
                .depositReference(invoice.getDepositReference())
                .patientReference(invoice.getPatientReference())
                .serviceDate(invoice.getServiceDate())
                .treatmentReference(invoice.getTreatmentReference())
                .referringDoctor(invoice.getReferringDoctor())
                .paymentMode(invoice.getPaymentMode())
                .discountPercent(invoice.getDiscountPercent())
                .billingMode(invoice.getBillingMode() == null ? null : invoice.getBillingMode().name())
                .taxMode(invoice.getTaxMode() == null ? null : invoice.getTaxMode().name())
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
                .unit(item.getUnit())
                .qty(item.getQty())
                .rate(item.getRate())
                .gstPercentage(item.getGstPercentage())
                .discountPercent(item.getDiscountPercent())
                .taxableValue(item.getTaxableValue())
                .taxAmount(item.getTaxAmount())
                .total(item.getTotal())
                .build();
    }
}
