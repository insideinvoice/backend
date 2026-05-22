package com.insideinvoice.auth.service;

import com.insideinvoice.auth.dto.request.ForgotPasswordRequest;
import com.insideinvoice.auth.dto.request.LoginRequest;
import com.insideinvoice.auth.dto.request.ResetPasswordRequest;
import com.insideinvoice.auth.dto.request.SignupRequest;
import com.insideinvoice.auth.dto.response.JwtResponse;

public interface AuthService {

    JwtResponse signup(SignupRequest request);

    JwtResponse login(LoginRequest request);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
