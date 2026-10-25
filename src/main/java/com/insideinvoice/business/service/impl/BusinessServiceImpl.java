package com.insideinvoice.business.service.impl;

import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.business.dto.SealConfig;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessServiceImpl implements BusinessService {

    private static final Logger log = LoggerFactory.getLogger(BusinessServiceImpl.class);

    // Built-in seal defaults: the round seal's historical 80px diameter and
    // the rubber stamp's 240px width on A4, both bottom-right, with the same
    // hand-stamped tilts the SVG components use. Values are millimetres.
    private static final double DEFAULT_ROUND_SIZE_MM = 21.0;
    private static final double DEFAULT_STAMP_SIZE_MM = 64.0;
    private static final double DEFAULT_ROUND_TILT_DEG = -8.0;
    private static final double DEFAULT_STAMP_TILT_DEG = -6.0;
    private static final double DEFAULT_RIGHT_MM = 10.0;
    private static final double DEFAULT_BOTTOM_MM = 8.0;

    // Ink colours the SVG components have always used. A missing or malformed
    // stored colour falls back to these, so a config saved before colour
    // existed renders exactly as it did before.
    private static final String DEFAULT_ROUND_COLOR = "#0A4BFF";
    private static final String DEFAULT_STAMP_COLOR = "#0000CC";

    // A4 portrait in millimetres. Edge offsets are measured from the paper's
    // right/bottom edge, so a seal can only travel as far as the page minus its
    // own footprint before it starts hanging off the sheet. The frontend clamps
    // the same way (SEAL_PAGE / sealEdgeRange) — keep the two in step.
    private static final double PAGE_W_MM = 210.0;
    private static final double PAGE_H_MM = 297.0;
    private static final double STAMP_ASPECT_H = 0.4; // rubber stamp is 2.5:1

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final BusinessMapper businessMapper;
    private final ObjectMapper objectMapper;

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
        if (request.getAdditionalPhone() != null) business.setAdditionalPhone(request.getAdditionalPhone());
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
        // Seal placement/size/tilt: null = leave unchanged. Existing values are
        // merged with the incoming ones (a partial payload only moves the
        // fields it sends), then clamped and stored as canonical JSON.
        if (request.getSealConfig() != null) {
            business.setSealConfig(normaliseSealConfig(request.getSealConfig(), business.getSealConfig()));
        }

        // Default Terms & Conditions (V40): null = leave unchanged, an empty
        // list CLEARS them. Lines are trimmed, blanks dropped and capped at
        // five before being stored as canonical JSON.
        if (request.getTermsConditions() != null) {
            business.setTermsConditions(serializeTermsConditions(request.getTermsConditions()));
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
     * Serialises the owner's default Terms & Conditions (V40): trims each
     * line, drops blank lines, caps at five (the invoice footer renders five)
     * and converts to a JSON array of strings. An empty input returns null,
     * which clears the column so renderers omit the block entirely.
     */
    private String serializeTermsConditions(java.util.List<String> raw) {
        java.util.List<String> terms = (raw == null)
                ? java.util.List.of()
                : raw.stream()
                        .filter(line -> line != null)
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .limit(5)
                        .toList();
        if (terms.isEmpty()) {
            return null;
        }
        // A "line" is meant to be a short footer bullet; without a cap a
        // single 10 MB string would bloat the row and every PDF that later
        // renders it. 200 characters is about two printed sentences.
        for (String line : terms) {
            if (line.length() > 200) {
                throw new BadRequestException("Each terms & conditions line must be 200 characters or fewer");
            }
        }
        try {
            return objectMapper.writeValueAsString(terms);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise terms & conditions", e);
        }
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

    /**
     * Merges incoming seal placement into the stored configuration and returns
     * canonical JSON. Every variant always ends up with all four values so the
     * renderers never have to guess; values are clamped to sensible page-safe
     * ranges (a seal larger than the page or a 400° tilt is never useful).
     */
    private String normaliseSealConfig(SealConfig incoming, String existingJson) {
        SealConfig base = readSealConfig(existingJson);
        SealConfig merged = SealConfig.builder()
                // Default OFF: a fresh business stamps only the final page (the
                // one carrying the grand total + signature). Owners opt into
                // repeating the mark on every continuation page.
                .allPages(pickBoolean(incoming.getAllPages(), base.getAllPages(), false))
                // Signature size rides the same config: null = keep the stored
                // value (the built-in default is 78px).
                .signatureSize((int) pick(
                        incoming.getSignatureSize() == null ? null : incoming.getSignatureSize().doubleValue(),
                        base.getSignatureSize() == null ? null : base.getSignatureSize().doubleValue(),
                        78.0))
                .round(mergeVariant(base.getRound(), incoming.getRound(), true))
                .stamp(mergeVariant(base.getStamp(), incoming.getStamp(), false))
                .signature(mergeSignature(base.getSignature(), incoming.getSignature()))
                .build();
        try {
            return objectMapper.writeValueAsString(merged);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid seal configuration.");
        }
    }

    /**
     * Signature placement merge over the raw JSON node:
     *   absent field        -> keep the stored placement (seal-only saves
     *                          never disturb it);
     *   explicit null/empty -> REMOVE (back to inline rendering);
     *   object              -> upsert, clamped to the sheet.
     */
    private com.fasterxml.jackson.databind.JsonNode mergeSignature(
            com.fasterxml.jackson.databind.JsonNode currentJson,
            com.fasterxml.jackson.databind.JsonNode incoming) {
        SealConfig.Variant current = currentJson == null || currentJson.isNull()
                ? null
                : objectMapper.convertValue(currentJson, SealConfig.Variant.class);
        if (incoming == null || incoming.isMissingNode()) {
            return currentJson;
        }
        if (incoming.isNull() || (incoming.isObject() && incoming.isEmpty())) {
            return null;
        }
        SealConfig.Variant src = objectMapper.convertValue(incoming, SealConfig.Variant.class);
        double size = pick(src.getSizeMm(), current == null ? null : current.getSizeMm(), 38.0);
        double tilt = pick(src.getTilt(), current == null ? null : current.getTilt(), 0.0);
        double right = pick(src.getRightMm(), current == null ? null : current.getRightMm(), 16.0);
        double bottom = pick(src.getBottomMm(), current == null ? null : current.getBottomMm(), 10.0);
        return objectMapper.valueToTree(SealConfig.Variant.builder()
                .sizeMm(roundTo2(clamp(size, 10, 140)))
                .tilt(roundTo2(clamp(tilt, -45, 45)))
                .rightMm(roundTo2(clamp(right, -10, 210)))
                .bottomMm(roundTo2(clamp(bottom, -10, 297)))
                .build());
    }

    private SealConfig readSealConfig(String json) {
        if (json == null || json.isBlank()) {
            return SealConfig.builder().build();
        }
        try {
            return objectMapper.readValue(json, SealConfig.class);
        } catch (Exception e) {
            log.warn("Ignoring unreadable seal_config on business: {}", e.getMessage());
            return SealConfig.builder().build();
        }
    }

    private SealConfig.Variant mergeVariant(SealConfig.Variant current, SealConfig.Variant incoming, boolean round) {
        double defaultSize = round ? DEFAULT_ROUND_SIZE_MM : DEFAULT_STAMP_SIZE_MM;
        double defaultTilt = round ? DEFAULT_ROUND_TILT_DEG : DEFAULT_STAMP_TILT_DEG;

        double size = pick(incoming == null ? null : incoming.getSizeMm(),
                current == null ? null : current.getSizeMm(), defaultSize);
        double tilt = pick(incoming == null ? null : incoming.getTilt(),
                current == null ? null : current.getTilt(), defaultTilt);
        double right = pick(incoming == null ? null : incoming.getRightMm(),
                current == null ? null : current.getRightMm(), DEFAULT_RIGHT_MM);
        double bottom = pick(incoming == null ? null : incoming.getBottomMm(),
                current == null ? null : current.getBottomMm(), DEFAULT_BOTTOM_MM);

        double sizeMm = roundTo2(clamp(size, round ? 5 : 10, round ? 120 : 180));
        // The stamp's height follows its 2.5:1 artwork aspect; the round seal is
        // square. Travel is capped by the page minus that footprint, so the mark
        // can reach every point of the sheet but never leaves it.
        double boxH = round ? sizeMm : sizeMm * STAMP_ASPECT_H;

        String defaultColor = round ? DEFAULT_ROUND_COLOR : DEFAULT_STAMP_COLOR;
        String incomingColor = incoming == null ? null : incoming.getColor();
        String currentColor = current == null ? null : current.getColor();
        String color = normalizeColor(incomingColor != null ? incomingColor : currentColor, defaultColor);

        return SealConfig.Variant.builder()
                .sizeMm(sizeMm)
                .tilt(roundTo2(clamp(tilt, -45, 45)))
                .rightMm(roundTo2(clamp(right, 0, Math.max(0, PAGE_W_MM - sizeMm))))
                .bottomMm(roundTo2(clamp(bottom, 0, Math.max(0, PAGE_H_MM - boxH))))
                .color(color)
                .build();
    }

    /**
     * Canonicalises an ink colour to upper-case {@code #RRGGBB}. Accepts the
     * short {@code #RGB} form too; anything that isn't a hex colour (an XSS
     * payload, an empty string, a colour word) is dropped for the default so
     * only a well-formed value ever reaches the SVG renderer.
     */
    private static String normalizeColor(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim();
        if (v.length() == 4 && v.charAt(0) == '#') {
            // #RGB -> #RRGGBB
            v = "#" + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2) + v.charAt(3) + v.charAt(3);
        }
        if (v.length() == 7 && v.charAt(0) == '#') {
            String hex = v.substring(1);
            if (hex.matches("(?i)[0-9a-f]{6}")) {
                return v.toUpperCase();
            }
        }
        return fallback;
    }

    private static double pick(Double primary, Double secondary, double fallback) {
        if (primary != null && Double.isFinite(primary)) return primary;
        if (secondary != null && Double.isFinite(secondary)) return secondary;
        return fallback;
    }

    private static boolean pickBoolean(Boolean primary, Boolean secondary, boolean fallback) {
        if (primary != null) return primary;
        if (secondary != null) return secondary;
        return fallback;
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    private static double roundTo2(double value) {
        return Math.round(value * 100.0) / 100.0;
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
