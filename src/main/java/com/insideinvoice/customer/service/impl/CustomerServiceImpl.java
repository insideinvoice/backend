package com.insideinvoice.customer.service.impl;

import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.customer.dto.request.CreateCustomerRequest;
import com.insideinvoice.customer.dto.request.UpdateCustomerRequest;
import com.insideinvoice.customer.dto.response.CustomerResponse;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.customer.mapper.CustomerMapper;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.customer.service.CustomerService;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import java.util.Optional;
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

    @Override
    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request, Long businessId) {
        if (request.getPhone() != null) {
            Optional<Customer> existingByPhone = customerRepository.findByPhoneAndBusinessId(request.getPhone(), businessId);
            if (existingByPhone.isPresent()) {
                Customer existing = updateExistingCustomer(existingByPhone.get(), request);
                existing = customerRepository.save(existing);
                log.info("Customer updated (duplicate phone): {} for businessId: {}", existing.getId(), businessId);
                return customerMapper.toResponse(existing);
            }
        }
        if (request.getEmail() != null) {
            Optional<Customer> existingByEmail = customerRepository.findByEmailAndBusinessId(request.getEmail(), businessId);
            if (existingByEmail.isPresent()) {
                Customer existing = updateExistingCustomer(existingByEmail.get(), request);
                existing = customerRepository.save(existing);
                log.info("Customer updated (duplicate email): {} for businessId: {}", existing.getId(), businessId);
                return customerMapper.toResponse(existing);
            }
        }

        Customer customer = customerMapper.toEntity(request, businessId);
        customer = customerRepository.save(customer);
        log.info("Customer created: {} for businessId: {}", customer.getId(), businessId);
        return customerMapper.toResponse(customer);
    }

    private Customer updateExistingCustomer(Customer existing, CreateCustomerRequest request) {
        existing.setName(request.getName());
        if (request.getEmail() != null) existing.setEmail(request.getEmail());
        if (request.getPhone() != null) existing.setPhone(request.getPhone());
        existing.setBillingAddress(request.getBillingAddress());
        existing.setShippingAddress(request.getShippingAddress());
        existing.setGstIn(request.getGstIn());
        existing.setCity(request.getCity());
        existing.setState(request.getState());
        existing.setCountry(request.getCountry());
        existing.setPincode(request.getPincode());
        return existing;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CustomerResponse> getAllCustomers(Long businessId, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
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
            Optional<Customer> byEmail = customerRepository.findByEmailAndBusinessId(email, businessId);
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

        // fk_invoices_customer (V1:105, NO ACTION) turns this into a raw
        // DataIntegrityViolationException → 500 with the row still present; pre-check
        // so the client gets an actionable 400.
        if (invoiceRepository.existsByCustomerId(id)) {
            throw new BadRequestException("Cannot delete customer: invoices exist for this customer");
        }

        customerRepository.delete(customer);

        log.info("Customer deleted: {} for businessId: {}", id, businessId);
    }
}
