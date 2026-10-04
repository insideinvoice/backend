package com.insideinvoice.labels.service;

import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.labels.dto.request.CreateHazmatLabelRequest;
import com.insideinvoice.labels.dto.request.UpdateHazmatLabelRequest;
import com.insideinvoice.labels.dto.response.HazardClassResponse;
import com.insideinvoice.labels.dto.response.HazmatLabelResponse;
import com.insideinvoice.labels.dto.response.UnNumberResponse;

import java.util.List;

public interface HazmatLabelService {

    HazmatLabelResponse create(CreateHazmatLabelRequest request, Long businessId, Long userId, String ip);

    HazmatLabelResponse update(Long id, UpdateHazmatLabelRequest request, Long businessId, Long userId, String ip);

    void delete(Long id, Long businessId, Long userId, String ip);

    HazmatLabelResponse get(Long id, Long businessId);

    PagedResponse<HazmatLabelResponse> list(Long businessId, int page, int size, String q, String status);

    byte[] preview(CreateHazmatLabelRequest request) throws Exception;

    byte[] previewById(Long id, Long businessId) throws Exception;

    byte[] generatePdf(Long id, Long businessId, Long userId, String ip) throws Exception;

    byte[] getPdf(Long id, Long businessId) throws Exception;

    byte[] bulkPdf(List<Long> ids, Long businessId, Long userId, String ip) throws Exception;

    String zpl(Long id, Long businessId) throws Exception;

    HazmatLabelResponse markPrinted(Long id, Long businessId, Long userId, String ip);

    List<UnNumberResponse> unNumbers(String q);

    List<HazardClassResponse> classes();
}
