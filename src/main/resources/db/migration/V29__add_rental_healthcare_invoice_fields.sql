-- V29: Rental & Leasing + Healthcare & Wellness optional invoice fields.
-- All columns are nullable and purely additive: legacy invoices keep NULL,
-- every other industry never writes them, and no existing row is touched.
--   Rental:   agreement/contract no, asset/equipment id, serial no, vehicle
--             reg no, rental start/end, billing period, expected return,
--             deposit reference.
--   Health:   patient/customer ref, service date, treatment/session ref,
--             referring doctor, billing period (shared with rental).

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS agreement_number VARCHAR(100);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS asset_number VARCHAR(100);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS serial_number VARCHAR(100);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS vehicle_number VARCHAR(60);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS period_start DATE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS period_end DATE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS billing_period_start DATE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS billing_period_end DATE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS expected_return_date DATE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS deposit_reference VARCHAR(100);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS patient_reference VARCHAR(100);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS service_date DATE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS treatment_reference VARCHAR(100);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS referring_doctor VARCHAR(150);

COMMENT ON COLUMN invoices.agreement_number IS 'Rental agreement / contract number (Rental & Leasing).';
COMMENT ON COLUMN invoices.asset_number IS 'Asset / equipment identifier being hired (Rental & Leasing).';
COMMENT ON COLUMN invoices.serial_number IS 'Serial number of hired asset (Rental & Leasing).';
COMMENT ON COLUMN invoices.vehicle_number IS 'Vehicle registration number, vehicle rentals only.';
COMMENT ON COLUMN invoices.period_start IS 'Rental / service period start date.';
COMMENT ON COLUMN invoices.period_end IS 'Rental / service period end date.';
COMMENT ON COLUMN invoices.billing_period_start IS 'Billing period start (rental retainers, recurring wellness).';
COMMENT ON COLUMN invoices.billing_period_end IS 'Billing period end.';
COMMENT ON COLUMN invoices.expected_return_date IS 'Expected asset return date (rental).';
COMMENT ON COLUMN invoices.deposit_reference IS 'Reference only for a refundable security deposit — never counted as revenue.';
COMMENT ON COLUMN invoices.patient_reference IS 'Patient / customer reference shown instead of medical detail (Healthcare).';
COMMENT ON COLUMN invoices.service_date IS 'Date the service/session took place (Healthcare).';
COMMENT ON COLUMN invoices.treatment_reference IS 'Treatment / session reference (Healthcare).';
COMMENT ON COLUMN invoices.referring_doctor IS 'Referring doctor / referral reference (Healthcare).';
