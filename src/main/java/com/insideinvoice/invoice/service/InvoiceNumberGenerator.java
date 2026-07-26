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

import java.time.Year;

@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private static final Logger log = LoggerFactory.getLogger(InvoiceNumberGenerator.class);

    private final BusinessRepository businessRepository;

    @Transactional
    public String generateNextInvoiceNumber(Long businessId) {
        Business business = businessRepository.findByIdWithLock(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", "id", businessId));

        Long sequence = business.getNextInvoiceSequence();
        int year = Year.now().getValue();

        String invoiceNumber = String.format("INV-%d-%03d", year, sequence);

        business.setNextInvoiceSequence(sequence + 1);
        businessRepository.save(business);

        log.debug("Generated invoice number: {} for businessId: {}", invoiceNumber, businessId);
        return invoiceNumber;
    }

    @Transactional
    public void reserveNextSequence(Long businessId) {
        Business business = businessRepository.findByIdWithLock(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", "id", businessId));

        business.setNextInvoiceSequence(business.getNextInvoiceSequence() + 1);
        businessRepository.save(business);

        log.debug("Reserved next invoice sequence for businessId: {}", businessId);
    }
}
