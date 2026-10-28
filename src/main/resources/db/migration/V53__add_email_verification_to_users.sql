-- Email verification for self-service registration (POST /api/auth/register).
--
-- A sign-up is provisioned immediately but stays INVISIBLE to login until the
-- owner proves they control the inbox: register stores a 6-digit OTP (same
-- pattern as the forgot-password flow) and the login path refuses any account
-- with email_verified = false.
--
-- The OTP gets its OWN columns rather than reusing tmp_otp/otp_attempts:
-- forgot-password must keep working for a user who is mid-verification, and
-- the two flows would otherwise silently overwrite each other's code and
-- share one attempt budget.

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_otp VARCHAR(6);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_otp_attempts INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_otp_created_at TIMESTAMP;

-- Every account that exists BEFORE this migration predates verification: it
-- was created by an admin, imported, or already owns invoices. Flipping them
-- all to verified on deploy is the only safe choice — a DEFAULT of false would
-- lock the entire existing user base out of login the moment this ships.
UPDATE users SET email_verified = true;
