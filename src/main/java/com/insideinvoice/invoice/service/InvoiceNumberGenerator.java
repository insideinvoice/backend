package com.insideinvoice.invoice.service;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private static final Logger log = LoggerFactory.getLogger(InvoiceNumberGenerator.class);

    private final BusinessRepository businessRepository;

    @Transactional
    public String generateNextInvoiceNumber(Long businessId) {
        Business business = businessRepository.findByIdWithLock(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", "id", businessId));

        if (business.getInvoicePrefix() == null || business.getInvoicePrefix().isBlank()) {
            throw new BadRequestException("Invoice prefix not configured for this business");
        }

        String prefix = business.getInvoicePrefix();
        Long sequence = business.getNextInvoiceSequence();

        String invoiceNumber = String.format("%s-%03d", prefix.toUpperCase(), sequence);

        business.setNextInvoiceSequence(sequence + 1);
        businessRepository.save(business);

        log.debug("Generated invoice number: {} for businessId: {}", invoiceNumber, businessId);
        return invoiceNumber;
    }
}
