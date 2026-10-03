package com.insideinvoice.auth.service;

import com.insideinvoice.auth.dto.request.ChangePasswordRequest;
import com.insideinvoice.auth.dto.request.ForgotPasswordRequest;
import com.insideinvoice.auth.dto.request.LoginRequest;
import com.insideinvoice.auth.dto.request.ResetPasswordRequest;
import com.insideinvoice.auth.dto.request.SignupRequest;
import com.insideinvoice.auth.dto.request.UpdateProfileRequest;
import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.auth.dto.response.JwtResponse;
import org.springframework.security.core.Authentication;
import java.util.UUID;

public interface AuthService {

    JwtResponse signup(SignupRequest request);

    JwtResponse login(LoginRequest request);

    ApiResponse<Void> forgotPassword(ForgotPasswordRequest request);

    ApiResponse<Void> resetPassword(ResetPasswordRequest request);

    void updateProfile(UpdateProfileRequest request, Long userId);

    ApiResponse<Void> changePassword(ChangePasswordRequest request, Long userId);
}
