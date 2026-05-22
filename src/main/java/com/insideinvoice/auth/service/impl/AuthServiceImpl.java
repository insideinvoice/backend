package com.insideinvoice.auth.service.impl;

import com.insideinvoice.auth.dto.request.ChangePasswordRequest;
import com.insideinvoice.auth.dto.request.ForgotPasswordRequest;
import com.insideinvoice.auth.dto.request.LoginRequest;
import com.insideinvoice.auth.dto.request.ResetPasswordRequest;
import com.insideinvoice.auth.dto.request.SignupRequest;
import com.insideinvoice.auth.dto.request.UpdateProfileRequest;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

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
                user.getId(), user.getEmail(), user.getBusinessId(), user.getName());

        log.info("User logged in successfully: {}", user.getEmail());
        return userMapper.toJwtResponse(user, token);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));

        String resetToken = UUID.randomUUID().toString();
        user.setResetPasswordToken(passwordEncoder.encode(resetToken));
        userRepository.save(user);

        log.info("Password reset token generated for user: {}", request.getEmail());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request.getToken() == null || request.getToken().isBlank()) {
            throw new BadRequestException("Reset token is required");
        }

        User user = userRepository.findByResetPasswordToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setResetPasswordToken(null);
        userRepository.save(user);

        log.info("Password reset successfully");
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
    public void changePassword(ChangePasswordRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setRawPassword(request.getNewPassword());
        userRepository.save(user);
        log.info("Password changed for user: {}", user.getEmail());
    }
}
