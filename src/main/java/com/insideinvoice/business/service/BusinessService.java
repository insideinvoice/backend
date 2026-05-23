package com.insideinvoice.business.service;

import com.insideinvoice.business.dto.request.BusinessSetupRequest;
import com.insideinvoice.business.dto.request.BusinessUpdateRequest;
import com.insideinvoice.business.dto.response.BusinessResponse;

public interface BusinessService {

    BusinessResponse setupBusiness(Long userId, Long businessId, BusinessSetupRequest request);

    BusinessResponse getBusiness(Long businessId);

    BusinessResponse updateBusiness(Long businessId, BusinessUpdateRequest request);

    void updateSignature(Long businessId, String base64Signature);
}
