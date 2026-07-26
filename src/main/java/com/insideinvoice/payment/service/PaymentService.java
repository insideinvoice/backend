package com.insideinvoice.payment.service;

import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.payment.dto.request.CreatePaymentRequest;
import com.insideinvoice.payment.dto.response.PaymentResponse;

import java.util.List;

public interface PaymentService {

    PaymentResponse createPayment(CreatePaymentRequest request, Long businessId);

    PaymentResponse getPayment(Long id, Long businessId);

    PagedResponse<PaymentResponse> getAllPayments(Long businessId, int page, int size, String sortBy, String sortDir);

    List<PaymentResponse> getPaymentsByInvoice(Long invoiceId, Long businessId);

    void deletePayment(Long id, Long businessId);
}
