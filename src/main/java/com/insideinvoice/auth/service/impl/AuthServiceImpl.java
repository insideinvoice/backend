package com.insideinvoice.auth.service.impl;

import com.insideinvoice.auth.dto.request.ChangePasswordRequest;
import com.insideinvoice.auth.dto.request.ForgotPasswordRequest;
import com.insideinvoice.auth.dto.request.LoginRequest;
import com.insideinvoice.auth.dto.request.ResetPasswordOtpRequest;
import com.insideinvoice.auth.dto.request.SignupRequest;
import com.insideinvoice.auth.dto.request.UpdateProfileRequest;
import com.insideinvoice.auth.dto.request.VerifyOtpRequest;
import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.auth.dto.response.JwtResponse;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.mapper.UserMapper;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.auth.service.AuthService;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.DuplicateResourceException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.security.JwtTokenProvider;
import com.insideinvoice.security.UserPrincipal;
import com.insideinvoice.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final int OTP_EXPIRY_MINUTES = 10;

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final EmailService emailService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    @Transactional
    public JwtResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("User", "username", request.getUsername());
        }

        boolean isAdminRequest = false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            isAdminRequest = principal.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }

        Role role = Role.USER;
        if (isAdminRequest && request.getRole() != null) {
            try {
                role = Role.valueOf(request.getRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                role = Role.USER;
            }
        }

        Business business = Business.builder()
                .businessName(request.getBusinessName() != null ? request.getBusinessName() : request.getName() + "'s Business")
                .ownerName(request.getName())
                .nextInvoiceSequence(1L)
                .build();
        business = businessRepository.save(business);

        User user = User.builder()
                .name(request.getName())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .rawPassword(request.getPassword())
                .role(role)
                .businessId(business.getId())
                .businessSetupCompleted(false)
                .mustChangePassword(false)
                .build();
        user = userRepository.save(user);

        business.setOwnerName(request.getName());
        businessRepository.save(business);

        String token = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getBusinessId(), user.getName());

        log.info("User signed up successfully: {} as {}", user.getEmail(), role);
        return userMapper.toJwtResponse(user, token);
    }

    @Override
    public JwtResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));

        String token = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getBusinessId(), user.getName(),
                Boolean.TRUE.equals(request.getRememberMe()));

        log.info("User logged in successfully: {}", user.getEmail());
        return userMapper.toJwtResponse(user, token);
    }

    @Override
    // Deliberately NOT @Transactional: the Resend HTTP call (connect 10s / read 20s)
    // must not run while holding one of the 10 Hikari connections. The save below runs
    // in its own short transaction; a failed email now leaves the OTP stored instead of
    // rolling it back, and the user can simply request another one.
    public ApiResponse<Void> sendOtp(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user != null) {
            String otp = generateOtp();
            user.setTmpOtp(otp);
            user.setOtpAttempts(0);
            user.setOtpCreatedAt(LocalDateTime.now());
            userRepository.save(user);

            emailService.sendOtpEmail(user.getEmail(), otp);
            log.info("OTP sent to user: {}", user.getEmail());
        } else {
            log.info("OTP requested for non-existent email: {}", request.getEmail());
        }

        // Always return the same message — don't reveal if email exists
        return ApiResponse.success("OTP sent to your email.");
    }

    @Override
    @Transactional
    public ApiResponse<Void> verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Invalid or expired OTP"));

        if (user.getTmpOtp() == null) {
            throw new BadRequestException("Invalid or expired OTP");
        }

        // Check OTP expiry
        if (user.getOtpCreatedAt() != null &&
                user.getOtpCreatedAt().plusMinutes(OTP_EXPIRY_MINUTES).isBefore(LocalDateTime.now())) {
            user.setTmpOtp(null);
            user.setOtpAttempts(0);
            user.setOtpCreatedAt(null);
            userRepository.save(user);
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        // Check max attempts
        int attempts = user.getOtpAttempts() != null ? user.getOtpAttempts() : 0;
        if (attempts >= OTP_MAX_ATTEMPTS) {
            user.setTmpOtp(null);
            user.setOtpAttempts(0);
            user.setOtpCreatedAt(null);
            userRepository.save(user);
            throw new BadRequestException("Too many failed attempts. Please request a new OTP.");
        }

        if (!user.getTmpOtp().equals(request.getOtp())) {
            user.setOtpAttempts(attempts + 1);
            userRepository.save(user);
            throw new BadRequestException("Invalid or expired OTP");
        }

        // OTP verified successfully
        user.setOtpAttempts(0);
        userRepository.save(user);

        log.info("OTP verified for user: {}", user.getEmail());
        return ApiResponse.success("OTP verified");
    }

    @Override
    @Transactional
    public ApiResponse<Void> resetPasswordWithOtp(ResetPasswordOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Invalid or expired OTP"));

        if (user.getTmpOtp() == null) {
            throw new BadRequestException("Invalid or expired OTP");
        }

        // Check OTP expiry
        if (user.getOtpCreatedAt() != null &&
                user.getOtpCreatedAt().plusMinutes(OTP_EXPIRY_MINUTES).isBefore(LocalDateTime.now())) {
            user.setTmpOtp(null);
            user.setOtpAttempts(0);
            user.setOtpCreatedAt(null);
            userRepository.save(user);
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        // Enforce the shared attempt counter. This endpoint used to skip both the
        // attempt limit and the increment, so a 6-digit OTP was brute-forceable by
        // calling /reset-password directly (verify-otp's limit never applied).
        int attempts = user.getOtpAttempts() != null ? user.getOtpAttempts() : 0;
        if (attempts >= OTP_MAX_ATTEMPTS) {
            user.setTmpOtp(null);
            user.setOtpAttempts(0);
            user.setOtpCreatedAt(null);
            userRepository.save(user);
            throw new BadRequestException("Too many failed attempts. Please request a new OTP.");
        }

        if (!user.getTmpOtp().equals(request.getOtp())) {
            user.setOtpAttempts(attempts + 1);
            userRepository.save(user);
            throw new BadRequestException("Invalid or expired OTP");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setRawPassword(request.getNewPassword());
        user.setTmpOtp(null);
        user.setOtpAttempts(0);
        user.setOtpCreatedAt(null);
        user.setMustChangePassword(false);
        user.setResetPasswordToken(null);
        userRepository.save(user);

        log.info("Password reset successfully for user: {}", user.getEmail());
        return ApiResponse.success("Password reset successful");
    }

    @Override
    @Transactional
    public void updateProfile(UpdateProfileRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!user.getUsername().equals(request.getUsername()) && userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("User", "username", request.getUsername());
        }

        user.setName(request.getName());
        user.setUsername(request.getUsername());
        userRepository.save(user);
        log.info("Profile updated for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public ApiResponse<Void> changePassword(ChangePasswordRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setRawPassword(request.getNewPassword());
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.info("Password changed for user: {}", user.getEmail());

        return ApiResponse.success("Password changed successfully");
    }

    private String generateOtp() {
        int otp = SECURE_RANDOM.nextInt(900000) + 100000;
        return String.valueOf(otp);
    }
}