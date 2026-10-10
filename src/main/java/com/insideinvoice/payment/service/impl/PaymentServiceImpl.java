package com.insideinvoice.payment.service.impl;

import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceStatus;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import com.insideinvoice.payment.dto.request.CreatePaymentRequest;
import com.insideinvoice.payment.dto.response.PaymentResponse;
import com.insideinvoice.payment.entity.Payment;
import com.insideinvoice.payment.repository.PaymentRepository;
import com.insideinvoice.payment.service.PaymentService;
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
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, Long businessId) {
        // Pessimistic row lock: two concurrent payments against the same invoice used to
        // both read the same paid-total and both pass the remaining-balance check
        // (overpayment, last-writer-wins status). The lock serializes the read-compute-write.
        Invoice invoice = invoiceRepository.findByIdAndBusinessIdForUpdate(request.getInvoiceId(), businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", request.getInvoiceId()));

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment amount must be positive");
        }

        // A cancelled invoice is void: recording a payment against it silently injected
        // revenue into reports while the document itself was struck off.
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BadRequestException("Cannot record a payment against a cancelled invoice");
        }

        BigDecimal totalPaid = paymentRepository.findByInvoiceId(invoice.getId()).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = invoice.getGrandTotal().subtract(totalPaid);
        if (request.getAmount().compareTo(remaining) > 0) {
            throw new BadRequestException(
                    "Payment amount exceeds remaining balance. Remaining: " + remaining.toPlainString());
        }

        Payment payment = Payment.builder()
                .businessId(businessId)
                .invoiceId(invoice.getId())
                .amount(request.getAmount())
                .paymentMode(request.getPaymentMode())
                .referenceNo(request.getReferenceNo())
                .paymentDate(request.getPaymentDate())
                .notes(request.getNotes())
                .build();
        payment = paymentRepository.save(payment);

        BigDecimal newTotalPaid = totalPaid.add(request.getAmount());
        if (newTotalPaid.compareTo(invoice.getGrandTotal()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoiceRepository.save(invoice);
            log.info("Invoice {} fully paid, status set to PAID", invoice.getInvoiceNumber());
        } else {
            invoice.setStatus(InvoiceStatus.PENDING);
            invoiceRepository.save(invoice);
        }

        log.info("Payment of {} recorded for invoice {} (businessId: {})",
                request.getAmount(), invoice.getInvoiceNumber(), businessId);

        return toResponse(payment, invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long id, Long businessId) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", id));
        if (!payment.getBusinessId().equals(businessId)) {
            throw new ResourceNotFoundException("Payment", "id", id);
        }
        Invoice invoice = invoiceRepository.findById(payment.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", payment.getInvoiceId()));
        return toResponse(payment, invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> getAllPayments(Long businessId, int page, int size, String sortBy, String sortDir) {
        Sort sort = com.insideinvoice.common.PageParams.safeSort(sortBy, sortDir,
                java.util.Set.of("createdAt", "amount", "paymentDate", "paymentMode"), "createdAt");
        Pageable pageable = com.insideinvoice.common.PageParams.of(page, size, sort);
        Page<Payment> payments = paymentRepository.findByBusinessId(businessId, pageable);

        // Batch-load the invoices for the whole page in ONE query (only the invoice
        // number is needed) instead of one findById per payment (N+1).
        java.util.List<Long> invoiceIds = payments.getContent().stream()
                .map(Payment::getInvoiceId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        java.util.Map<Long, Invoice> invoicesById = invoiceIds.isEmpty()
                ? java.util.Map.of()
                : invoiceRepository.findAllById(invoiceIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Invoice::getId, java.util.function.Function.identity(), (a, b) -> a));

        // Batch-load customer names for the page's invoices in ONE query (avoids a
        // customer findById per payment inside toResponse).
        java.util.Set<Long> customerIds = invoicesById.values().stream()
                .map(Invoice::getCustomerId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, String> customerNames = customerIds.isEmpty()
                ? java.util.Map.of()
                : customerRepository.findByIdInAndBusinessId(customerIds, businessId).stream()
                        .collect(java.util.stream.Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));

        return PagedResponse.<PaymentResponse>builder()
                .content(payments.getContent().stream()
                        .map(payment -> {
                            Invoice invoice = invoicesById.get(payment.getInvoiceId());
                            String name = invoice == null
                                    ? "Unknown"
                                    : customerNames.getOrDefault(invoice.getCustomerId(), "Unknown");
                            return toResponse(payment, invoice, name);
                        })
                        .toList())
                .page(payments.getNumber())
                .size(payments.getSize())
                .totalElements(payments.getTotalElements())
                .totalPages(payments.getTotalPages())
                .last(payments.isLast())
                .first(payments.isFirst())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByInvoice(Long invoiceId, Long businessId) {
        // Ownership check: without it, any authenticated user could read another
        // tenant's payment history by guessing invoice IDs (IDOR/BOLA). Uniform 404 so
        // ID existence is never confirmed across tenants.
        invoiceRepository.findByIdAndBusinessId(invoiceId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        List<Payment> payments = paymentRepository.findByInvoiceId(invoiceId).stream()
                .filter(p -> p.getBusinessId().equals(businessId))
                .toList();
        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        return payments.stream()
                .map(p -> toResponse(p, invoice))
                .toList();
    }

    @Override
    @Transactional
    public void deletePayment(Long id, Long businessId) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", id));
        if (!payment.getBusinessId().equals(businessId)) {
            throw new ResourceNotFoundException("Payment", "id", id);
        }
        paymentRepository.delete(payment);

        // Keep invoice status truthful: a PAID invoice whose only payment was deleted
        // used to stay PAID forever, drifting balance/reporting. FOR UPDATE serializes
        // this recompute with concurrent createPayment/updateInvoice (same row lock).
        invoiceRepository.findByIdAndBusinessIdForUpdate(payment.getInvoiceId(), businessId)
                .ifPresent(invoice -> {
            if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.PENDING) {
                BigDecimal totalPaid = paymentRepository.findByInvoiceId(invoice.getId()).stream()
                        .map(Payment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                InvoiceStatus recomputed = totalPaid.compareTo(invoice.getGrandTotal()) >= 0
                        ? InvoiceStatus.PAID
                        : InvoiceStatus.PENDING;
                if (invoice.getStatus() != recomputed) {
                    invoice.setStatus(recomputed);
                    invoiceRepository.save(invoice);
                    log.info("Invoice {} status recomputed to {} after payment delete",
                            invoice.getInvoiceNumber(), recomputed);
                }
            }
        });

        log.info("Payment {} deleted for businessId: {}", id, businessId);
    }

    private PaymentResponse toResponse(Payment payment, Invoice invoice) {
        String customerName = "Unknown";
        if (invoice != null) {
            customerName = customerRepository.findById(invoice.getCustomerId())
                    .map(Customer::getName)
                    .orElse("Unknown");
        }
        return toResponse(payment, invoice, customerName);
    }

    private PaymentResponse toResponse(Payment payment, Invoice invoice, String customerName) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .businessId(payment.getBusinessId())
                .invoiceId(payment.getInvoiceId())
                .invoiceNumber(invoice != null ? invoice.getInvoiceNumber() : null)
                .customerName(customerName != null ? customerName : "Unknown")
                .amount(payment.getAmount())
                .paymentMode(payment.getPaymentMode())
                .referenceNo(payment.getReferenceNo())
                .paymentDate(payment.getPaymentDate())
                .notes(payment.getNotes())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
