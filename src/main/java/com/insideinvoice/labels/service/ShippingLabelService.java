package com.insideinvoice.labels.service;

import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.labels.dto.request.CreateShippingLabelRequest;
import com.insideinvoice.labels.dto.request.UpdateShippingLabelRequest;
import com.insideinvoice.labels.dto.response.ShippingLabelResponse;

import java.util.List;

public interface ShippingLabelService {

    ShippingLabelResponse create(CreateShippingLabelRequest request, Long businessId, Long userId, String ip);

    ShippingLabelResponse update(Long id, UpdateShippingLabelRequest request, Long businessId, Long userId, String ip);

    void delete(Long id, Long businessId, Long userId, String ip);

    ShippingLabelResponse get(Long id, Long businessId);

    PagedResponse<ShippingLabelResponse> list(Long businessId, int page, int size, String q,
                                              String status, String carrier, Long invoiceId);

    byte[] preview(CreateShippingLabelRequest request) throws Exception;

    byte[] previewById(Long id, Long businessId) throws Exception;

    byte[] generatePdf(Long id, Long businessId, Long userId, String ip) throws Exception;

    byte[] getPdf(Long id, Long businessId) throws Exception;

    byte[] bulkPdf(List<Long> ids, Long businessId, Long userId, String ip) throws Exception;

    String zpl(Long id, Long businessId) throws Exception;

    ShippingLabelResponse markPrinted(Long id, Long businessId, Long userId, String ip);

    ShippingLabelResponse fromInvoice(Long invoiceId, Long businessId, Long userId, String ip);
}
