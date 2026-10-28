package com.insideinvoice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The credentials were CORRECT but the account has not proven its inbox yet
 * (self-service sign-up, {@code email_verified = false}).
 *
 * <p>Mapped to <b>403</b> deliberately, not 401: the axios client force-logs-out
 * on any 401, and a 401 here would wipe a perfectly good session on the
 * verify-email screen. 403 reads as "authenticated enough, not authorised yet",
 * which is exactly this state, and lets the SPA route straight to the OTP form
 * with the address pre-filled.</p>
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class EmailNotVerifiedException extends RuntimeException {

    public static final String CODE = "EMAIL_NOT_VERIFIED";

    private final String email;

    public EmailNotVerifiedException(String email) {
        super("Please verify your email address before signing in. "
                + "We've sent a 6-digit code to " + email + ".");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
