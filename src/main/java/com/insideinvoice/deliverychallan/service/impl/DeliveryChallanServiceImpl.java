package com.insideinvoice.deliverychallan.service.impl;

import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.deliverychallan.dto.request.CreateDeliveryChallanItemRequest;
import com.insideinvoice.deliverychallan.dto.request.CreateDeliveryChallanRequest;
import com.insideinvoice.deliverychallan.dto.response.DeliveryChallanItemResponse;
import com.insideinvoice.deliverychallan.dto.response.DeliveryChallanResponse;
import com.insideinvoice.deliverychallan.entity.DeliveryChallan;
import com.insideinvoice.deliverychallan.entity.DeliveryChallanItem;
import com.insideinvoice.deliverychallan.repository.DeliveryChallanRepository;
import com.insideinvoice.deliverychallan.service.DeliveryChallanService;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryChallanServiceImpl implements DeliveryChallanService {

    private final DeliveryChallanRepository deliveryChallanRepository;
    private final CustomerRepository customerRepository;
    private final BusinessRepository businessRepository;
    private static final DateTimeFormatter DC_NUMBER_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Override
    @Transactional
    public DeliveryChallanResponse createDeliveryChallan(CreateDeliveryChallanRequest request, Long businessId,
            Long userId) {
        Customer customer = customerRepository.findByIdAndBusinessId(request.getCustomerId(), businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()));

        // Serialize challan-number generation per business (same trick as
        // InvoiceNumberGenerator): the timestamp + existsBy loop raced on
        // uq_delivery_challans_number and turned concurrent creates into 500s.
        businessRepository.findByIdWithLock(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", "id", businessId));

        String challanNumber = request.getChallanNumber();
        if (challanNumber == null || challanNumber.isBlank()) {
            String base = "DC-" + LocalDateTime.now().format(DC_NUMBER_TS);
            challanNumber = base;
            int suffix = 1;
            while (deliveryChallanRepository.existsByBusinessIdAndChallanNumber(businessId, challanNumber)) {
                challanNumber = base + "-" + suffix++;
            }
        } else {
            challanNumber = challanNumber.trim();
            if (deliveryChallanRepository.existsByBusinessIdAndChallanNumber(businessId, challanNumber)) {
                throw new BadRequestException("Challan number '" + challanNumber + "' already exists");
            }
        }

        DeliveryChallan challan = DeliveryChallan.builder()
                .businessId(businessId)
                .challanNumber(challanNumber)
                .customerId(customer.getId())
                .challanDate(request.getChallanDate())
                .poNumber(trimToNull(request.getPoNumber()))
                .poDate(request.getPoDate())
                .createdBy(userId)
                .build();

        List<DeliveryChallanItem> items = new ArrayList<>();
        int sno = 1;
        for (CreateDeliveryChallanItemRequest itemRequest : request.getItems()) {
            DeliveryChallanItem item = DeliveryChallanItem.builder()
                    .challan(challan)
                    .sno(sno++)
                    .description(itemRequest.getDescription().trim())
                    .quantity(itemRequest.getQuantity())
                    .build();
            items.add(item);
        }
        challan.setItems(items);

        challan = deliveryChallanRepository.save(challan);
        return toResponse(challan, customer);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<DeliveryChallanResponse> getAllDeliveryChallans(Long businessId, int page, int size,
            String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = com.insideinvoice.common.PageParams.of(page, size, sort);
        Page<DeliveryChallan> challans = deliveryChallanRepository.findByBusinessId(businessId, pageable);

        return PagedResponse.<DeliveryChallanResponse>builder()
                .content(challans.getContent().stream()
                        .map(challan -> toResponse(challan, findCustomer(challan.getCustomerId(), businessId)))
                        .toList())
                .page(challans.getNumber())
                .size(challans.getSize())
                .totalElements(challans.getTotalElements())
                .totalPages(challans.getTotalPages())
                .last(challans.isLast())
                .first(challans.isFirst())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryChallanResponse getDeliveryChallan(Long id, Long businessId) {
        DeliveryChallan challan = deliveryChallanRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery challan", "id", id));
        return toResponse(challan, findCustomer(challan.getCustomerId(), businessId));
    }

    @Override
    @Transactional
    public void deleteDeliveryChallan(Long id, Long businessId) {
        DeliveryChallan challan = deliveryChallanRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery challan", "id", id));
        deliveryChallanRepository.delete(challan);
    }

    /**
     * Null-tolerant: V16 has no FK from delivery_challans to customers, so a deleted
     * customer must not 404 the entire list (orElseThrow here made GET /delivery-challans
     * return 404 after any customer deletion). toResponse renders null customer fields
     * as null — same tolerance InvoiceServiceImpl uses with .orElse("Unknown").
     */
    private Customer findCustomer(Long customerId, Long businessId) {
        return customerRepository.findByIdAndBusinessId(customerId, businessId).orElse(null);
    }

    private DeliveryChallanResponse toResponse(DeliveryChallan challan, Customer customer) {
        List<DeliveryChallanItemResponse> items = challan.getItems().stream()
                .map(item -> DeliveryChallanItemResponse.builder()
                        .id(item.getId())
                        .sno(item.getSno())
                        .description(item.getDescription())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        return DeliveryChallanResponse.builder()
                .id(challan.getId())
                .businessId(challan.getBusinessId())
                .challanNumber(challan.getChallanNumber())
                .customerId(challan.getCustomerId())
                .customerName(customer != null ? customer.getName() : null)
                .customerAddress(customer != null ? buildAddress(customer) : null)
                .customerPhone(customer != null ? customer.getPhone() : null)
                .customerGstIn(customer != null ? customer.getGstIn() : null)
                .challanDate(challan.getChallanDate())
                .poNumber(challan.getPoNumber())
                .poDate(challan.getPoDate())
                .createdAt(challan.getCreatedAt())
                .items(items)
                .build();
    }

    private String buildAddress(Customer customer) {
        String address = customer.getBillingAddress();
        String city = customer.getCity();
        if (isBlank(address)) {
            return isBlank(city) ? null : city;
        }
        if (isBlank(city)) {
            return address;
        }
        return address + ", " + city;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
