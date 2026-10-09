package com.insideinvoice.labels.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.labels.dto.request.CreateHazmatLabelRequest;
import com.insideinvoice.labels.dto.request.UpdateHazmatLabelRequest;
import com.insideinvoice.labels.dto.response.HazardClassResponse;
import com.insideinvoice.labels.dto.response.HazmatLabelResponse;
import com.insideinvoice.labels.dto.response.UnNumberResponse;
import com.insideinvoice.labels.entity.HazmatLabel;
import com.insideinvoice.labels.entity.LabelAudit;
import com.insideinvoice.labels.entity.LabelFile;
import com.insideinvoice.labels.entity.UnNumberReference;
import com.insideinvoice.labels.enums.ColorMode;
import com.insideinvoice.labels.enums.LabelAuditAction;
import com.insideinvoice.labels.enums.LabelKind;
import com.insideinvoice.labels.enums.LabelStatus;
import com.insideinvoice.labels.enums.TransportMode;
import com.insideinvoice.labels.hazmat.HazardClass;
import com.insideinvoice.labels.hazmat.HazmatRenderers;
import com.insideinvoice.labels.hazmat.HazmatSpec;
import com.insideinvoice.labels.render.LabelAddress;
import com.insideinvoice.labels.repository.HazmatLabelRepository;
import com.insideinvoice.labels.repository.LabelAuditRepository;
import com.insideinvoice.labels.repository.LabelFileRepository;
import com.insideinvoice.labels.repository.UnNumberReferenceRepository;
import com.insideinvoice.labels.service.HazmatLabelService;
import com.insideinvoice.labels.util.Hashing;
import com.insideinvoice.labels.util.ZplExporter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@lombok.RequiredArgsConstructor
public class HazmatLabelServiceImpl implements HazmatLabelService {

    private static final Logger log = LoggerFactory.getLogger(HazmatLabelServiceImpl.class);

    /** Hard cap on merged labels per bulk-PDF request — bounds heap + request time. */
    private static final int MAX_BULK_PDF = 100;

    private final HazmatLabelRepository hazmatLabelRepository;
    private final LabelFileRepository labelFileRepository;
    private final LabelAuditRepository labelAuditRepository;
    private final UnNumberReferenceRepository unNumberReferenceRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    // ---------------------------------------------------------------- create

    @Override
    @Transactional
    public HazmatLabelResponse create(CreateHazmatLabelRequest request, Long businessId, Long userId, String ip) {
        HazmatLabel e = new HazmatLabel();
        e.setBusinessId(businessId);
        e.setCreatedBy(userId);
        e.setStatus(LabelStatus.DRAFT);
        e.setLabelNumber(nextLabelNumber(businessId));
        applyCreate(e, request);
        hazmatLabelRepository.save(e);
        audit(businessId, e.getId(), LabelAuditAction.CREATED, userId, ip);
        return toResponse(e);
    }

    private void applyCreate(HazmatLabel e, CreateHazmatLabelRequest req) {
        e.setInvoiceId(req.getInvoiceId());
        e.setLabelType(req.getLabelType() != null ? req.getLabelType() : com.insideinvoice.labels.enums.HazmatLabelType.CLASS_DIAMOND);
        e.setLabelSize(req.getLabelSize() != null ? req.getLabelSize() : defaultSize(req));
        e.setColorMode(req.getColorMode() != null ? req.getColorMode() : ColorMode.COLOR);
        e.setTransportMode(req.getTransportMode() != null ? req.getTransportMode() : TransportMode.ROAD);
        e.setUnNumber(req.getUnNumber());
        e.setProperShippingName(req.getProperShippingName());
        e.setTechnicalName(req.getTechnicalName());
        e.setHazardClass(req.getHazardClass());
        e.setDivision(req.getDivision());
        e.setCompatGroup(req.getCompatGroup());
        e.setPackingGroup(req.getPackingGroup());
        e.setSubsidiaryRisks(req.getSubsidiaryRisks());
        e.setNetQuantity(req.getNetQuantity());
        e.setPackageCount(req.getPackageCount() != null ? req.getPackageCount() : 1);
        e.setConsignorJson(writeJson(req.getConsignor()));
        e.setConsigneeJson(writeJson(req.getConsignee()));
        e.setEmergencyPhone(req.getEmergencyPhone());
        e.setGraCode(req.getGraCode());
        e.setErgGuide(req.getErgGuide());
        e.setLithiumWh(req.getLithiumWh());
        e.setNotes(req.getNotes());
        e.setOverpack(Boolean.TRUE.equals(req.getOverpack()));
        e.setMarinePollutant(Boolean.TRUE.equals(req.getMarinePollutant()));
        e.setRadiationCategory(req.getRadiationCategory());
        e.setLimitedQuantityCode(req.getLimitedQuantityCode());
        e.setLithiumUnList(req.getLithiumUnList());
        e.setLithiumPackingInstruction(req.getLithiumPackingInstruction());
        e.setGeneratedBy(req.getGeneratedBy());
    }

