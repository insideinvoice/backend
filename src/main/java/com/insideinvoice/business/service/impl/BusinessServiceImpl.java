package com.insideinvoice.business.service.impl;

import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.business.dto.request.BusinessSetupRequest;
import com.insideinvoice.business.dto.request.BusinessUpdateRequest;
import com.insideinvoice.business.dto.response.BusinessResponse;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.mapper.BusinessMapper;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.business.service.BusinessService;
import com.insideinvoice.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessServiceImpl implements BusinessService {

    private static final Logger log = LoggerFactory.getLogger(BusinessServiceImpl.class);

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final BusinessMapper businessMapper;

    @Override
    @Transactional
    public BusinessResponse setupBusiness(Long userId, Long businessId, BusinessSetupRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", "id", businessId));

        User user = userRepository.findByIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        businessMapper.updateEntity(business, request);
        business.setOwnerName(user.getName());
        business = businessRepository.save(business);

        user.setBusinessSetupCompleted(true);
        userRepository.save(user);

        log.info("Business setup completed for businessId: {}", businessId);
        return businessMapper.toResponse(business);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessResponse getBusiness(Long businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", "id", businessId));
        return businessMapper.toResponse(business);
    }

    @Override
    @Transactional
    public BusinessResponse updateBusiness(Long businessId, BusinessUpdateRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", "id", businessId));

        if (request.getBusinessName() != null) business.setBusinessName(request.getBusinessName());
        if (request.getGstIn() != null) business.setGstIn(request.getGstIn());
        if (request.getPhone() != null) business.setPhone(request.getPhone());
        if (request.getEmail() != null) business.setEmail(request.getEmail());
        if (request.getWebsite() != null) business.setWebsite(request.getWebsite());
        if (request.getAddressLine1() != null) business.setAddressLine1(request.getAddressLine1());
        if (request.getAddressLine2() != null) business.setAddressLine2(request.getAddressLine2());
        if (request.getCity() != null) business.setCity(request.getCity());
        if (request.getState() != null) business.setState(request.getState());
        if (request.getCountry() != null) business.setCountry(request.getCountry());
        if (request.getPincode() != null) business.setPincode(request.getPincode());
        if (request.getInvoicePrefix() != null) business.setInvoicePrefix(request.getInvoicePrefix());
        if (request.getBankName() != null) business.setBankName(request.getBankName());
        if (request.getAccountNo() != null) business.setAccountNo(request.getAccountNo());
        if (request.getBranch() != null) business.setBranch(request.getBranch());
        if (request.getIfsc() != null) business.setIfsc(request.getIfsc());
        if (request.getBankAddress() != null) business.setBankAddress(request.getBankAddress());
        if (request.getUpiId() != null) business.setUpiId(request.getUpiId());

        business = businessRepository.save(business);
        log.info("Business updated: {}", businessId);
        return businessMapper.toResponse(business);
    }

    @Override
    @Transactional
    public void updateSignature(Long businessId, String base64Signature) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", "id", businessId));
        business.setSignature(base64Signature);
        businessRepository.save(business);
        log.info("Signature updated for businessId: {}", businessId);
    }
}
