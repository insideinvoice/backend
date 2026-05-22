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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceNumberGenerator invoiceNumberGenerator;

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

        String invoiceNumber = invoiceNumberGenerator.generateNextInvoiceNumber(businessId);

        Invoice invoice = invoiceMapper.toEntity(request, invoiceNumber, businessId, userId);
        invoice = invoiceRepository.save(invoice);

        log.info("Invoice created: {} for businessId: {}", invoice.getInvoiceNumber(), businessId);
        return invoiceMapper.toResponse(invoice, customer.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<InvoiceResponse> getAllInvoices(Long businessId, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Invoice> invoices = invoiceRepository.findByBusinessId(businessId, pageable);

        return PagedResponse.<InvoiceResponse>builder()
                .content(invoices.getContent().stream()
                        .map(invoice -> {
                            String customerName = getCustomerName(invoice.getCustomerId(), businessId);
                            return invoiceMapper.toResponse(invoice, customerName);
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
    @Transactional
    public InvoiceResponse updateInvoice(Long id, UpdateInvoiceRequest request, Long businessId) {
        Invoice invoice = invoiceRepository.findByIdAndBusinessId(id, businessId)
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

        invoice.setCustomerId(request.getCustomerId());
        invoice.setInvoiceType(invoiceType);
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setDueDate(request.getDueDate());
        invoice.setPaymentTerms(request.getPaymentTerms());
        invoice.setNotes(request.getNotes());
        invoice.setStatus(status);
        invoice.setPlaceOfSupply(request.getPlaceOfSupply());
        invoice.setDeliveryNote(request.getDeliveryNote());
        invoice.setReferenceNumber(request.getReferenceNumber());
        invoice.setBuyerOrderNumber(request.getBuyerOrderNumber());
        invoice.setDispatchDocNumber(request.getDispatchDocNumber());
        invoice.setDispatchedThrough(request.getDispatchedThrough());
        invoice.setTermsOfDelivery(request.getTermsOfDelivery());
        invoice.setOtherReferences(request.getOtherReferences());
        invoice.setDestination(request.getDestination());

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
        invoiceRepository.delete(invoice);

        log.info("Invoice deleted: {} for businessId: {}", id, businessId);
    }

    private String getCustomerName(Long customerId, Long businessId) {
        return customerRepository.findByIdAndBusinessId(customerId, businessId)
                .map(Customer::getName)
                .orElse("Unknown");
    }
}