    private String defaultSize(CreateHazmatLabelRequest req) {
        try {
            HazmatSpec spec = HazmatSpec.builder()
                    .labelType(req.getLabelType() != null ? req.getLabelType() : com.insideinvoice.labels.enums.HazmatLabelType.CLASS_DIAMOND)
                    .build();
            return spec.size();
        } catch (Exception e) {
            return "hazmat-4x4";
        }
    }

    // ---------------------------------------------------------------- update

    @Override
    @Transactional
    public HazmatLabelResponse update(Long id, UpdateHazmatLabelRequest req, Long businessId, Long userId, String ip) {
        HazmatLabel e = getEntity(id, businessId);
        if (req.getLabelType() != null) e.setLabelType(req.getLabelType());
        if (req.getLabelSize() != null) e.setLabelSize(req.getLabelSize());
        if (req.getColorMode() != null) e.setColorMode(req.getColorMode());
        if (req.getTransportMode() != null) e.setTransportMode(req.getTransportMode());
        if (req.getUnNumber() != null) e.setUnNumber(req.getUnNumber());
        if (req.getProperShippingName() != null) e.setProperShippingName(req.getProperShippingName());
        if (req.getTechnicalName() != null) e.setTechnicalName(req.getTechnicalName());
        if (req.getHazardClass() != null) e.setHazardClass(req.getHazardClass());
        if (req.getDivision() != null) e.setDivision(req.getDivision());
        if (req.getCompatGroup() != null) e.setCompatGroup(req.getCompatGroup());
        if (req.getPackingGroup() != null) e.setPackingGroup(req.getPackingGroup());
        if (req.getSubsidiaryRisks() != null) e.setSubsidiaryRisks(req.getSubsidiaryRisks());
        if (req.getNetQuantity() != null) e.setNetQuantity(req.getNetQuantity());
        if (req.getPackageCount() != null) e.setPackageCount(req.getPackageCount());
        if (req.getConsignor() != null) e.setConsignorJson(writeJson(req.getConsignor()));
        if (req.getConsignee() != null) e.setConsigneeJson(writeJson(req.getConsignee()));
        if (req.getEmergencyPhone() != null) e.setEmergencyPhone(req.getEmergencyPhone());
        if (req.getGraCode() != null) e.setGraCode(req.getGraCode());
        if (req.getErgGuide() != null) e.setErgGuide(req.getErgGuide());
        if (req.getLithiumWh() != null) e.setLithiumWh(req.getLithiumWh());
        if (req.getNotes() != null) e.setNotes(req.getNotes());
        if (req.getOverpack() != null) e.setOverpack(req.getOverpack());
        if (req.getMarinePollutant() != null) e.setMarinePollutant(req.getMarinePollutant());
        if (req.getRadiationCategory() != null) e.setRadiationCategory(req.getRadiationCategory());
        if (req.getLimitedQuantityCode() != null) e.setLimitedQuantityCode(req.getLimitedQuantityCode());
        if (req.getLithiumUnList() != null) e.setLithiumUnList(req.getLithiumUnList());
        if (req.getLithiumPackingInstruction() != null) e.setLithiumPackingInstruction(req.getLithiumPackingInstruction());
        if (req.getGeneratedBy() != null) e.setGeneratedBy(req.getGeneratedBy());

        audit(businessId, id, LabelAuditAction.UPDATED, userId, ip);
        return toResponse(e);
    }

    // ---------------------------------------------------------------- delete / read

    @Override
    @Transactional
    public void delete(Long id, Long businessId, Long userId, String ip) {
        HazmatLabel e = getEntity(id, businessId);
        e.setDeletedAt(OffsetDateTime.now());
        audit(businessId, id, LabelAuditAction.DELETED, userId, ip);
    }

