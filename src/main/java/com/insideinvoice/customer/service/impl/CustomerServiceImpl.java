package com.insideinvoice.customer.service.impl;

import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.customer.dto.request.CreateCustomerRequest;
import com.insideinvoice.customer.dto.request.UpdateCustomerRequest;
import com.insideinvoice.customer.dto.response.CustomerResponse;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.customer.mapper.CustomerMapper;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.customer.service.CustomerService;
import com.insideinvoice.exception.DuplicateResourceException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import com.insideinvoice.labels.entity.HazmatLabel;
import com.insideinvoice.labels.entity.ShippingLabel;
import com.insideinvoice.labels.repository.HazmatLabelRepository;
import com.insideinvoice.labels.repository.LabelFileRepository;
import com.insideinvoice.labels.repository.ShippingLabelRepository;
import com.insideinvoice.payment.repository.PaymentRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
public class CustomerServiceImpl implements CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceImpl.class);

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ShippingLabelRepository shippingLabelRepository;
    private final HazmatLabelRepository hazmatLabelRepository;
    private final LabelFileRepository labelFileRepository;

    @Override
    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request, Long businessId) {
        assertIdentityUnique(request.getPhone(), request.getEmail(), businessId, null);

        Customer customer = customerMapper.toEntity(request, businessId);
        customer = customerRepository.save(customer);
        log.info("Customer created: {} for businessId: {}", customer.getId(), businessId);
        return customerMapper.toResponse(customer);
    }

    /**
     * Phone + email act as the customer's natural key, but ONLY inside a single
     * business. Every lookup is scoped by businessId, so two businesses (tenants)
     * may hold the same phone/email — the same person can be a customer of both.
     * A repeat inside ONE business is rejected with 409 instead of being merged
     * into the existing row.
     *
     * @param excludeCustomerId the customer being updated (null on create)
     */
    private void assertIdentityUnique(String phone, String email, Long businessId, Long excludeCustomerId) {
        if (phone != null && !phone.isBlank()) {
            customerRepository.findByPhoneAndBusinessId(phone, businessId)
                    .filter(existing -> excludeCustomerId == null || !excludeCustomerId.equals(existing.getId()))
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException(String.format(
                                "Phone '%s' is already used by customer '%s' in your business",
                                phone, existing.getName()));
                    });
        }
        if (email != null && !email.isBlank()) {
            customerRepository.findByEmailIgnoreCaseAndBusinessId(email, businessId)
                    .filter(existing -> excludeCustomerId == null || !excludeCustomerId.equals(existing.getId()))
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException(String.format(
                                "Email '%s' is already used by customer '%s' in your business",
                                email, existing.getName()));
                    });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CustomerResponse> getAllCustomers(Long businessId, int page, int size, String sortBy, String sortDir) {
        Sort sort = com.insideinvoice.common.PageParams.safeSort(sortBy, sortDir,
                java.util.Set.of("createdAt", "name", "email", "phone", "city", "state"), "createdAt");
        Pageable pageable = com.insideinvoice.common.PageParams.of(page, size, sort);
        Page<Customer> customers = customerRepository.findByBusinessId(businessId, pageable);

        return PagedResponse.<CustomerResponse>builder()
                .content(customers.getContent().stream().map(customerMapper::toResponse).toList())
                .page(customers.getNumber())
                .size(customers.getSize())
                .totalElements(customers.getTotalElements())
                .totalPages(customers.getTotalPages())
                .last(customers.isLast())
                .first(customers.isFirst())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Long id, Long businessId) {
        Customer customer = customerRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(Long id, UpdateCustomerRequest request, Long businessId) {
        Customer customer = customerRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));

        // Effective values after the partial update (null/blank = keep, or clear to "")
        // must not collide with ANOTHER customer of the same business.
        String phone = request.getPhone() != null ? request.getPhone() : customer.getPhone();
        String email = request.getEmail() != null ? request.getEmail() : customer.getEmail();
        assertIdentityUnique(phone, email, businessId, id);

        customerMapper.updateEntity(customer, request);
        customer = customerRepository.save(customer);

        log.info("Customer updated: {} for businessId: {}", id, businessId);
        return customerMapper.toResponse(customer);
    }

    @Override
    public CustomerResponse findCustomerByEmailOrPhone(String email, String phone, Long businessId) {
        if (phone != null && !phone.isBlank()) {
            Optional<Customer> byPhone = customerRepository.findByPhoneAndBusinessId(phone, businessId);
            if (byPhone.isPresent()) {
                log.info("Customer found by phone: {} for businessId: {}", phone, businessId);
                return customerMapper.toResponse(byPhone.get());
            }
        }
        if (email != null && !email.isBlank()) {
            Optional<Customer> byEmail = customerRepository.findByEmailIgnoreCaseAndBusinessId(email, businessId);
            if (byEmail.isPresent()) {
                log.info("Customer found by email: {} for businessId: {}", email, businessId);
                return customerMapper.toResponse(byEmail.get());
            }
        }
        throw new ResourceNotFoundException("Customer", "phone", phone != null ? phone : email);
    }

    @Override
    @Transactional
    public void deleteCustomer(Long id, Long businessId) {
        Customer customer = customerRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));

        // Deleting a customer takes their invoices with them. The schema blocks a
        // naked customer delete (fk_invoices_customer V1:105, NO ACTION → raw 500),
        // and invoice rows are themselves referenced by payments (fk_payments_invoice
        // V8:13) and shipping/hazmat labels (V14:37,87), so the cascade clears
        // dependents in FK order: payments → label PDF files → label rows → invoices
        // (items follow the parent via CascadeType.ALL) → the customer. label_files
        // has no FK (V14:94) but is purged too so no orphaned PDFs are left behind —
        // same order the admin label purge uses.
        List<Invoice> invoices = invoiceRepository.findByCustomerId(id);
        if (!invoices.isEmpty()) {
            List<Long> invoiceIds = invoices.stream().map(Invoice::getId).toList();

            for (Long invoiceId : invoiceIds) {
                paymentRepository.deleteByInvoiceId(invoiceId);
            }

            List<ShippingLabel> shippingLabels = new ArrayList<>();
            List<HazmatLabel> hazmatLabels = new ArrayList<>();
            Set<Long> labelIds = new LinkedHashSet<>();
            for (Long invoiceId : invoiceIds) {
                for (ShippingLabel label : shippingLabelRepository.findByInvoiceId(invoiceId)) {
                    shippingLabels.add(label);
                    labelIds.add(label.getId());
                }
                for (HazmatLabel label : hazmatLabelRepository.findByInvoiceId(invoiceId)) {
                    hazmatLabels.add(label);
                    labelIds.add(label.getId());
                }
            }
            if (!labelIds.isEmpty()) {
                labelFileRepository.deleteByLabelIdIn(labelIds);
                shippingLabelRepository.deleteAll(shippingLabels);
                hazmatLabelRepository.deleteAll(hazmatLabels);
            }

            invoiceRepository.deleteAll(invoices);
        }

        customerRepository.delete(customer);

        log.info("Customer deleted: {} for businessId: {} ({} invoice(s) cascade-deleted)",
                id, businessId, invoices.size());
    }
}
