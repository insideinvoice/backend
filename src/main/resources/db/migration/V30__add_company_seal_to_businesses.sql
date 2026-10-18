-- V30: Persist the company seal/stamp preference on the business.
--
-- The preference previously lived only in the browser's localStorage, so it
-- never reached the server: the emailed invoice (rebuilt by the backend) and
-- the public share page (/i/:token, viewed on the CUSTOMER'S device) could not
-- show the seal the business had chosen. Storing it here lets every server
-- rendering match what the owner downloads.
--
-- Defaults preserve today's behaviour exactly: seal off, round seal type.
-- A business only shows a seal once it is explicitly enabled.
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS seal_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS seal_type VARCHAR(20) NOT NULL DEFAULT 'round';

COMMENT ON COLUMN businesses.seal_enabled IS
    'Whether the company seal/stamp is rendered on invoice documents and public share pages.';
COMMENT ON COLUMN businesses.seal_type IS
    'Seal variant: "round" (round seal) or "stamp" (rubber stamp).';