    @Override
    public HazmatLabelResponse get(Long id, Long businessId) {
        return toResponse(getEntity(id, businessId));
    }

    @Override
    public PagedResponse<HazmatLabelResponse> list(Long businessId, int page, int size, String q, String status) {
        Pageable pageable = com.insideinvoice.common.PageParams.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<HazmatLabel> result = hazmatLabelRepository.search(
                businessId, parseStatus(status), q == null || q.isBlank() ? "" : q, pageable);
        List<HazmatLabelResponse> content = new ArrayList<>();
        for (HazmatLabel e : result.getContent()) {
            content.add(toResponse(e));
        }
        return PagedResponse.<HazmatLabelResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .first(result.isFirst())
                .build();
    }

    // ---------------------------------------------------------------- preview

    @Override
    public byte[] preview(CreateHazmatLabelRequest request) throws Exception {
        HazmatSpec spec = specFromRequest(request);
        return HazmatRenderers.forSpec(spec).render(spec);
    }

    @Override
    public byte[] previewById(Long id, Long businessId) throws Exception {
        HazmatLabel e = getEntity(id, businessId);
        HazmatSpec spec = specFrom(e);
        return HazmatRenderers.forSpec(spec).render(spec);
    }

    // ---------------------------------------------------------------- pdf

    @Override
    public byte[] generatePdf(Long id, Long businessId, Long userId, String ip) throws Exception {
        // 1. Short read tx: ownership check + build the render spec (detached POJO).
        HazmatSpec spec = transactionTemplate.execute(status -> specFrom(getEntity(id, businessId)));

        // 2. Render OUTSIDE any transaction (CPU-heavy; must not pin a DB connection).
        byte[] pdf = HazmatRenderers.forSpec(spec).render(spec);
        String sha = Hashing.sha256Hex(pdf);

        // 3. Short write tx: persist the blob + status + audit row.
        transactionTemplate.executeWithoutResult(status -> {
            HazmatLabel e = getEntity(id, businessId); // re-fetch: prior tx detached it
            LabelFile file = labelFileRepository.findByLabelIdAndLabelType(id, LabelKind.HAZMAT).orElse(null);
            if (file == null) {
                file = LabelFile.builder()
                        .labelId(id)
                        .labelType(LabelKind.HAZMAT)
                        .build();
            }
            file.setPdfBytes(pdf);
            file.setPdfSha256(sha);
            file.setCreatedAt(OffsetDateTime.now());
            file.setUpdatedAt(OffsetDateTime.now());
            labelFileRepository.save(file);
            e.setPdfSha256(sha);
            e.setStatus(LabelStatus.GENERATED);
            audit(businessId, id, LabelAuditAction.REGENERATED, userId, ip);
        });
        return pdf;
    }

    @Override
    public byte[] getPdf(Long id, Long businessId) throws Exception {
        // Ownership check + cached-blob read in a short tx. label_files has no
        // businessId column, so getEntity() must run first to prevent cross-tenant leaks.
        byte[] cached = transactionTemplate.execute(status -> {
            getEntity(id, businessId);
            LabelFile file = labelFileRepository.findByLabelIdAndLabelType(id, LabelKind.HAZMAT).orElse(null);
            return file != null ? file.getPdfBytes() : null;
        });
        if (cached != null) {
            return cached;
        }
        return generatePdf(id, businessId, null, null);
    }

