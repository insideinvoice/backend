package com.insideinvoice.labels.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import com.insideinvoice.labels.dto.StoredShippingRefs;
import com.insideinvoice.labels.dto.request.CreateShippingLabelRequest;
import com.insideinvoice.labels.dto.request.UpdateShippingLabelRequest;
import com.insideinvoice.labels.dto.response.ShippingLabelResponse;
import com.insideinvoice.labels.entity.LabelAudit;
import com.insideinvoice.labels.entity.LabelFile;
import com.insideinvoice.labels.entity.ShippingLabel;
import com.insideinvoice.labels.enums.LabelAuditAction;
import com.insideinvoice.labels.enums.LabelKind;
import com.insideinvoice.labels.enums.LabelPreset;
import com.insideinvoice.labels.enums.LabelStatus;
import com.insideinvoice.labels.render.LabelAddress;
import com.insideinvoice.labels.render.ShippingLabelSpec;
import com.insideinvoice.labels.render.ShippingRenderers;
import com.insideinvoice.labels.repository.LabelAuditRepository;
import com.insideinvoice.labels.repository.LabelFileRepository;
import com.insideinvoice.labels.repository.ShippingLabelRepository;
import com.insideinvoice.labels.service.ShippingLabelService;
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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@lombok.RequiredArgsConstructor
public class ShippingLabelServiceImpl implements ShippingLabelService {

    private static final Logger log = LoggerFactory.getLogger(ShippingLabelServiceImpl.class);

    /** Hard cap on merged labels per bulk-PDF request — bounds heap + request time. */
    private static final int MAX_BULK_PDF = 100;

    private final ShippingLabelRepository shippingLabelRepository;
    private final LabelFileRepository labelFileRepository;
    private final LabelAuditRepository labelAuditRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final BusinessRepository businessRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    // ---------------------------------------------------------------- create

    @Override
    @Transactional
    public ShippingLabelResponse create(CreateShippingLabelRequest request, Long businessId, Long userId, String ip) {
        // tenant boundary: a label may only be linked to an invoice owned by this
        // business. Without this, any invoice id (including another tenant's) was
        // stored and echoed back in responses.
        if (request.getInvoiceId() != null) {
            invoiceRepository.findByIdAndBusinessId(request.getInvoiceId(), businessId)
                    .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", request.getInvoiceId()));
        }
        ShippingLabel e = new ShippingLabel();
        e.setBusinessId(businessId);
        e.setCreatedBy(userId);
        e.setStatus(LabelStatus.DRAFT);
        e.setLabelNumber(nextLabelNumber(businessId));
        e.setCartonIndex(1);
        e.setCartonTotal(1);
        applyCreate(e, request);
        shippingLabelRepository.save(e);
        audit(businessId, e.getId(), LabelAuditAction.CREATED, userId, ip);
        return toResponse(e);
    }

    private void applyCreate(ShippingLabel e, CreateShippingLabelRequest req) {
        e.setInvoiceId(req.getInvoiceId());
        e.setPreset(req.getPreset() != null ? req.getPreset() : LabelPreset.STANDARD_CARRIER_4X6);
        e.setLabelSize(req.getSizeKey() != null ? req.getSizeKey() : com.insideinvoice.labels.render.LabelSizes.DEFAULT_SHIPPING);
        e.setDpi(req.getDpi() != null ? req.getDpi() : 203);
        e.setThermalMode(req.getThermalMode() == null || req.getThermalMode());
        e.setPrintBorder(Boolean.TRUE.equals(req.getPrintBorder()));
        e.setFromJson(writeJson(req.getShipFrom()));
        e.setToJson(writeJson(req.getShipTo()));
        e.setCarrier(req.getCarrier());
        e.setServiceLevel(req.getServiceLevel());
        e.setServiceCode(req.getServiceCode());
        e.setTrackingNumber(req.getTrackingNumber());
        e.setShipDate(req.getShipDate());
        e.setPackageWeightKg(parseKg(req.getWeightKg()));
        e.setDimsCm(req.getDimsCm());
        int cartons = req.getCartonCount() == null || req.getCartonCount() < 1 ? 1 : Math.min(req.getCartonCount(), 999);
        e.setCartonTotal(cartons);
        e.setReferenceFields(writeJson(refsFrom(req)));
        e.setFbaJson(writeJson(req.getFba()));
        e.setGs1Json(writeJson(req.getGs1()));
        e.setFnskuCopies(req.getFnskuCopies());
        e.setFnskuItemsJson(writeJson(req.getFnskuItems()));
        e.setGeneratedBy(req.getGeneratedBy());
    }

