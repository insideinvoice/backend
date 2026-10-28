package com.insideinvoice.auth.service;

import com.insideinvoice.auth.dto.request.ChangePasswordRequest;
import com.insideinvoice.auth.dto.request.ForgotPasswordRequest;
import com.insideinvoice.auth.dto.request.LoginRequest;
import com.insideinvoice.auth.dto.request.RegisterRequest;
import com.insideinvoice.auth.dto.request.ResendVerificationRequest;
import com.insideinvoice.auth.dto.request.ResetPasswordOtpRequest;
import com.insideinvoice.auth.dto.request.SignupRequest;
import com.insideinvoice.auth.dto.request.UpdateProfileRequest;
import com.insideinvoice.auth.dto.request.VerifyEmailRequest;
import com.insideinvoice.auth.dto.request.VerifyOtpRequest;
import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.auth.dto.response.EmailVerificationPendingResponse;
import com.insideinvoice.auth.dto.response.JwtResponse;
import org.springframework.security.core.Authentication;
import java.util.UUID;

public interface AuthService {

    JwtResponse signup(SignupRequest request);

    /**
     * Public self-service registration (POST /api/auth/register). Accepts only
     * name/email/password/confirmPassword — the account is always created with
     * the standard USER role and a freshly provisioned stub business; a public
     * request can never supply a role, username or business id.
     *
     * <p>Returns an email-verification PENDING response rather than a session:
     * the account exists but is invisible to login until the owner enters the
     * 6-digit code mailed to the address (see {@link #verifyEmail}).</p>
     */
    com.insideinvoice.auth.dto.response.EmailVerificationPendingResponse register(RegisterRequest request);

    /**
     * Checks the sign-up OTP against the stored value and, on success, marks
     * the account verified and issues the session — the SPA continues into the
     * normal first-run journey (business setup if it is still outstanding).
     */
    JwtResponse verifyEmail(VerifyEmailRequest request);

    /** Regenerates and re-mails the sign-up OTP. Never reveals whether the address exists. */
    ApiResponse<EmailVerificationPendingResponse> resendVerificationOtp(ResendVerificationRequest request);

    JwtResponse login(LoginRequest request);

    ApiResponse<EmailVerificationPendingResponse> sendOtp(ForgotPasswordRequest request);

    ApiResponse<Void> verifyOtp(VerifyOtpRequest request);

    ApiResponse<Void> resetPasswordWithOtp(ResetPasswordOtpRequest request);

    void updateProfile(UpdateProfileRequest request, Long userId);

    ApiResponse<Void> changePassword(ChangePasswordRequest request, Long userId);

    /** Ends every server-side session for the user by rotating their token version. */
    void revokeSessions(Long userId);

    /**
     * Instagram-style availability probe for the settings / signup username
     * field. Case-insensitive; blank input is never available.
     */
    boolean isUsernameAvailable(String username);

    /** Fresh profile of the signed-in user — keeps the client's stored snapshot current. */
    com.insideinvoice.auth.dto.response.UserProfileResponse getUserProfile(Long userId);
}
