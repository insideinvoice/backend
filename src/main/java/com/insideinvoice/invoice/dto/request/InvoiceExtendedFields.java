package com.insideinvoice.invoice.dto.request;

import java.time.LocalDate;

/**
 * The V29 industry-extended optional fields shared by the create and update
 * invoice requests. Implemented implicitly by the request DTOs via Lombok's
 * generated getters, so the service and mapper can apply and validate these
 * fields with one code path instead of duplicating them per request type.
 *
 * <p>All values are reference/display only (rental agreement, asset/vehicle
 * identifiers, rental or service periods, patient/doctor references). They
 * never participate in totals or tax calculations.</p>
 */
public interface InvoiceExtendedFields {

    String getAgreementNumber();

    String getAssetNumber();

    String getSerialNumber();

    String getVehicleNumber();

    LocalDate getPeriodStart();

    LocalDate getPeriodEnd();

    LocalDate getBillingPeriodStart();

    LocalDate getBillingPeriodEnd();

    LocalDate getExpectedReturnDate();

    String getDepositReference();

    String getPatientReference();

    LocalDate getServiceDate();

    String getTreatmentReference();

    String getReferringDoctor();
}