    // ---------------------------------------------------------------- update

    @Override
    @Transactional
    public ShippingLabelResponse update(Long id, UpdateShippingLabelRequest req, Long businessId, Long userId, String ip) {
        ShippingLabel e = getEntity(id, businessId);
        if (req.getPreset() != null) e.setPreset(req.getPreset());
        if (req.getSizeKey() != null) e.setLabelSize(req.getSizeKey());
        if (req.getDpi() != null) e.setDpi(req.getDpi());
        if (req.getThermalMode() != null) e.setThermalMode(req.getThermalMode());
        if (req.getPrintBorder() != null) e.setPrintBorder(req.getPrintBorder());
        if (req.getShipFrom() != null) e.setFromJson(writeJson(req.getShipFrom()));
        if (req.getShipTo() != null) e.setToJson(writeJson(req.getShipTo()));
        if (req.getCarrier() != null) e.setCarrier(req.getCarrier());
        if (req.getServiceLevel() != null) e.setServiceLevel(req.getServiceLevel());
        if (req.getServiceCode() != null) e.setServiceCode(req.getServiceCode());
        if (req.getTrackingNumber() != null) e.setTrackingNumber(req.getTrackingNumber());
        if (req.getShipDate() != null) e.setShipDate(req.getShipDate());
        if (req.getWeightKg() != null) e.setPackageWeightKg(parseKg(req.getWeightKg()));
        if (req.getDimsCm() != null) e.setDimsCm(req.getDimsCm());
        if (req.getCartonCount() != null) e.setCartonTotal(Math.max(1, Math.min(req.getCartonCount(), 999)));
        if (req.getFba() != null) e.setFbaJson(writeJson(req.getFba()));
        if (req.getGs1() != null) e.setGs1Json(writeJson(req.getGs1()));
        if (req.getFnskuCopies() != null) e.setFnskuCopies(req.getFnskuCopies());
        if (req.getFnskuItems() != null) e.setFnskuItemsJson(writeJson(req.getFnskuItems()));
        if (req.getGeneratedBy() != null) e.setGeneratedBy(req.getGeneratedBy());

        StoredShippingRefs refs = readRefs(e);
        if (req.getBillingType() != null) refs.setBillingType(req.getBillingType());
        if (req.getCodAmount() != null) refs.setCodAmount(req.getCodAmount());
        if (req.getInvoiceNo() != null) refs.setInvoiceNo(req.getInvoiceNo());
        if (req.getPoNumber() != null) refs.setPoNumber(req.getPoNumber());
        if (req.getRef1() != null) refs.setRef1(req.getRef1());
        if (req.getRef2() != null) refs.setRef2(req.getRef2());
        if (req.getNotes() != null) refs.setNotes(req.getNotes());
        if (req.getThisWayUp() != null) refs.setThisWayUp(req.getThisWayUp());
        if (req.getFragile() != null) refs.setFragile(req.getFragile());
        if (req.getKeepDry() != null) refs.setKeepDry(req.getKeepDry());
        if (req.getDoNotStack() != null) refs.setDoNotStack(req.getDoNotStack());
        if (req.getHandleWithCare() != null) refs.setHandleWithCare(req.getHandleWithCare());
        e.setReferenceFields(writeJson(refs));

        audit(businessId, id, LabelAuditAction.UPDATED, userId, ip);
        return toResponse(e);
    }