    @Override
    public byte[] bulkPdf(List<Long> ids, Long businessId, Long userId, String ip) throws Exception {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("At least one label id is required");
        }
        List<Long> capped = ids.size() > MAX_BULK_PDF ? ids.subList(0, MAX_BULK_PDF) : ids;
        try (PDDocument out = new PDDocument()) {
            for (Long id : capped) {
                // getPdf keeps each iteration's DB work in a short tx and renders outside it.
                byte[] pdf = getPdf(id, businessId);
                try (PDDocument in = Loader.loadPDF(pdf)) {
                    for (int p = 0; p < in.getNumberOfPages(); p++) {
                        out.importPage(in.getPage(p));
                    }
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            out.save(bos);
            return bos.toByteArray();
        }
    }

    @Override
    public String zpl(Long id, Long businessId) throws Exception {
        return ZplExporter.hazmat(specFrom(getEntity(id, businessId)));
    }

    @Override
    @Transactional
    public HazmatLabelResponse markPrinted(Long id, Long businessId, Long userId, String ip) {
        HazmatLabel e = getEntity(id, businessId);
        e.setStatus(LabelStatus.PRINTED);
        audit(businessId, id, LabelAuditAction.PRINTED, userId, ip);
        return toResponse(e);
    }

    // ---------------------------------------------------------------- unNumbers / classes

    @Override
    public List<UnNumberResponse> unNumbers(String q) {
        String needle = q == null || q.isBlank() ? "" : q;
        List<UnNumberReference> refs = unNumberReferenceRepository.search(needle);
        List<UnNumberResponse> result = new ArrayList<>();
        int limit = 20;
        for (UnNumberReference r : refs) {
            if (result.size() >= limit) {
                break;
            }
            result.add(UnNumberResponse.builder()
                    .unNumber(r.getUnNumber())
                    .properShippingName(r.getProperShippingName())
                    .hazardClass(r.getHazardClass())
                    .division(r.getDivision())
                    .compatGroup(r.getCompatGroup())
                    .packingGroup(r.getPackingGroup())
                    .subsidiaryRisks(r.getSubsidiaryRisks())
                    .ergGuide(r.getErgGuide())
                    .ltdQty(r.getLtdQty())
                    .paxAllowed(r.getPaxAllowed())
                    .caoAllowed(r.getCaoAllowed())
                    .specialProvisions(r.getSpecialProvisions())
                    .build());
        }
        return result;
    }

    @Override
    public List<HazardClassResponse> classes() {
        List<HazardClassResponse> result = new ArrayList<>();
        for (HazardClass c : HazardClass.values()) {
            result.add(HazardClassResponse.builder()
                    .key(c.key())
                    .className(c.className())
                    .hex("#" + c.hex())
                    .pattern(c.pattern().name())
                    .symbol(c.symbol().name())
                    .textColor(c.textColor())
                    .numeral(c.numeral())
                    .cmyk(c.cmyk())
                    .rgb(c.rgb())
                    .build());
        }
        return result;
    }

    // ================================================================== helpers

    private HazmatLabel getEntity(Long id, Long businessId) {
        return hazmatLabelRepository.findByIdAndBusinessIdAndDeletedAtIsNull(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("HazmatLabel", "id", id));
    }

    private HazmatSpec specFrom(HazmatLabel e) {
        return HazmatSpec.builder()
                .labelNumber(e.getLabelNumber())
                .labelType(e.getLabelType())
                .labelSize(e.getLabelSize())
                .colorMode(e.getColorMode())
                .transportMode(e.getTransportMode())
                .unNumber(e.getUnNumber())
                .properShippingName(e.getProperShippingName())
                .technicalName(e.getTechnicalName())
                .hazardClass(e.getHazardClass())
                .division(e.getDivision())
                .compatGroup(e.getCompatGroup())
                .packingGroup(e.getPackingGroup())
                .subsidiaryRisks(e.getSubsidiaryRisks())
                .netQuantity(e.getNetQuantity())
                .packageCount(e.getPackageCount())
                .consignor(readJsonSafe(e.getConsignorJson(), LabelAddress.class))
                .consignee(readJsonSafe(e.getConsigneeJson(), LabelAddress.class))
                .emergencyPhone(e.getEmergencyPhone())
                .graCode(e.getGraCode())
                .ergGuide(e.getErgGuide())
                .lithiumWh(e.getLithiumWh())
                .notes(e.getNotes())
                .overpack(Boolean.TRUE.equals(e.getOverpack()))
                .marinePollutant(Boolean.TRUE.equals(e.getMarinePollutant()))
                .radiationCategory(e.getRadiationCategory())
                .limitedQuantityCode(e.getLimitedQuantityCode())
                .lithiumUnList(e.getLithiumUnList())
                .lithiumPackingInstruction(e.getLithiumPackingInstruction())
                .generatedBy(e.getGeneratedBy())
                .build();
    }

    private HazmatSpec specFromRequest(CreateHazmatLabelRequest request) {
        return HazmatSpec.builder()
                .labelNumber(request.getInvoiceId() != null ? "HZL-INV-" + request.getInvoiceId() : "PREVIEW")
                .labelType(request.getLabelType() != null ? request.getLabelType() : com.insideinvoice.labels.enums.HazmatLabelType.CLASS_DIAMOND)
                .labelSize(request.getLabelSize())
                .colorMode(request.getColorMode() != null ? request.getColorMode() : ColorMode.COLOR)
                .transportMode(request.getTransportMode() != null ? request.getTransportMode() : TransportMode.ROAD)
                .unNumber(request.getUnNumber())
                .properShippingName(request.getProperShippingName())
                .technicalName(request.getTechnicalName())
                .hazardClass(request.getHazardClass())
                .division(request.getDivision())
                .compatGroup(request.getCompatGroup())
                .packingGroup(request.getPackingGroup())
                .subsidiaryRisks(request.getSubsidiaryRisks())
                .netQuantity(request.getNetQuantity())
                .packageCount(request.getPackageCount())
                .consignor(request.getConsignor())
                .consignee(request.getConsignee())
                .emergencyPhone(request.getEmergencyPhone())
                .graCode(request.getGraCode())
                .ergGuide(request.getErgGuide())
                .lithiumWh(request.getLithiumWh())
                .notes(request.getNotes())
                .overpack(Boolean.TRUE.equals(request.getOverpack()))
                .marinePollutant(Boolean.TRUE.equals(request.getMarinePollutant()))
                .radiationCategory(request.getRadiationCategory())
                .limitedQuantityCode(request.getLimitedQuantityCode())
                .lithiumUnList(request.getLithiumUnList())
                .lithiumPackingInstruction(request.getLithiumPackingInstruction())
                .generatedBy(request.getGeneratedBy())
                .build();
    }

    private HazmatLabelResponse toResponse(HazmatLabel e) {
        boolean hasPdf = labelFileRepository.existsByLabelIdAndLabelType(e.getId(), LabelKind.HAZMAT);
        return HazmatLabelResponse.builder()
                .id(e.getId())
                .labelNumber(e.getLabelNumber())
                .invoiceId(e.getInvoiceId())
                .labelType(e.getLabelType())
                .labelSize(e.getLabelSize())
                .colorMode(e.getColorMode())
                .transportMode(e.getTransportMode())
                .unNumber(e.getUnNumber())
                .properShippingName(e.getProperShippingName())
                .technicalName(e.getTechnicalName())
                .hazardClass(e.getHazardClass())
                .division(e.getDivision())
                .compatGroup(e.getCompatGroup())
                .packingGroup(e.getPackingGroup())
                .subsidiaryRisks(e.getSubsidiaryRisks())
                .netQuantity(e.getNetQuantity())
                .packageCount(e.getPackageCount())
                .consignor(readJsonSafe(e.getConsignorJson(), LabelAddress.class))
                .consignee(readJsonSafe(e.getConsigneeJson(), LabelAddress.class))
                .emergencyPhone(e.getEmergencyPhone())
                .graCode(e.getGraCode())
                .ergGuide(e.getErgGuide())
                .lithiumWh(e.getLithiumWh())
                .notes(e.getNotes())
                .overpack(e.getOverpack())
                .marinePollutant(e.getMarinePollutant())
                .radiationCategory(e.getRadiationCategory())
                .limitedQuantityCode(e.getLimitedQuantityCode())
                .lithiumUnList(e.getLithiumUnList())
                .lithiumPackingInstruction(e.getLithiumPackingInstruction())
                .status(e.getStatus())
                .pdfSha256(e.getPdfSha256())
                .hasPdf(hasPdf)
                .generatedBy(e.getGeneratedBy())
                .createdBy(e.getCreatedBy())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private void audit(Long businessId, Long labelId, LabelAuditAction action, Long userId, String ip) {
        labelAuditRepository.save(LabelAudit.builder()
                .businessId(businessId)
                .labelId(labelId)
                .labelType(LabelKind.HAZMAT)
                .action(action)
                .userId(userId)
                .ip(ip)
                .createdAt(OffsetDateTime.now())
                .build());
    }

    private String nextLabelNumber(Long businessId) {
        long count = hazmatLabelRepository.countByBusinessIdAndDeletedAtIsNull(businessId);
        int year = java.time.Year.now().getValue();
        for (long i = 1; i <= 1000; i++) {
            String candidate = String.format("HZL-%d-%04d", year, count + i);
            if (!hazmatLabelRepository.existsByBusinessIdAndLabelNumber(businessId, candidate)) {
                return candidate;
            }
        }
        return "HZL-" + businessId + "-" + System.currentTimeMillis();
    }

    private LabelStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return LabelStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    private String writeJson(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("json", e);
        }
    }

    private <T> T readJsonSafe(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }
}
