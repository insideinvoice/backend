-- V41: Optional second phone number for the business.
--
-- Printed alongside the primary phone wherever the owner's contact block
-- renders (e.g. a mobile + a landline). NULL means "not provided" —
-- renderers simply keep showing the primary phone alone.
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS additional_phone VARCHAR(20);

COMMENT ON COLUMN businesses.additional_phone IS
    'Optional second phone number; NULL = none.';