    // ---------------------------------------------------------------- delete

    @Override
    @Transactional
    public void delete(Long id, Long businessId, Long userId, String ip) {
        ShippingLabel e = getEntity(id, businessId);
        e.setDeletedAt(OffsetDateTime.now());
        audit(businessId, id, LabelAuditAction.DELETED, userId, ip);
    }

    // ---------------------------------------------------------------- read

    @Override
    public ShippingLabelResponse get(Long id, Long businessId) {
        return toResponse(getEntity(id, businessId));
    }

    @Override
    public PagedResponse<ShippingLabelResponse> list(Long businessId, int page, int size, String q,
                                                     String status, String carrier, Long invoiceId) {
        Pageable pageable = com.insideinvoice.common.PageParams.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ShippingLabel> result = shippingLabelRepository.search(
                businessId, parseStatus(status), carrier == null || carrier.isBlank() ? "" : carrier, invoiceId, q == null || q.isBlank() ? "" : q, pageable);
        List<ShippingLabelResponse> content = new ArrayList<>();
        for (ShippingLabel e : result.getContent()) {
            content.add(toResponse(e));
        }
        return PagedResponse.<ShippingLabelResponse>builder()
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
    public byte[] preview(CreateShippingLabelRequest request) throws Exception {
        ShippingLabelSpec spec = fromCreateRequest(request);
        return ShippingRenderers.forPreset(spec.effectivePreset()).render(spec);
    }

    @Override
    public byte[] previewById(Long id, Long businessId) throws Exception {
        ShippingLabel e = getEntity(id, businessId);
        ShippingLabelSpec spec = specFrom(e);
        return ShippingRenderers.forPreset(spec.effectivePreset()).render(spec);
    }

    // ---------------------------------------------------------------- pdf

    @Override
    public byte[] generatePdf(Long id, Long businessId, Long userId, String ip) throws Exception {
        // 1. Short read tx: ownership check + build the render spec. The spec is a
        //    detached POJO of scalar fields, safe to use after the tx closes.
        ShippingLabelSpec spec = transactionTemplate.execute(status -> specFrom(getEntity(id, businessId)));

        // 2. Render OUTSIDE any transaction. PDFBox layout/font-embedding is CPU-heavy
        //    and must not pin one of the small Hikari connections while it runs.
        byte[] pdf = ShippingRenderers.forPreset(spec.effectivePreset()).render(spec);
        String sha = Hashing.sha256Hex(pdf);

        // 3. Short write tx: persist the blob + status + audit row.
        transactionTemplate.executeWithoutResult(status -> {
            ShippingLabel e = getEntity(id, businessId); // re-fetch: prior tx detached it
            LabelFile file = labelFileRepository.findByLabelIdAndLabelType(id, LabelKind.SHIPPING).orElse(null);
            if (file == null) {
                file = LabelFile.builder()
                        .labelId(id)
                        .labelType(LabelKind.SHIPPING)
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
            LabelFile file = labelFileRepository.findByLabelIdAndLabelType(id, LabelKind.SHIPPING).orElse(null);
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

    // ---------------------------------------------------------------- zpl

    @Override
    public String zpl(Long id, Long businessId) throws Exception {
        return ZplExporter.shipping(specFrom(getEntity(id, businessId)));
    }

    // ---------------------------------------------------------------- printed

    @Override
    @Transactional
    public ShippingLabelResponse markPrinted(Long id, Long businessId, Long userId, String ip) {
        ShippingLabel e = getEntity(id, businessId);
        e.setStatus(LabelStatus.PRINTED);
        audit(businessId, id, LabelAuditAction.PRINTED, userId, ip);
        return toResponse(e);
    }

    // ---------------------------------------------------------------- fromInvoice

    @Override
    @Transactional
    public ShippingLabelResponse fromInvoice(Long invoiceId, Long businessId, Long userId, String ip) {
        Invoice inv = invoiceRepository.findByIdAndBusinessId(invoiceId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        Customer customer = customerRepository.findByIdAndBusinessId(inv.getCustomerId(), businessId).orElse(null);
        Business business = businessRepository.findById(businessId).orElse(null);

        ShippingLabel e = new ShippingLabel();
        e.setBusinessId(businessId);
        e.setInvoiceId(invoiceId);
        e.setCreatedBy(userId);
        e.setStatus(LabelStatus.DRAFT);
        e.setLabelNumber(nextLabelNumber(businessId));
        e.setPreset(LabelPreset.STANDARD_CARRIER_4X6);
        e.setLabelSize(com.insideinvoice.labels.render.LabelSizes.DEFAULT_SHIPPING);
        e.setDpi(203);
        e.setThermalMode(true);
        e.setPrintBorder(false);
        e.setCartonIndex(1);
        e.setCartonTotal(1);

        if (customer != null) {
            List<String> lines = customer.getShippingAddress() != null && !customer.getShippingAddress().isBlank()
                    ? Arrays.asList(customer.getShippingAddress().split("\\r?\\n"))
                    : Collections.emptyList();
            e.setToJson(writeJson(LabelAddress.builder()
                    .name(customer.getName())
                    .addressLines(lines)
                    .city(customer.getCity())
                    .state(customer.getState())
                    .pincode(customer.getPincode())
                    .country(customer.getCountry())
                    .phone(customer.getPhone())
                    .build()));
        }
        if (business != null) {
            List<String> lines = new ArrayList<>();
            if (business.getAddressLine1() != null && !business.getAddressLine1().isBlank()) {
                lines.add(business.getAddressLine1());
            }
            if (business.getAddressLine2() != null && !business.getAddressLine2().isBlank()) {
                lines.add(business.getAddressLine2());
            }
            e.setFromJson(writeJson(LabelAddress.builder()
                    .name(business.getBusinessName())
                    .company(business.getBusinessName())
                    .addressLines(lines)
                    .city(business.getCity())
                    .state(business.getState())
                    .pincode(business.getPincode())
                    .country(business.getCountry())
                    .phone(business.getPhone())
                    .build()));
        }
        StoredShippingRefs refs = StoredShippingRefs.builder()
                .invoiceNo(inv.getInvoiceNumber())
                .poNumber(inv.getBuyerOrderNumber())
                .notes(inv.getNotes())
                .build();
        e.setReferenceFields(writeJson(refs));
        shippingLabelRepository.save(e);
        audit(businessId, e.getId(), LabelAuditAction.CREATED, userId, ip);
        return toResponse(e);
    }

    // ================================================================== helpers

    private ShippingLabel getEntity(Long id, Long businessId) {
        return shippingLabelRepository.findByIdAndBusinessIdAndDeletedAtIsNull(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("ShippingLabel", "id", id));
    }

    private ShippingLabelSpec specFrom(ShippingLabel e) {
        StoredShippingRefs refs = readRefs(e);
        return ShippingLabelSpec.builder()
                .labelNumber(e.getLabelNumber())
                .preset(e.getPreset())
                .sizeKey(e.getLabelSize())
                .dpi(e.getDpi())
                .thermalMode(e.getThermalMode())
                .printBorder(e.getPrintBorder())
                .shipFrom(readJsonSafe(e.getFromJson(), LabelAddress.class))
                .shipTo(readJsonSafe(e.getToJson(), LabelAddress.class))
                .carrier(e.getCarrier())
                .serviceLevel(e.getServiceLevel())
                .serviceCode(e.getServiceCode())
                .trackingNumber(e.getTrackingNumber())
                .shipDate(e.getShipDate())
                .weightKg(e.getPackageWeightKg() == null ? null : e.getPackageWeightKg().toPlainString())
                .dimsCm(e.getDimsCm())
                .cartonCount(e.getCartonTotal())
                .billingType(refs.getBillingType())
                .codAmount(refs.getCodAmount())
                .invoiceNo(refs.getInvoiceNo())
                .poNumber(refs.getPoNumber())
                .ref1(refs.getRef1())
                .ref2(refs.getRef2())
                .notes(refs.getNotes())
                .thisWayUp(Boolean.TRUE.equals(refs.getThisWayUp()))
                .fragile(Boolean.TRUE.equals(refs.getFragile()))
                .keepDry(Boolean.TRUE.equals(refs.getKeepDry()))
                .doNotStack(Boolean.TRUE.equals(refs.getDoNotStack()))
                .handleWithCare(Boolean.TRUE.equals(refs.getHandleWithCare()))
                .fba(readJsonSafe(e.getFbaJson(), ShippingLabelSpec.FbaFields.class))
                .gs1(readJsonSafe(e.getGs1Json(), ShippingLabelSpec.Gs1Fields.class))
                .fnskuCopies(e.getFnskuCopies())
                .fnskuItems(readJsonListSafe(e.getFnskuItemsJson(), ShippingLabelSpec.FnskuItem.class))
                .generatedBy(e.getGeneratedBy())
                .build();
    }

    private ShippingLabelSpec fromCreateRequest(CreateShippingLabelRequest request) {
        ShippingLabelSpec spec = ShippingLabelSpec.builder()
                .labelNumber(request.getInvoiceNo() != null ? request.getInvoiceNo() : "PREVIEW")
                .preset(request.getPreset() != null ? request.getPreset() : LabelPreset.STANDARD_CARRIER_4X6)
                .sizeKey(request.getSizeKey())
                .dpi(request.getDpi())
                .thermalMode(request.getThermalMode())
                .printBorder(request.getPrintBorder())
                .shipFrom(request.getShipFrom())
                .shipTo(request.getShipTo())
                .carrier(request.getCarrier())
                .serviceLevel(request.getServiceLevel())
                .serviceCode(request.getServiceCode())
                .trackingNumber(request.getTrackingNumber())
                .shipDate(request.getShipDate())
                .weightKg(request.getWeightKg())
                .dimsCm(request.getDimsCm())
                .cartonCount(request.getCartonCount())
                .billingType(request.getBillingType())
                .codAmount(request.getCodAmount())
                .invoiceNo(request.getInvoiceNo())
                .poNumber(request.getPoNumber())
                .ref1(request.getRef1())
                .ref2(request.getRef2())
                .notes(request.getNotes())
                .thisWayUp(Boolean.TRUE.equals(request.getThisWayUp()))
                .fragile(Boolean.TRUE.equals(request.getFragile()))
                .keepDry(Boolean.TRUE.equals(request.getKeepDry()))
                .doNotStack(Boolean.TRUE.equals(request.getDoNotStack()))
                .handleWithCare(Boolean.TRUE.equals(request.getHandleWithCare()))
                .fba(request.getFba())
                .gs1(request.getGs1())
                .fnskuCopies(request.getFnskuCopies())
                .fnskuItems(request.getFnskuItems() != null ? request.getFnskuItems() : List.of())
                .generatedBy(request.getGeneratedBy())
                .build();
        return spec;
    }

    private ShippingLabelResponse toResponse(ShippingLabel e) {
        StoredShippingRefs refs = readRefs(e);
        boolean hasPdf = labelFileRepository.existsByLabelIdAndLabelType(e.getId(), LabelKind.SHIPPING);
        return ShippingLabelResponse.builder()
                .id(e.getId())
                .labelNumber(e.getLabelNumber())
                .invoiceId(e.getInvoiceId())
                .preset(e.getPreset())
                .labelSize(e.getLabelSize())
                .dpi(e.getDpi())
                .carrier(e.getCarrier())
                .serviceLevel(e.getServiceLevel())
                .serviceCode(e.getServiceCode())
                .trackingNumber(e.getTrackingNumber())
                .shipDate(e.getShipDate())
                .shipFrom(readJsonSafe(e.getFromJson(), LabelAddress.class))
                .shipTo(readJsonSafe(e.getToJson(), LabelAddress.class))
                .packageWeightKg(e.getPackageWeightKg())
                .dimsCm(e.getDimsCm())
                .cartonCount(e.getCartonTotal())
                .thermalMode(e.getThermalMode())
                .printBorder(e.getPrintBorder())
                .status(e.getStatus())
                .pdfSha256(e.getPdfSha256())
                .hasPdf(hasPdf)
                .generatedBy(e.getGeneratedBy())
                .createdBy(e.getCreatedBy())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .billingType(refs.getBillingType())
                .codAmount(refs.getCodAmount())
                .invoiceNo(refs.getInvoiceNo())
                .poNumber(refs.getPoNumber())
                .ref1(refs.getRef1())
                .ref2(refs.getRef2())
                .notes(refs.getNotes())
                .thisWayUp(refs.getThisWayUp())
                .fragile(refs.getFragile())
                .keepDry(refs.getKeepDry())
                .doNotStack(refs.getDoNotStack())
                .handleWithCare(refs.getHandleWithCare())
                .fba(readJsonSafe(e.getFbaJson(), ShippingLabelSpec.FbaFields.class))
                .gs1(readJsonSafe(e.getGs1Json(), ShippingLabelSpec.Gs1Fields.class))
                .fnskuCopies(e.getFnskuCopies())
                .fnskuItems(readJsonListSafe(e.getFnskuItemsJson(), ShippingLabelSpec.FnskuItem.class))
                .build();
    }

    private StoredShippingRefs refsFrom(CreateShippingLabelRequest req) {
        return StoredShippingRefs.builder()
                .billingType(req.getBillingType())
                .codAmount(req.getCodAmount())
                .invoiceNo(req.getInvoiceNo())
                .poNumber(req.getPoNumber())
                .ref1(req.getRef1())
                .ref2(req.getRef2())
                .notes(req.getNotes())
                .thisWayUp(req.getThisWayUp())
                .fragile(req.getFragile())
                .keepDry(req.getKeepDry())
                .doNotStack(req.getDoNotStack())
                .handleWithCare(req.getHandleWithCare())
                .build();
    }

    private StoredShippingRefs readRefs(ShippingLabel e) {
        StoredShippingRefs refs = readJsonSafe(e.getReferenceFields(), StoredShippingRefs.class);
        return refs != null ? refs : new StoredShippingRefs();
    }

    private void audit(Long businessId, Long labelId, LabelAuditAction action, Long userId, String ip) {
        labelAuditRepository.save(LabelAudit.builder()
                .businessId(businessId)
                .labelId(labelId)
                .labelType(LabelKind.SHIPPING)
                .action(action)
                .userId(userId)
                .ip(ip)
                .createdAt(OffsetDateTime.now())
                .build());
    }

    private String nextLabelNumber(Long businessId) {
        long count = shippingLabelRepository.countByBusinessIdAndDeletedAtIsNull(businessId);
        int year = java.time.Year.now().getValue();
        for (long i = 1; i <= 1000; i++) {
            String candidate = String.format("SL-%d-%04d", year, count + i);
            if (!shippingLabelRepository.existsByBusinessIdAndLabelNumber(businessId, candidate)) {
                return candidate;
            }
        }
        return "SL-" + businessId + "-" + System.currentTimeMillis();
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


    private static final Pattern WEIGHT = Pattern.compile("(\\d+(?:[.,]\\d+)?)");

    private BigDecimal parseKg(String weight) {
        if (weight == null || weight.isBlank()) {
            return null;
        }
        Matcher m = WEIGHT.matcher(weight);
        if (m.find()) {
            return new BigDecimal(m.group(1).replace(',', '.'));
        }
        return null;
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

    private <T> List<T> readJsonListSafe(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            return List.of();
        }
    }
}
