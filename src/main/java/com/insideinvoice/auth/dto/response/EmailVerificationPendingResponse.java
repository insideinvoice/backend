package com.insideinvoice.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What {@code POST /api/auth/register} returns: NO session, only what the SPA
 * needs to park the visitor on the verify-email screen.
 *
 * <p>{@code requiresEmailVerification} is always true on this path — the field
 * exists so the client branches on a contract instead of on "accessToken is
 * missing", and so a future auto-verified registration path can reuse the same
 * response type by flipping the flag.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationPendingResponse {

    /** Echo of the (normalized) address the code was mailed to — pre-fills the OTP form. */
    private String email;

    private boolean requiresEmailVerification;

    /** How long the current code stays valid, surfaced so the UI can word the resend hint. */
    private int expiresInSeconds;

    /** Mirrors the rate-limit window the resend endpoint enforces, so the UI counts down honestly. */
    private int resendAfterSeconds;
}
