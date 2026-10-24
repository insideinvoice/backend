package com.insideinvoice.auth.service;

import com.insideinvoice.auth.dto.request.ChangePasswordRequest;
import com.insideinvoice.auth.dto.request.ForgotPasswordRequest;
import com.insideinvoice.auth.dto.request.LoginRequest;
import com.insideinvoice.auth.dto.request.ResetPasswordOtpRequest;
import com.insideinvoice.auth.dto.request.SignupRequest;
import com.insideinvoice.auth.dto.request.UpdateProfileRequest;
import com.insideinvoice.auth.dto.request.VerifyOtpRequest;
import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.auth.dto.response.JwtResponse;
import org.springframework.security.core.Authentication;
import java.util.UUID;

public interface AuthService {

    JwtResponse signup(SignupRequest request);

    JwtResponse login(LoginRequest request);

    ApiResponse<Void> sendOtp(ForgotPasswordRequest request);

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
