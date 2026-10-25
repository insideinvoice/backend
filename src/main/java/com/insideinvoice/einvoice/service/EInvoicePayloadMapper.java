package com.insideinvoice.einvoice.service;

import com.insideinvoice.einvoice.entity.EInvoiceConfig;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the EXISTING invoice (and its items, using the stored engine-computed
 * totals) into the INV-01-shaped payload. No second tax engine: whatever the
 * invoice stores is what is submitted; per-line discounts (V45) flow through
 * as item-level discount fields. Field names follow the common e-invoice
 * schema vocabulary; provider adapters translate to their exact wire format.
 */
@Component
public class EInvoicePayloadMapper {

    public Map<String, Object> buildPayload(Invoice invoice, String supplierGstin,
                                            String buyerGstin, String buyerName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentType", invoice.getInvoiceType() == null ? null : invoice.getInvoiceType().name());
        payload.put("documentNumber", invoice.getInvoiceNumber());
        payload.put("documentDate", invoice.getInvoiceDate() == null ? null : invoice.getInvoiceDate().toString());
        payload.put("supplierGstin", supplierGstin);
        payload.put("buyerGstin", buyerGstin);
        payload.put("buyerName", buyerName);
        payload.put("placeOfSupply", invoice.getPlaceOfSupply());
        payload.put("reverseCharge", Boolean.TRUE.equals(invoice.getReverseCharge()));
        payload.put("taxMode", invoice.getTaxMode() == null ? null : invoice.getTaxMode().name());

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        if (invoice.getItems() != null) {
            boolean igstMode = "IGST".equalsIgnoreCase(
                    invoice.getTaxMode() == null ? "" : invoice.getTaxMode().name());
            for (InvoiceItem item : invoice.getItems()) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("description", item.getItemName());
                line.put("hsn", item.getHsn());
                line.put("quantity", item.getQty());
                line.put("unit", item.getUnit());
                line.put("unitPrice", item.getRate());
                line.put("grossAmount", item.getTaxableValue());
                line.put("discountPercent", item.getDiscountPercent());
                line.put("taxableValue", item.getTaxableValue());
                line.put("gstRate", item.getGstPercentage());
                line.put("taxAmount", item.getTaxAmount());
                items.add(line);
                BigDecimal tax = item.getTaxAmount() == null ? BigDecimal.ZERO : item.getTaxAmount();
                if (igstMode) igst = igst.add(tax);
                else { cgst = cgst.add(tax.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP));
                       sgst = sgst.add(tax.subtract(tax.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP))); }
            }
        }
        payload.put("items", items);
        payload.put("totalCgst", cgst);
        payload.put("totalSgst", sgst);
        payload.put("totalIgst", igst);
        payload.put("totalInvoiceValue", invoice.getGrandTotal());
        return payload;
    }
}
