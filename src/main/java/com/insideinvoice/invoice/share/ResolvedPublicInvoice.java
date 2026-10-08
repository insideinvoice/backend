package com.insideinvoice.invoice.share;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.invoice.entity.Invoice;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Invoice plus the seller/buyer rows needed to render it publicly, loaded and
 * snapshot inside one read-only transaction (items collection force-initialized).
 */
@Getter
@AllArgsConstructor
public class ResolvedPublicInvoice {

    private final Invoice invoice;
    private final Business business;
    private final Customer customer;
}
