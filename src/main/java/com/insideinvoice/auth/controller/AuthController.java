package com.insideinvoice.auth.controller;

import com.insideinvoice.auth.dto.request.ChangePasswordRequest;
import com.insideinvoice.auth.dto.request.ForgotPasswordRequest;
import com.insideinvoice.auth.dto.request.LoginRequest;
import com.insideinvoice.auth.dto.request.ResetPasswordOtpRequest;
import com.insideinvoice.auth.dto.request.SignupRequest;
import com.insideinvoice.auth.dto.request.UpdateProfileRequest;
import com.insideinvoice.auth.dto.request.VerifyOtpRequest;
import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.auth.dto.response.JwtResponse;
import com.insideinvoice.auth.service.AuthService;
import com.insideinvoice.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Auth management APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<JwtResponse>> signup(@Valid @RequestBody SignupRequest request) {
        JwtResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT token")
    public ResponseEntity<ApiResponse<JwtResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse httpResponse) {
        JwtResponse response = authService.login(request);

        Cookie jwtCookie = new Cookie("jwt", response.getAccessToken());
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(false);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(3600);
        httpResponse.addCookie(jwtCookie);

        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Send OTP for password reset")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody ForgotPasswordRequest request) {
        ApiResponse<Void> response = authService.sendOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP for password reset")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        ApiResponse<Void> response = authService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using OTP")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordOtpRequest request) {
        ApiResponse<Void> response = authService.resetPasswordWithOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and clear JWT cookie")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse httpResponse) {
        Cookie jwtCookie = new Cookie("jwt", null);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(0);
        httpResponse.addCookie(jwtCookie);

        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile (name)")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        authService.updateProfile(request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Profile updated"));
    }

    @PutMapping("/change-password")
    @Operation(summary = "Change user password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ApiResponse<Void> response = authService.changePassword(request, principal.getId());
        return ResponseEntity.ok(response);
    }
}
