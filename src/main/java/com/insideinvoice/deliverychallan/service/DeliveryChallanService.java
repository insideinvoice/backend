package com.insideinvoice.deliverychallan.service;

import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.deliverychallan.dto.request.CreateDeliveryChallanRequest;
import com.insideinvoice.deliverychallan.dto.response.DeliveryChallanResponse;

public interface DeliveryChallanService {

    DeliveryChallanResponse createDeliveryChallan(CreateDeliveryChallanRequest request, Long businessId, Long userId);

    PagedResponse<DeliveryChallanResponse> getAllDeliveryChallans(Long businessId, int page, int size,
            String sortBy, String sortDir);

    DeliveryChallanResponse getDeliveryChallan(Long id, Long businessId);

    void deleteDeliveryChallan(Long id, Long businessId);
}
