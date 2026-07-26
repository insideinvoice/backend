package com.insideinvoice.business.mapper;

import com.insideinvoice.business.dto.request.BusinessSetupRequest;
import com.insideinvoice.business.dto.response.BusinessResponse;
import com.insideinvoice.business.entity.Business;
import org.springframework.stereotype.Component;

@Component
public class BusinessMapper {

    public Business toEntity(BusinessSetupRequest request) {
        return Business.builder()
                .businessName(request.getBusinessName())
                .gstIn(request.getGstIn())
                .phone(request.getPhone())
                .email(request.getEmail())
                .website(request.getWebsite())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .pincode(request.getPincode())
                .invoicePrefix(request.getInvoicePrefix())
                .bankName(request.getBankName())
                .accountNo(request.getAccountNo())
                .branch(request.getBranch())
                .ifsc(request.getIfsc())
                .bankAddress(request.getBankAddress())
                .upiId(request.getUpiId())
                .nextInvoiceSequence(1L)
                .build();
    }

    public BusinessResponse toResponse(Business business) {
        return BusinessResponse.builder()
                .id(business.getId())
                .businessName(business.getBusinessName())
                .ownerName(business.getOwnerName())
                .gstIn(business.getGstIn())
                .phone(business.getPhone())
                .email(business.getEmail())
                .website(business.getWebsite())
                .addressLine1(business.getAddressLine1())
                .addressLine2(business.getAddressLine2())
                .city(business.getCity())
                .state(business.getState())
                .country(business.getCountry())
                .pincode(business.getPincode())
                .invoicePrefix(business.getInvoicePrefix())
                .nextInvoiceSequence(business.getNextInvoiceSequence())
                .signature(business.getSignature())
                .bankName(business.getBankName())
                .accountNo(business.getAccountNo())
                .branch(business.getBranch())
                .ifsc(business.getIfsc())
                .bankAddress(business.getBankAddress())
                .upiId(business.getUpiId())
                .createdAt(business.getCreatedAt())
                .updatedAt(business.getUpdatedAt())
                .build();
    }

    public void updateEntity(Business business, BusinessSetupRequest request) {
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
    }
}
