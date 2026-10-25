package com.insideinvoice.einvoice.dto;

import com.insideinvoice.einvoice.entity.EInvoiceRegistration;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * View-page projection: a derived lifecycle status plus, when a submission
 * exists, the persisted registration (IRN, acknowledgement, signed QR flag).
 */
@Data
@Builder
public class EInvoiceStatusResponse {

    private Long invoiceId;
    /** DISABLED | NOT_APPLICABLE | READY_TO_REGISTER | REGISTERED | REGISTRATION_FAILED | ... */
    private String derivedStatus;
    private RegistrationView registration;

    @Data
    @Builder
    public static class RegistrationView {
        private Long id;
        private String status;
        private String irn;
        private String ackNumber;
        private LocalDateTime ackDate;
        private boolean hasSignedQr;
        private String provider;
        private String environment;
        private boolean mock;
        private LocalDateTime registeredAt;
        private String errorCode;
        private String errorMessage;

        public static RegistrationView from(EInvoiceRegistration r) {
            if (r == null) return null;
            return RegistrationView.builder()
                    .id(r.getId())
                    .status(r.getStatus() == null ? null : r.getStatus().name())
                    .irn(r.getIrn())
                    .ackNumber(r.getAckNumber())
                    .ackDate(r.getAckDate())
                    .hasSignedQr(r.getSignedQrData() != null && !r.getSignedQrData().isBlank())
                    .provider(r.getProvider() == null ? null : r.getProvider().name())
                    .environment(r.getEnvironment() == null ? null : r.getEnvironment().name())
                    .mock(Boolean.TRUE.equals(r.getIsMock()))
                    .registeredAt(r.getRegisteredAt())
                    .errorCode(r.getErrorCode())
                    .errorMessage(r.getErrorMessage())
                    .build();
        }
    }
}
