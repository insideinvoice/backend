package com.insideinvoice.invoice.share;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceItem;
import com.insideinvoice.invoice.entity.InvoiceType;
import com.insideinvoice.invoice.share.dto.PublicInvoiceResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps entities to the public allow-list DTO field by field. No entity is serialized
 * directly anywhere on the public path, so new private columns cannot leak by accident.
 */
@Component
public class PublicInvoiceAssembler {

    private static final ObjectMapper OM = new ObjectMapper();

    public PublicInvoiceResponse assemble(ResolvedPublicInvoice resolved) {
        return assemble(resolved, null);
    }

    /**
     * @param typeOverride {@code TAX_INVOICE} / {@code PROFORMA_INVOICE} to render the
     *                     other document for the same invoice, or {@code null} to use the
     *                     stored type. Unknown values are ignored rather than trusted, so
     *                     a bad query parameter can never reach the template lookup.
     */
    public PublicInvoiceResponse assemble(ResolvedPublicInvoice resolved, String typeOverride) {
        Invoice invoice = resolved.getInvoice();
        Business business = resolved.getBusiness();
        Customer customer = resolved.getCustomer();

        String invoiceTypeName = effectiveTypeName(invoice, typeOverride);

        return PublicInvoiceResponse.builder()
                .invoiceNumber(invoice.getInvoiceNumber())
                .invoiceType(invoiceTypeName)
                .status(invoice.getStatus() != null ? invoice.getStatus().name() : null)
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .subtotal(invoice.getSubtotal())
                .taxAmount(invoice.getTaxAmount())
                .grandTotal(invoice.getGrandTotal())
                .discountPercent(invoice.getDiscountPercent())
                .template(resolveTemplate(business, invoiceTypeName))
                .paperSize(resolvePaperSize(business, invoiceTypeName))
                .billingMode(invoice.getBillingMode() != null ? invoice.getBillingMode().name() : null)
                .showHnSac(business == null || business.getShowHnSac() == null || business.getShowHnSac())
                .paymentTerms(invoice.getPaymentTerms())
                .paymentMode(invoice.getPaymentMode())
                .placeOfSupply(invoice.getPlaceOfSupply())
                .destination(invoice.getDestination())
                .termsOfDelivery(invoice.getTermsOfDelivery())
                .deliveryNote(invoice.getDeliveryNote())
                .deliveryNoteDate(invoice.getDeliveryNoteDate())
                .referenceNumber(invoice.getReferenceNumber())
                .buyerOrderNumber(invoice.getBuyerOrderNumber())
                .dispatchDocNumber(invoice.getDispatchDocNumber())
                .dispatchedThrough(invoice.getDispatchedThrough())
                .otherReferences(invoice.getOtherReferences())
                .notes(invoice.getNotes())
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
                .seller(seller(business))
                .buyer(buyer(customer))
                .items(items(invoice))
                .build();
    }

    /** The type the document is rendered as: the validated override, else the stored one. */
    static String effectiveTypeName(Invoice invoice, String typeOverride) {
        if (InvoiceType.TAX_INVOICE.name().equals(typeOverride)
                || InvoiceType.PROFORMA_INVOICE.name().equals(typeOverride)) {
            return typeOverride;
        }
        return invoice.getInvoiceType() != null ? invoice.getInvoiceType().name() : null;
    }

    private String resolveTemplate(Business b, String invoiceTypeName) {
        if (b == null) return null;
        String perTypeTemplate = perTypeValue(b, invoiceTypeName, "template");
        return perTypeTemplate != null ? perTypeTemplate : b.getInvoiceTemplate();
    }

    private String resolvePaperSize(Business b, String invoiceTypeName) {
        if (b == null) return null;
        String perTypeSize = perTypeValue(b, invoiceTypeName, "paperSize");
        return perTypeSize != null ? perTypeSize : "A4_PORTRAIT";
    }

    private String perTypeValue(Business b, String invoiceTypeName, String field) {
        String json = b.getPrintSettings();
        if (json == null || json.isBlank() || invoiceTypeName == null) return null;
        try {
            JsonNode root = OM.readTree(json);
            JsonNode node = root.get(invoiceTypeName);
            if (node == null || node.isNull()) return null;
            if (node.isTextual()) {
                // legacy format where the type maps directly to a paper size id
                return field.equals("paperSize") ? node.asText() : null;
            }
            JsonNode f = node.get(field);
            return (f != null && !f.isNull()) ? f.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private PublicInvoiceResponse.Seller seller(Business b) {
        if (b == null) {
            return null;
        }
        return PublicInvoiceResponse.Seller.builder()
                .businessName(b.getBusinessName())
                .gstIn(b.getGstIn())
                .phone(b.getPhone())
                .email(b.getEmail())
                .website(b.getWebsite())
                .addressLine1(b.getAddressLine1())
                .addressLine2(b.getAddressLine2())
                .city(b.getCity())
                .state(b.getState())
                .country(b.getCountry())
                .pincode(b.getPincode())
                .specialistIn(b.getSpecialistIn())
                .specialistInEnabled(b.getSpecialistInEnabled())
                .showHnSac(b.getShowHnSac() == null || b.getShowHnSac())
                .signature(b.getSignature())
                .industry(b.getIndustry())
                .payment(PublicInvoiceResponse.PaymentInstructions.builder()
                        .bankName(b.getBankName())
                        .branch(b.getBranch())
                        .accountNo(b.getAccountNo())
                        .ifsc(b.getIfsc())
                        .bankAddress(b.getBankAddress())
                        .upiId(b.getUpiId())
                        .build())
                .build();
    }

    private PublicInvoiceResponse.Buyer buyer(Customer c) {
        if (c == null) {
            return null;
        }
        return PublicInvoiceResponse.Buyer.builder()
                .name(c.getName())
                .gstIn(c.getGstIn())
                .phone(c.getPhone())
                .email(c.getEmail())
                .billingAddress(c.getBillingAddress())
                .city(c.getCity())
                .state(c.getState())
                .country(c.getCountry())
                .pincode(c.getPincode())
                .build();
    }

    private List<PublicInvoiceResponse.Item> items(Invoice invoice) {
        return invoice.getItems().stream()
                .map(this::item)
                .toList();
    }

    private PublicInvoiceResponse.Item item(InvoiceItem i) {
        return PublicInvoiceResponse.Item.builder()
                .sno(i.getSno())
                .itemName(i.getItemName())
                .hsn(i.getHsn())
                .unit(i.getUnit())
                .qty(i.getQty())
                .rate(i.getRate())
                .gstPercentage(i.getGstPercentage())
                .taxableValue(i.getTaxableValue())
                .taxAmount(i.getTaxAmount())
                .total(i.getTotal())
                .build();
    }
}
