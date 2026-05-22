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

    @Override
    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request, Long businessId) {
        if (request.getEmail() != null && customerRepository.existsByEmailAndBusinessId(request.getEmail(), businessId)) {
            throw new DuplicateResourceException("Customer", "email", request.getEmail());
        }

        Customer customer = customerMapper.toEntity(request, businessId);
        customer = customerRepository.save(customer);

        log.info("Customer created: {} for businessId: {}", customer.getId(), businessId);
        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CustomerResponse> getAllCustomers(Long businessId, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
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
    @Transactional
    public void deleteCustomer(Long id, Long businessId) {
        Customer customer = customerRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        customerRepository.delete(customer);

        log.info("Customer deleted: {} for businessId: {}", id, businessId);
    }
}
