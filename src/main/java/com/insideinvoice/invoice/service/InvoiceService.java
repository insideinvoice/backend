package com.insideinvoice.invoice.service;

import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.invoice.dto.request.CreateInvoiceRequest;
import com.insideinvoice.invoice.dto.request.UpdateInvoiceRequest;
import com.insideinvoice.invoice.dto.response.InvoiceResponse;

public interface InvoiceService {

    InvoiceResponse createInvoice(CreateInvoiceRequest request, Long businessId, Long userId);

    PagedResponse<InvoiceResponse> getAllInvoices(Long businessId, int page, int size, String sortBy, String sortDir);

    InvoiceResponse getInvoice(Long id, Long businessId);

    InvoiceResponse updateInvoice(Long id, UpdateInvoiceRequest request, Long businessId);

    void deleteInvoice(Long id, Long businessId);
}
