package com.insideinvoice.invoice.share;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceItem;
import com.insideinvoice.invoice.share.dto.PublicInvoiceResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps entities to the public allow-list DTO field by field. No entity is serialized
 * directly anywhere on the public path, so new private columns cannot leak by accident.
 */
@Component
public class PublicInvoiceAssembler {

    public PublicInvoiceResponse assemble(ResolvedPublicInvoice resolved) {
        Invoice invoice = resolved.getInvoice();
        Business business = resolved.getBusiness();
        Customer customer = resolved.getCustomer();

        return PublicInvoiceResponse.builder()
                .invoiceNumber(invoice.getInvoiceNumber())
                .invoiceType(invoice.getInvoiceType() != null ? invoice.getInvoiceType().name() : null)
                .status(invoice.getStatus() != null ? invoice.getStatus().name() : null)
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .subtotal(invoice.getSubtotal())
                .taxAmount(invoice.getTaxAmount())
                .grandTotal(invoice.getGrandTotal())
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
                .seller(seller(business))
                .buyer(buyer(customer))
                .items(items(invoice))
                .build();
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
                .signature(b.getSignature())
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
                .qty(i.getQty())
                .rate(i.getRate())
                .gstPercentage(i.getGstPercentage())
                .taxableValue(i.getTaxableValue())
                .taxAmount(i.getTaxAmount())
                .total(i.getTotal())
                .build();
    }
}
