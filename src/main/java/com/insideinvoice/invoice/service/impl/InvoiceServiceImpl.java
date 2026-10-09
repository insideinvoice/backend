package com.insideinvoice.invoice.service.impl;

import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.invoice.dto.request.CreateInvoiceRequest;
import com.insideinvoice.invoice.dto.request.InvoiceItemRequest;
import com.insideinvoice.invoice.dto.request.UpdateInvoiceRequest;
import com.insideinvoice.invoice.dto.response.InvoiceResponse;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceStatus;
import com.insideinvoice.invoice.entity.InvoiceType;
import com.insideinvoice.invoice.mapper.InvoiceMapper;
import com.insideinvoice.invoice.repository.InvoiceItemRepository;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import com.insideinvoice.invoice.service.InvoiceNumberGenerator;
import com.insideinvoice.invoice.service.InvoiceService;
import com.insideinvoice.labels.repository.HazmatLabelRepository;
import com.insideinvoice.labels.repository.ShippingLabelRepository;
import com.insideinvoice.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final PaymentRepository paymentRepository;
    private final ShippingLabelRepository shippingLabelRepository;
    private final HazmatLabelRepository hazmatLabelRepository;

    @Override
    @Transactional
    public InvoiceResponse createInvoice(CreateInvoiceRequest request, Long businessId, Long userId) {
        Customer customer = customerRepository.findByIdAndBusinessId(request.getCustomerId(), businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()));

        InvoiceType invoiceType;
        try {
            invoiceType = InvoiceType.valueOf(request.getInvoiceType());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid invoice type: " + request.getInvoiceType());
        }

        for (InvoiceItemRequest item : request.getItems()) {
            if (item.getQty().compareTo(item.getQty().abs()) != 0) {
                throw new BadRequestException("Quantity must be positive");
            }
            if (item.getRate().compareTo(item.getRate().abs()) != 0) {
                throw new BadRequestException("Rate must be positive");
            }
        }

        String invoiceNumber;
        if (request.getInvoiceNumber() != null && !request.getInvoiceNumber().isBlank()) {
            if (invoiceRepository.existsByInvoiceNumberAndBusinessId(request.getInvoiceNumber(), businessId)) {
                throw new BadRequestException("Invoice number " + request.getInvoiceNumber() + " already exists");
            }
            invoiceNumber = request.getInvoiceNumber();
            invoiceNumberGenerator.reserveNextSequence(businessId);
        } else {
            invoiceNumber = invoiceNumberGenerator.generateNextInvoiceNumber(businessId);
        }

        Invoice invoice = invoiceMapper.toEntity(request, invoiceNumber, businessId, userId);
        invoice = invoiceRepository.save(invoice);

        log.info("Invoice created: {} for businessId: {}", invoice.getInvoiceNumber(), businessId);
        return invoiceMapper.toResponse(invoice, customer.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<InvoiceResponse> getAllInvoices(Long businessId, int page, int size, String sortBy, String sortDir) {
        Sort sort = com.insideinvoice.common.PageParams.safeSort(sortBy, sortDir,
                java.util.Set.of("createdAt", "invoiceDate", "dueDate", "grandTotal", "invoiceNumber", "status"),
                "createdAt");
        Pageable pageable = com.insideinvoice.common.PageParams.of(page, size, sort);
        Page<Invoice> invoices = invoiceRepository.findByBusinessId(businessId, pageable);
        List<Invoice> content = invoices.getContent();

        java.util.Set<Long> customerIds = content.stream()
                .map(Invoice::getCustomerId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<Long, String> customerNames = customerIds.isEmpty()
                ? java.util.Map.of()
                : customerRepository.findByIdInAndBusinessId(customerIds, businessId).stream()
                        .collect(java.util.stream.Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));

        // Batch-load items for the whole page in ONE query instead of letting the
        // mapper lazily fetch them per invoice (N+1).
        java.util.List<Long> invoiceIds = content.stream().map(Invoice::getId).toList();
        java.util.Map<Long, java.util.List<com.insideinvoice.invoice.entity.InvoiceItem>> itemsByInvoice =
                invoiceIds.isEmpty()
                        ? java.util.Map.of()
                        : invoiceItemRepository.findByInvoiceIdIn(invoiceIds).stream()
                                .collect(java.util.stream.Collectors.groupingBy(
                                        it -> it.getInvoice().getId()));

        return PagedResponse.<InvoiceResponse>builder()
                .content(content.stream()
                        .map(invoice -> {
                            String customerName = customerNames.getOrDefault(invoice.getCustomerId(), "Unknown");
                            java.util.List<com.insideinvoice.invoice.entity.InvoiceItem> items =
                                    itemsByInvoice.getOrDefault(invoice.getId(), java.util.List.of());
                            return invoiceMapper.toResponse(invoice, customerName, items);
                        })
                        .toList())
                .page(invoices.getNumber())
                .size(invoices.getSize())
                .totalElements(invoices.getTotalElements())
                .totalPages(invoices.getTotalPages())
                .last(invoices.isLast())
                .first(invoices.isFirst())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(Long id, Long businessId) {
        Invoice invoice = invoiceRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));

        String customerName = getCustomerName(invoice.getCustomerId(), businessId);
        return invoiceMapper.toResponse(invoice, customerName);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));

        String customerName = customerRepository.findById(invoice.getCustomerId())
                .map(Customer::getName)
                .orElse("Unknown");
        return invoiceMapper.toResponse(invoice, customerName);
    }

    @Override
    @Transactional
    public InvoiceResponse updateInvoiceById(Long id, UpdateInvoiceRequest request) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()));

        InvoiceType invoiceType;
        try {
            invoiceType = InvoiceType.valueOf(request.getInvoiceType());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid invoice type: " + request.getInvoiceType());
        }

        InvoiceStatus status;
        try {
            status = InvoiceStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid invoice status: " + request.getStatus());
        }

        applyInvoiceNumber(invoice, request.getInvoiceNumber(), invoice.getBusinessId());
        invoice.setCustomerId(request.getCustomerId());
        invoice.setInvoiceType(invoiceType);
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setDueDate(request.getDueDate());
        invoice.setPaymentTerms(request.getPaymentTerms());
        invoice.setNotes(request.getNotes());
        invoice.setStatus(status);
        invoice.setPlaceOfSupply(request.getPlaceOfSupply());
        invoice.setDeliveryNote(request.getDeliveryNote());
        invoice.setDeliveryNoteDate(request.getDeliveryNoteDate());
        invoice.setReferenceNumber(request.getReferenceNumber());
        invoice.setBuyerOrderNumber(request.getBuyerOrderNumber());
        invoice.setDispatchDocNumber(request.getDispatchDocNumber());
        invoice.setDispatchedThrough(request.getDispatchedThrough());
        invoice.setTermsOfDelivery(request.getTermsOfDelivery());
        invoice.setOtherReferences(request.getOtherReferences());
        invoice.setDestination(request.getDestination());
        invoice.setPaymentMode(request.getPaymentMode());
        invoice.setDiscountPercent(request.getDiscountPercent() != null ? request.getDiscountPercent() : BigDecimal.ZERO);

        invoice.getItems().clear();
        for (InvoiceItemRequest itemRequest : request.getItems()) {
            invoice.getItems().add(invoiceMapper.toInvoiceItem(itemRequest, invoice));
        }

        invoiceMapper.calculateInvoiceTotals(invoice);
        invoice = invoiceRepository.save(invoice);

        log.info("Invoice updated by admin: {}", invoice.getInvoiceNumber());
        return invoiceMapper.toResponse(invoice, customer.getName());
    }

    @Override
    @Transactional
    public InvoiceResponse updateInvoice(Long id, UpdateInvoiceRequest request, Long businessId) {
        // FOR UPDATE: serializes with createPayment (which locks the same row), so a
        // concurrent payment can't have its status/totals overwritten by a stale edit.
        Invoice invoice = invoiceRepository.findByIdAndBusinessIdForUpdate(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));

        Customer customer = customerRepository.findByIdAndBusinessId(request.getCustomerId(), businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()));

        InvoiceType invoiceType;
        try {
            invoiceType = InvoiceType.valueOf(request.getInvoiceType());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid invoice type: " + request.getInvoiceType());
        }

        InvoiceStatus status;
        try {
            status = InvoiceStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid invoice status: " + request.getStatus());
        }

        applyInvoiceNumber(invoice, request.getInvoiceNumber(), businessId);
        invoice.setCustomerId(request.getCustomerId());
        invoice.setInvoiceType(invoiceType);
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setDueDate(request.getDueDate());
        invoice.setPaymentTerms(request.getPaymentTerms());
        invoice.setNotes(request.getNotes());
        invoice.setStatus(status);
        invoice.setPlaceOfSupply(request.getPlaceOfSupply());
        invoice.setDeliveryNote(request.getDeliveryNote());
        invoice.setDeliveryNoteDate(request.getDeliveryNoteDate());
        invoice.setReferenceNumber(request.getReferenceNumber());
        invoice.setBuyerOrderNumber(request.getBuyerOrderNumber());
        invoice.setDispatchDocNumber(request.getDispatchDocNumber());
        invoice.setDispatchedThrough(request.getDispatchedThrough());
        invoice.setTermsOfDelivery(request.getTermsOfDelivery());
        invoice.setOtherReferences(request.getOtherReferences());
        invoice.setDestination(request.getDestination());
        invoice.setPaymentMode(request.getPaymentMode());
        invoice.setDiscountPercent(request.getDiscountPercent() != null ? request.getDiscountPercent() : BigDecimal.ZERO);

        invoice.getItems().clear();
        for (InvoiceItemRequest itemRequest : request.getItems()) {
            invoice.getItems().add(invoiceMapper.toInvoiceItem(itemRequest, invoice));
        }

        invoiceMapper.calculateInvoiceTotals(invoice);
        invoice = invoiceRepository.save(invoice);

        log.info("Invoice updated: {} for businessId: {}", invoice.getInvoiceNumber(), businessId);
        return invoiceMapper.toResponse(invoice, customer.getName());
    }

    @Override
    @Transactional
    public void deleteInvoice(Long id, Long businessId) {
        Invoice invoice = invoiceRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));

        // FK order in the schema blocks the delete (fk_payments_invoice V8:13,
        // fk_shipping/hazmat_labels_invoice V14:37,87) — surface it as a 400 with an
        // actionable message instead of a raw DataIntegrityViolationException → 500.
        if (paymentRepository.existsByInvoiceId(id)) {
            throw new BadRequestException("Cannot delete invoice: payments are recorded against it");
        }
        if (shippingLabelRepository.existsByInvoiceId(id) || hazmatLabelRepository.existsByInvoiceId(id)) {
            throw new BadRequestException("Cannot delete invoice: shipping/hazmat labels reference it");
        }

        invoiceRepository.delete(invoice);

        log.info("Invoice deleted: {} for businessId: {}", id, businessId);
    }

    /**
     * Update paths accept an omitted invoiceNumber: both frontend save flows
     * (InvoiceForm buildPayload and InvoiceView handleSave) only send the field in
     * ghost mode, so "absent" means "keep the stored number" — overwriting it with
     * null used to hit the NOT NULL column (500 before this release, 400 after the
     * audit guard). When a number IS provided it is validated for length and
     * uniqueness; races still surface as 409 via DataIntegrityViolationException.
     */
    private void applyInvoiceNumber(Invoice invoice, String requested, Long businessId) {
        if (requested == null || requested.isBlank()) {
            if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
                throw new BadRequestException("Invoice number is required");
            }
            return;
        }
        String number = requested.trim();
        if (number.length() > 50) {
            throw new BadRequestException("Invoice number must not exceed 50 characters");
        }
        if (!number.equals(invoice.getInvoiceNumber())
                && invoiceRepository.existsByInvoiceNumberAndBusinessId(number, businessId)) {
            throw new BadRequestException("Invoice number " + number + " already exists");
        }
        invoice.setInvoiceNumber(number);
    }

    private String getCustomerName(Long customerId, Long businessId) {
        return customerRepository.findByIdAndBusinessId(customerId, businessId)
                .map(Customer::getName)
                .orElse("Unknown");
    }
}
