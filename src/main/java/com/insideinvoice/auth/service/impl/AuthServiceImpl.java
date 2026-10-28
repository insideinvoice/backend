package com.insideinvoice.auth.service.impl;

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
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.mapper.UserMapper;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.auth.service.AuthService;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.DuplicateResourceException;
import com.insideinvoice.exception.EmailNotVerifiedException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.security.JwtTokenProvider;
import com.insideinvoice.security.UserPrincipal;
import com.insideinvoice.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final int OTP_EXPIRY_MINUTES = 10;

    /** Sign-up codes reuse the reset flow's expiry and guessing budget. */
    private static final int EMAIL_VERIFICATION_EXPIRY_MINUTES = OTP_EXPIRY_MINUTES;
    private static final int EMAIL_VERIFICATION_MAX_ATTEMPTS = OTP_MAX_ATTEMPTS;
    /** Client-side resend countdown — mirrors the SERVER's per-account cooldown below. */
    private static final int RESEND_VERIFICATION_COOLDOWN_SECONDS = 900;
    /**
     * Per-ACCOUNT floor between two verification emails. Without this, "Resend"
     * fires a fresh mail on every click (the rate limiter only caps per-IP
     * floods, not one determined user hammering their own button). An owner who
     * just asked for a code does not need another for 15 minutes.
     */
    private static final int RESEND_VERIFICATION_MIN_INTERVAL_MINUTES = 15;
    /**
     * Password-reset mails reuse the same per-account floor. Without it, anyone
     * could email-bomb an arbitrary victim by hitting /auth/forgot-password in
     * a loop from many IPs (the per-IP limiter does not stop a distributed
     * flood aimed at one inbox).
     */
    private static final int RESEND_RESET_MIN_INTERVAL_MINUTES = 15;

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
        // Case-insensitive: "John" vs "john" and A@b.com vs a@b.com are the
        // same identity — no two accounts may register with either.
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        if (userRepository.existsByUsernameIgnoreCase(request.getUsername())) {
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
                // Admin-provisioned accounts skip inbox proof: a privileged user
                // just vouched for the address by typing it into the console.
                .emailVerified(true)
                .build();
        user = userRepository.save(user);

        business.setOwnerName(request.getName());
        businessRepository.save(business);

        String token = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getBusinessId(), user.getName(),
                false, user.getTokenVersion());

        log.info("User signed up successfully: {} as {}", user.getEmail(), role);
        return userMapper.toJwtResponse(user, token);
    }

    /**
     * Public self-service registration. Reuses the same provisioning journey as
     * signup (stub business + USER account + immediate JWT) but with a strict,
     * privilege-safe contract: the role is hardcoded (a public request can never
     * choose one), the username is derived from the email (the schema requires
     * one; visitors never see it), the email is normalized (trim + lowercase —
     * uniqueness is enforced case-insensitively by the V39 LOWER() index, so
     * storing the canonical form keeps lookups and dedup consistent), and no
     * plaintext password copy is kept (unlike the legacy admin-driven signup,
     * public accounts store only the BCrypt hash).
     *
     * <p>Duplicate emails are rejected with 409 before any write. The
     * concurrent race (two identical registrations passing the pre-check at once)
     * is closed by the unique index; the resulting
     * {@link DataIntegrityViolationException} maps to the same 409 and rolls the
     * whole transaction back — including the stub business — so a retry can
     * never leave duplicate businesses behind.</p>
     *
     * <p><b>No session is issued.</b> A 6-digit code is generated, persisted on
     * the account and mailed; login refuses the account until that code is
     * entered (see {@link #verifyEmail}). The mail is dispatched AFTER the
     * commit — if the transaction rolls back (race, validation) no code ever
     * leaves the building for an account that does not exist.</p>
     */
    @Override
    @Transactional
    public EmailVerificationPendingResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        String name = request.getName().trim();

        if (!Objects.equals(request.getPassword(), request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        // A VERIFIED account owns this address — that's a genuine duplicate.
        // An UNVERIFIED one never proved inbox ownership, so it has no standing:
        // re-registering OVERWRITES it (fresh password + OTP + re-send) instead of
        // locking the address out forever. The real owner always wins, because
        // sign-in still requires the OTP mailed to that inbox.
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null && existing.isEmailVerified()) {
            throw new DuplicateResourceException("An account", "email", email);
        }

        String otp = generateOtp();
        LocalDateTime now = LocalDateTime.now();

        User user;
        if (existing != null) {
            // Overwrite the unverified stub IN PLACE — reuse its business so
            // abandoned sign-ups never pile up orphaned stub businesses. The
            // conditional WHERE emailVerified=false makes this race-safe: if the
            // account is verified mid-flight, 0 rows match and we fall through to
            // the duplicate error below.
            int updated = userRepository.overwriteUnverifiedUser(
                    existing.getId(), name, passwordEncoder.encode(request.getPassword()), otp, now);
            if (updated == 0) {
                throw new DuplicateResourceException("An account", "email", email);
            }
            user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            businessRepository.findById(user.getBusinessId()).ifPresent(biz -> {
                biz.setBusinessName(name + "'s Business");
                biz.setOwnerName(name);
                businessRepository.save(biz);
            });
            log.info("Unverified account re-registered (overwritten): {}", email);
        } else {
            String username = deriveUniqueUsername(email);
            Business business = businessRepository.save(Business.builder()
                    .businessName(name + "'s Business")
                    .ownerName(name)
                    .nextInvoiceSequence(1L)
                    .build());
            try {
                user = userRepository.save(User.builder()
                        .name(name)
                        .username(username)
                        .email(email)
                        .password(passwordEncoder.encode(request.getPassword()))
                        .role(Role.USER)
                        .businessId(business.getId())
                        .businessSetupCompleted(false)
                        .mustChangePassword(false)
                        .emailVerified(false)
                        .emailOtp(otp)
                        .emailOtpAttempts(0)
                        .emailOtpCreatedAt(now)
                        .build());
            } catch (DataIntegrityViolationException race) {
                // Lost the unique-index race on email (or the derived username).
                // The transaction — stub business included — rolls back with this
                // exception; the handler turns it into a plain 409 for the client.
                throw new DuplicateResourceException("An account", "email", email);
            }
            log.info("User registered self-service: {} (username {}), email verification pending",
                    user.getEmail(), username);
        }

        sendVerificationOtpAfterCommit(user.getId(), otp, name);

        return EmailVerificationPendingResponse.builder()
                .email(user.getEmail())
                .requiresEmailVerification(true)
                .expiresInSeconds(EMAIL_VERIFICATION_EXPIRY_MINUTES * 60)
                .resendAfterSeconds(RESEND_VERIFICATION_COOLDOWN_SECONDS)
                .build();
    }

    @Override
    // Deliberately NOT @Transactional — same reasoning as sendOtp below. If this
    // method held a transaction, every failed attempt would throw and ROLL BACK
    // the attempt counter it had just incremented, leaving a 6-digit code
    // brute-forceable. Each write here commits on its own instead.
    public JwtResponse verifyEmail(VerifyEmailRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadRequestException("Invalid or expired code"));

        // SECURITY: this endpoint is permitAll, so it must NEVER mint a session
        // on the strength of an email address alone. An already-verified
        // account means the code was consumed long ago — anyone (not just the
        // owner) could otherwise POST a victim's address with a garbage OTP and
        // walk away with a valid JWT. Tell them to sign in instead; the old
        // "double submit convenience" was a full account-takeover hole.
        if (user.isEmailVerified()) {
            throw new BadRequestException("This email is already verified. Please sign in.");
        }

        if (user.getEmailOtp() == null) {
            throw new BadRequestException("Invalid or expired code. Please request a new one.");
        }

        // Capture the code we are validating BEFORE any write. Every mutation
        // below is gated on this exact value still being live, so a concurrent
        // resend/re-registration (which rotates the code) can never be
        // validated against — or clobbered by — this stale read.
        final String candidateOtp = user.getEmailOtp();

        if (user.getEmailOtpCreatedAt() != null
                && user.getEmailOtpCreatedAt().plusMinutes(EMAIL_VERIFICATION_EXPIRY_MINUTES)
                        .isBefore(LocalDateTime.now())) {
            userRepository.clearEmailOtpIfMatches(user.getId(), candidateOtp);
            throw new BadRequestException("That code has expired. Please request a new one.");
        }

        int attempts = user.getEmailOtpAttempts() != null ? user.getEmailOtpAttempts() : 0;
        if (attempts >= EMAIL_VERIFICATION_MAX_ATTEMPTS) {
            userRepository.clearEmailOtpIfMatches(user.getId(), candidateOtp);
            throw new BadRequestException("Too many failed attempts. Please request a new code.");
        }

        // Constant-time compare: this is an online guessing target, so the
        // check must not leak a correct prefix through timing.
        if (!otpMatches(candidateOtp, request.getOtp())) {
            // Atomic UPDATE, not a save() on this entity: it must survive the
            // exception thrown on the next line (a save inside a transaction
            // that then throws is rolled back — the counter would never move).
            // Gated on the live code so a stale failure cannot burn attempts
            // against a code the owner just had resent.
            userRepository.incrementEmailOtpAttempts(user.getId());
            throw new BadRequestException("Invalid or expired code");
        }

        // The check above is the test; this conditional UPDATE is the act. It
        // only flips the account if the code we validated is STILL the live one
        // and the account is STILL unverified — so a verification that races a
        // re-registration or a resend matches 0 rows and is rejected as stale
        // instead of silently verifying against an overwritten account.
        int verified = userRepository.markEmailVerifiedIfOtpMatches(user.getId(), candidateOtp);
        if (verified == 0) {
            throw new BadRequestException("Invalid or expired code. Please request a new one.");
        }

        // Re-read for a clean entity (tokenVersion/businessId may have moved)
        // before minting the session — never build a JWT from a stale row.
        user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        log.info("Email verified for user: {}", user.getEmail());
        // The SPA continues into the ordinary journey: business setup if it is
        // still outstanding, dashboard otherwise.
        return issueSession(user, false);
    }

    @Override
    // NOT @Transactional: like sendOtp, the Resend call must not hold one of the
    // 10 Hikari connections while it waits on the network.
    public ApiResponse<EmailVerificationPendingResponse> resendVerificationOtp(ResendVerificationRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        // Every branch returns this SAME message + envelope so the response never
        // reveals whether an account exists. Only the wait hint (data) differs,
        // and it defaults to the full window for anyone who could receive a code.
        int resendAfterSeconds = RESEND_VERIFICATION_COOLDOWN_SECONDS;

        if (user != null && !user.isEmailVerified()) {
            // Per-account cooldown: a code was already mailed within the last
            // 15 minutes. Do NOT rotate (the owner may be mid-entry on the
            // current code) and do NOT send again — the click is absorbed.
            if (user.getEmailOtpCreatedAt() != null
                    && user.getEmailOtpCreatedAt()
                            .plusMinutes(RESEND_VERIFICATION_MIN_INTERVAL_MINUTES)
                            .isAfter(LocalDateTime.now())) {
                long waitSeconds = java.time.Duration.between(
                        LocalDateTime.now(), user.getEmailOtpCreatedAt()
                                .plusMinutes(RESEND_VERIFICATION_MIN_INTERVAL_MINUTES))
                        .getSeconds();
                log.info("Verification resend throttled for {} — a code was sent {}s ago",
                        user.getEmail(), java.time.Duration.between(
                                user.getEmailOtpCreatedAt(), LocalDateTime.now()).getSeconds());
                resendAfterSeconds = (int) Math.max(waitSeconds, 1);
            } else {
                String otp = generateOtp();
                user.setEmailOtp(otp);
                user.setEmailOtpAttempts(0);
                user.setEmailOtpCreatedAt(LocalDateTime.now());
                userRepository.save(user);
                emailService.sendVerificationEmail(user.getEmail(), otp, user.getName());
                log.info("Verification OTP re-sent to: {}", user.getEmail());
            }
        } else {
            // Unknown address OR already verified — same outcome to the caller.
            log.info("Verification resend requested for {}",
                    user == null ? "non-existent email" : "already-verified account " + email);
        }

        // Identical message either way: never confirm whether an address has an
        // account, or whether verification is still outstanding.
        return ApiResponse.success(
                "If that address can receive mail, a new 6-digit code is on its way.",
                new EmailVerificationPendingResponse(
                        null, true, EMAIL_VERIFICATION_EXPIRY_MINUTES * 60, resendAfterSeconds));
    }

    /**
     * Mails the sign-up code once the registering transaction has committed.
     * Falls back to an immediate send when no transaction is active (tests).
     */
    private void sendVerificationOtpAfterCommit(Long userId, String otp, String name) {
        Runnable send = () -> {
            User stored = userRepository.findById(userId).orElse(null);
            String recipient = stored != null ? stored.getEmail() : null;
            String holder = stored != null ? stored.getName() : name;
            if (recipient != null) {
                emailService.sendVerificationEmail(recipient, otp, holder);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    /** Length-independent, constant-time OTP comparison. */
    private static boolean otpMatches(String expected, String submitted) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                submitted.getBytes(StandardCharsets.UTF_8));
    }

    /** Single place a session is minted, so every entry point stays in step. */
    private JwtResponse issueSession(User user, boolean rememberMe) {
        String token = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getBusinessId(), user.getName(),
                rememberMe, user.getTokenVersion());
        return userMapper.toJwtResponse(user, token);
    }

    /** Canonical email form: trimmed, lowercased. Never mutates existing accounts. */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Derives a free username from the email's local part (schema requires
     * 3-50 chars). Sanitizes to the handle charset, appends numeric suffixes on
     * collision, and falls back to a random suffix so registration never fails
     * on username contention alone.
     */
    private String deriveUniqueUsername(String email) {
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        String base = local.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "")
                .replaceAll("^[._-]+|[._-]+$", "");
        if (base.length() < 3) {
            // Handle charset collapsed to nothing/too short ("a@x.com", "++@x.com")
            // — prefix with a stable filler that still reads as a handle.
            base = "user" + base;
        }
        base = truncateUsername(base);

        String candidate = base;
        for (int i = 2; i <= 50; i++) {
            if (!userRepository.existsByUsernameIgnoreCase(candidate)) {
                return candidate;
            }
            candidate = truncateUsername(base + "-" + i);
        }
        // Extreme contention: go random rather than fail the registration.
        return truncateUsername(base + "-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private static String truncateUsername(String value) {
        return value.length() <= 50 ? value : value.substring(0, 50);
    }

    @Override
    public JwtResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));

        // Credentials are correct, but a self-service sign-up that never proved
        // its inbox gets no session — the SPA routes this to the OTP screen.
        if (!user.isEmailVerified()) {
            log.info("Login refused for unverified account: {}", user.getEmail());
            throw new EmailNotVerifiedException(user.getEmail());
        }

        String token = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getBusinessId(), user.getName(),
                Boolean.TRUE.equals(request.getRememberMe()), user.getTokenVersion());

        log.info("User logged in successfully: {}", user.getEmail());
        return userMapper.toJwtResponse(user, token);
    }

    @Override
    // Deliberately NOT @Transactional: the Resend HTTP call (connect 10s / read 20s)
    // must not run while holding one of the 10 Hikari connections. The save below runs
    // in its own short transaction; a failed email now leaves the OTP stored instead of
    // rolling it back, and the user can simply request another one.
    public ApiResponse<EmailVerificationPendingResponse> sendOtp(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        // Default wait = the full window. Every branch returns this SAME message
        // so the response never reveals whether an account exists; only the wait
        // hint in data differs (and unknown emails get the full window).
        int resendAfterSeconds = RESEND_RESET_MIN_INTERVAL_MINUTES * 60;

        if (user != null) {
            // Per-account cooldown: a reset code was already mailed within the
            // last 15 minutes. Absorb the click — do NOT rotate (the owner may
            // be mid-entry on the current code) and do NOT send again. This is
            // the only thing standing between a victim's inbox and a
            // distributed flood the per-IP limiter cannot see.
            if (user.getOtpCreatedAt() != null
                    && user.getOtpCreatedAt()
                            .plusMinutes(RESEND_RESET_MIN_INTERVAL_MINUTES)
                            .isAfter(LocalDateTime.now())) {
                long waitSeconds = java.time.Duration.between(
                        LocalDateTime.now(), user.getOtpCreatedAt()
                                .plusMinutes(RESEND_RESET_MIN_INTERVAL_MINUTES))
                        .getSeconds();
                log.info("Password-reset resend throttled for {} — a code was sent {}s ago",
                        user.getEmail(), java.time.Duration.between(
                                user.getOtpCreatedAt(), LocalDateTime.now()).getSeconds());
                resendAfterSeconds = (int) Math.max(waitSeconds, 1);
            } else {
                String otp = generateOtp();
                user.setTmpOtp(otp);
                user.setOtpAttempts(0);
                user.setOtpCreatedAt(LocalDateTime.now());
                userRepository.save(user);

                emailService.sendOtpEmail(user.getEmail(), otp);
                log.info("OTP sent to user: {}", user.getEmail());
            }
        } else {
            log.info("OTP requested for non-existent email: {}", request.getEmail());
        }

        // Always return the same message — don't reveal if email exists.
        // The wait hint lets the UI count down the REAL server cooldown instead
        // of pretending a fresh code went out on every click.
        return ApiResponse.success("OTP sent to your email.",
                new EmailVerificationPendingResponse(
                        null, false, OTP_EXPIRY_MINUTES * 60, resendAfterSeconds));
    }

    @Override
    public ApiResponse<Void> verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Invalid or expired OTP"));

        if (user.getTmpOtp() == null) {
            throw new BadRequestException("Invalid or expired OTP");
        }

        // Capture the code we are validating; every write below is a targeted
        // UPDATE gated on this exact value still being live (a resend rotates
        // it). Full-entity save() would write back every stale column of the
        // row we just read — including a password a concurrent reset changed.
        final String candidateOtp = user.getTmpOtp();

        // Check OTP expiry
        if (user.getOtpCreatedAt() != null &&
                user.getOtpCreatedAt().plusMinutes(OTP_EXPIRY_MINUTES).isBefore(LocalDateTime.now())) {
            userRepository.clearTmpOtpIfMatches(user.getId(), candidateOtp);
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        // Check max attempts
        int attempts = user.getOtpAttempts() != null ? user.getOtpAttempts() : 0;
        if (attempts >= OTP_MAX_ATTEMPTS) {
            userRepository.clearTmpOtpIfMatches(user.getId(), candidateOtp);
            throw new BadRequestException("Too many failed attempts. Please request a new OTP.");
        }

        // Constant-time compare — same online-guessing posture as verify-email
        // (a plain equals() can leak a correct prefix through timing).
        if (!otpMatches(candidateOtp, request.getOtp())) {
            userRepository.incrementTmpOtpAttempts(user.getId(), candidateOtp);
            throw new BadRequestException("Invalid or expired OTP");
        }

        // OTP verified successfully — atomic, code-gated counter reset. NOT a
        // save(): no @Transactional here means no rollback could ever undo a
        // throw, and no stale column rides along with the write.
        userRepository.resetTmpOtpAttempts(user.getId(), candidateOtp);

        log.info("OTP verified for user: {}", user.getEmail());
        return ApiResponse.success("OTP verified");
    }

    @Override
    // NOT @Transactional: every write below is a targeted, code-gated UPDATE in
    // its own transaction. Under a wrapping transaction these deliberate writes
    // would be rolled back by the exception thrown right after them — which is
    // exactly how the attempt counter used to vanish, leaving a 6-digit reset
    // code brute-forceable no matter how many wrong guesses were sent.
    public ApiResponse<Void> resetPasswordWithOtp(ResetPasswordOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Invalid or expired OTP"));

        if (user.getTmpOtp() == null) {
            throw new BadRequestException("Invalid or expired OTP");
        }

        // The code we validated, captured before any write. Every UPDATE below
        // is gated on this exact value still being live.
        final String candidateOtp = user.getTmpOtp();

        // Check OTP expiry
        if (user.getOtpCreatedAt() != null &&
                user.getOtpCreatedAt().plusMinutes(OTP_EXPIRY_MINUTES).isBefore(LocalDateTime.now())) {
            userRepository.clearTmpOtpIfMatches(user.getId(), candidateOtp);
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        // Enforce the shared attempt counter. This endpoint used to skip both the
        // attempt limit and the increment, so a 6-digit OTP was brute-forceable by
        // calling /reset-password directly (verify-otp's limit never applied).
        int attempts = user.getOtpAttempts() != null ? user.getOtpAttempts() : 0;
        if (attempts >= OTP_MAX_ATTEMPTS) {
            userRepository.clearTmpOtpIfMatches(user.getId(), candidateOtp);
            throw new BadRequestException("Too many failed attempts. Please request a new OTP.");
        }

        // Constant-time compare, atomic gated increment on failure.
        if (!otpMatches(candidateOtp, request.getOtp())) {
            userRepository.incrementTmpOtpAttempts(user.getId(), candidateOtp);
            throw new BadRequestException("Invalid or expired OTP");
        }

        // Consume the code and set the new hash in ONE atomic step, gated on the
        // code we validated still being live — a resend or a competing reset
        // that rotated it mid-flight matches 0 rows and is rejected as stale
        // instead of applying a password against a stale read. tokenVersion is
        // bumped here on purpose: a password reset is often the response to a
        // stolen token, so every existing session must die with the old hash.
        int updated = userRepository.resetPasswordIfOtpMatches(
                user.getId(), candidateOtp,
                passwordEncoder.encode(request.getNewPassword()),
                request.getNewPassword());
        if (updated == 0) {
            throw new BadRequestException("Invalid or expired OTP. Please request a new one.");
        }

        log.info("Password reset successfully for user: {}", user.getEmail());
        return ApiResponse.success("Password reset successful");
    }

    @Override
    @Transactional
    public void updateProfile(UpdateProfileRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String newUsername = request.getUsername().trim();
        // equalsIgnoreCase lets a user change only the casing of their OWN
        // handle ("john" → "John") while still blocking anyone else's,
        // whatever the casing.
        if (!user.getUsername().equalsIgnoreCase(newUsername)
                && userRepository.existsByUsernameIgnoreCase(newUsername)) {
            throw new DuplicateResourceException("User", "username", newUsername);
        }

        user.setName(request.getName());
        user.setUsername(newUsername);
        userRepository.save(user);
        log.info("Profile updated for user: {}", user.getEmail());
    }

    @Override
    public boolean isUsernameAvailable(String username) {
        String candidate = username == null ? "" : username.trim();
        if (candidate.isEmpty()) {
            return false;
        }
        return !userRepository.existsByUsernameIgnoreCase(candidate);
    }

    @Override
    public com.insideinvoice.auth.dto.response.UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return userMapper.toProfileResponse(user);
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
    @Override
    @Transactional
    public void revokeSessions(Long userId) {
        userRepository.incrementTokenVersion(userId);
        log.info("All sessions revoked for userId {}", userId);
    }
}
