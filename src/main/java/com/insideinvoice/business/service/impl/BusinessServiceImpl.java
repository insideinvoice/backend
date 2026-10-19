package com.insideinvoice.business.service.impl;

import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.business.dto.request.BusinessSetupRequest;
import com.insideinvoice.business.dto.request.BusinessUpdateRequest;
import com.insideinvoice.business.dto.request.UpdateInvoiceSettingsRequest;
import com.insideinvoice.business.dto.response.BusinessResponse;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.mapper.BusinessMapper;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.business.industry.Industry;
import com.insideinvoice.business.service.BusinessService;
import com.insideinvoice.common.IndianRegions;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.DuplicateResourceException;
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

        // /business/setup is the FIRST-RUN flow only: replaying it after completion
        // silently overwrote the business profile (name, GST, invoice convention...).
        // Ongoing edits go through PUT /business/update.
        if (user.isBusinessSetupCompleted()) {
            throw new DuplicateResourceException(
                    "Business setup has already been completed. Use Business Settings to make changes.");
        }

        businessMapper.updateEntity(business, request);
        // Onboarding jurisdiction config (optional): omitted values keep the
        // database defaults (line shown, "Bengaluru", region unknown), so old
        // clients and partial payloads never break first-run setup.
        applyJurisdictionSettings(
                business,
                request.getJurisdictionEnabled(),
                request.getJurisdictionRegion(),
                request.getJurisdictionCity());
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
        if (request.getSpecialistIn() != null) business.setSpecialistIn(request.getSpecialistIn());
        if (request.getSpecialistInEnabled() != null) business.setSpecialistInEnabled(request.getSpecialistInEnabled());
        // Industry is validated by @Pattern on the request; resolve() additionally
        // normalises case/whitespace. Null = leave unchanged (older clients).
        if (request.getIndustry() != null) {
            business.setIndustry(Industry.resolve(request.getIndustry()).getId());
        }
        // HSN/SAC display switch: null = leave unchanged. Display only — stored
        // codes and GST calculations are never touched by this setting.
        if (request.getShowHnSac() != null) {
            business.setShowHnSac(request.getShowHnSac());
        }
        // Company seal/stamp: null = leave unchanged. The type is normalised to
        // the two supported variants so an unexpected value can never reach the
        // document renderers (which branch on "round" vs anything else).
        if (request.getSealEnabled() != null) {
            business.setSealEnabled(request.getSealEnabled());
        }
        if (request.getSealType() != null && !request.getSealType().isBlank()) {
            business.setSealType("stamp".equalsIgnoreCase(request.getSealType().trim()) ? "stamp" : "round");
        }

        // Invoice jurisdiction footer: null = leave unchanged (older clients).
        // Shared with the onboarding setup path so both flows validate exactly
        // the same way.
        applyJurisdictionSettings(
                business,
                request.getJurisdictionEnabled(),
                request.getJurisdictionRegion(),
                request.getJurisdictionCity());

        business = businessRepository.save(business);
        log.info("Business updated: {}", businessId);
        return businessMapper.toResponse(business);
    }

    /**
     * Applies the configurable jurisdiction footer settings to a business.
     *
     * <p>Semantics: every null argument means "leave the current value" (the
     * database defaults for a fresh business) so older clients and partial
     * payloads never break. The region must be a member of
     * {@link IndianRegions}; the city is trimmed and its repeated whitespace
     * collapsed; when the footer ends up enabled the city must be non-blank,
     * otherwise the renderers could print a malformed footer line.</p>
     */
    private void applyJurisdictionSettings(Business business, Boolean enabled, String rawRegion, String rawCity) {
        if (enabled != null) {
            business.setJurisdictionEnabled(enabled);
        }
        if (rawRegion != null) {
            if (!IndianRegions.isValid(rawRegion)) {
                throw new BadRequestException(
                        "Unknown jurisdiction region: '" + rawRegion
                                + "'. Choose one of India's states or union territories.");
            }
            business.setJurisdictionRegion(rawRegion.trim());
        }
        if (rawCity != null) {
            business.setJurisdictionCity(rawCity.replaceAll("\\s+", " ").trim());
        }
        boolean jurisdictionEnabled = !Boolean.FALSE.equals(business.getJurisdictionEnabled());
        if (jurisdictionEnabled
                && (business.getJurisdictionCity() == null || business.getJurisdictionCity().isBlank())) {
            throw new BadRequestException(
                    "Jurisdiction city is required when the jurisdiction line is shown on invoices.");
        }
    }

    @Override
    @Transactional
    public BusinessResponse updateInvoiceSettings(Long businessId, UpdateInvoiceSettingsRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", "id", businessId));
        if (request.getInvoiceTemplate() != null) {
            business.setInvoiceTemplate(request.getInvoiceTemplate());
        }
        if (request.getPrintSettings() != null) {
            business.setPrintSettings(request.getPrintSettings());
        }
        if (request.getShowHnSac() != null) {
            business.setShowHnSac(request.getShowHnSac());
        }
        return businessMapper.toResponse(businessRepository.save(business));
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

    @Override
    @Transactional
    public void updateLogo(Long businessId, String base64Logo) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", "id", businessId));
        business.setLogo(base64Logo);
        businessRepository.save(business);
        log.info("Logo updated for businessId: {}", businessId);
    }
}
