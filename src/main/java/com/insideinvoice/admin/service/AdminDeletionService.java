package com.insideinvoice.admin.service;

import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.deliverychallan.repository.DeliveryChallanRepository;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import com.insideinvoice.labels.repository.HazmatLabelRepository;
import com.insideinvoice.labels.repository.LabelAuditRepository;
import com.insideinvoice.labels.repository.LabelFileRepository;
import com.insideinvoice.labels.repository.ShippingLabelRepository;
import com.insideinvoice.payment.repository.PaymentRepository;
import com.insideinvoice.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Atomic admin purge of a user's tenant data. Replaces the old controller-side sequence
 * of five independent repository calls (no transaction): that code deleted products,
 * then failed on {@code fk_invoices_customer} (customers deleted before invoices) — or
 * on {@code fk_payments_invoice} / {@code fk_shipping_labels_invoice} — after some
 * statements had already committed, leaving partial data loss plus a 500.
 *
 * <p>Delete order respects every FK in the schema:</p>
 * <ol>
 *   <li>label_files / label_audit (app-level children of labels)</li>
 *   <li>shipping_labels, hazmat_labels → invoices, businesses</li>
 *   <li>payments → invoices, businesses</li>
 *   <li>invoices → customers, businesses, users(created_by); items cascade</li>
 *   <li>delivery_challans → businesses; items cascade</li>
 *   <li>customers, products → businesses</li>
 *   <li>the user → businesses</li>
 *   <li>businesses last, only when no users remain</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class AdminDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AdminDeletionService.class);

    private final ShippingLabelRepository shippingLabelRepository;
    private final HazmatLabelRepository hazmatLabelRepository;
    private final LabelFileRepository labelFileRepository;
    private final LabelAuditRepository labelAuditRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final DeliveryChallanRepository deliveryChallanRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;

    @Transactional
    public void deleteUserWithBusinessData(User user) {
        Long businessId = user.getBusinessId();

        if (businessId != null) {
            List<Long> labelIds = new ArrayList<>(shippingLabelRepository.findIdByBusinessId(businessId));
            labelIds.addAll(hazmatLabelRepository.findIdByBusinessId(businessId));
            if (!labelIds.isEmpty()) {
                labelFileRepository.deleteByLabelIdIn(labelIds);
            }
            labelAuditRepository.deleteByBusinessId(businessId);

            shippingLabelRepository.deleteByBusinessId(businessId);
            hazmatLabelRepository.deleteByBusinessId(businessId);

            paymentRepository.deleteByBusinessId(businessId);

            invoiceRepository.deleteByBusinessId(businessId);

            deliveryChallanRepository.deleteByBusinessId(businessId);

            customerRepository.deleteByBusinessId(businessId);
            productRepository.deleteByBusinessId(businessId);
        }

        userRepository.delete(user);

        if (businessId != null && userRepository.countByBusinessId(businessId) == 0) {
            businessRepository.deleteById(businessId);
            log.info("Deleted business {} as well (no remaining users)", businessId);
        }
    }
}
