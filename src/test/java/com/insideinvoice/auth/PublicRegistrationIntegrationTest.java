package com.insideinvoice.auth;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.invoice.entity.Invoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public self-service registration (POST /api/auth/register) end-to-end.
 *
 * <p>Covers the task contract: success + provisioning, validation, duplicate
 * handling (including the concurrent race), password hashing, privileged-field
 * injection, protection of the rest of the API, tenant isolation, and —
 * mandatory — that every pre-existing login (seeded user AND seeded admin from
 * V25) keeps working untouched.</p>
 *
 * <p><b>Email verification</b>: register no longer issues a session. It stores a
 * 6-digit code, login refuses the account with 403 EMAIL_NOT_VERIFIED, and
 * /verify-email is what mints the token. The verification-specific cases live in
 * their own block below.</p>
 *
 * <p>Rate-limit note: RateLimitFilter hard-codes 5/min/IP on /api/auth/register
 * and PublicEndpointGuardFilter budgets /api/auth/ at 20/min/IP. Both honour
 * X-Forwarded-For, so every request here carries a fresh synthetic client IP —
 * the limiter logic itself is exercised by PublicRateLimitTest.</p>
 */
class PublicRegistrationIntegrationTest extends BaseIntegrationTest {

    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger IP_SEQ = new AtomicInteger(1);

    /** Fresh synthetic client IP so per-IP rate windows never collide across tests. */
    private static String freshIp() {
        int n = IP_SEQ.getAndIncrement();
        return "10.77." + (n / 250) + "." + (n % 250);
    }

    private static String runId() {
        return Long.toHexString(System.nanoTime() % 1_000_000_000L);
    }

    private String registerBody(String name, String email, String password, String confirm) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("name", name, "email", email, "password", password, "confirmPassword", confirm));
    }

    private ResultActions postRegister(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", freshIp())
                .content(body));
    }

    private ResultActions postLogin(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", freshIp())
                .content(objectMapper.writeValueAsString(
                        Map.of("email", email, "password", password))));
    }

    private ResultActions postVerify(String email, String otp) throws Exception {
        return mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", freshIp())
                .content(objectMapper.writeValueAsString(Map.of("email", email, "otp", otp))));
    }

    private ResultActions postResend(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", freshIp())
                .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    /** Registers, reads the code the account actually stored, and returns a verified session token. */
    private String registerVerified(String name, String email) throws Exception {
        postRegister(registerBody(name, email, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());
        String otp = userRepository.findByEmailIgnoreCase(email).orElseThrow().getEmailOtp();
        MvcResult verified = postVerify(email, otp).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(verified.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    // ------------------------------------------------------- email verification

    @Test
    @DisplayName("SECURITY: verify-email on an ALREADY-verified account -> 400, never a session")
    void alreadyVerifiedAccountNeverGetsSessionFromVerifyEmail() throws Exception {
        String rid = runId();
        String email = "hardened-" + rid + "@example.com";
        String realToken = registerVerified("Legit Owner", email);
        assertThat(realToken).isNotBlank();

        // THE takeover attempt: the endpoint is permitAll, so an attacker who
        // knows a victim's address used to be able to POST it with any garbage
        // OTP and receive a valid JWT. It must now be refused outright.
        for (String garbage : new String[] {"000000", "123456", "", "      ", "aaaaaa"}) {
            postVerify(email, garbage)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        // And no token was minted for any of those attempts.
        User after = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(after.isEmailVerified()).isTrue();
        assertThat(after.getName()).isEqualTo("Legit Owner");
    }

    @Test
    @DisplayName("SECURITY: correct OTP can be used ONCE — replay after verify -> 400, no second session")
    void verificationOtpIsSingleUse() throws Exception {
        String rid = runId();
        String email = "singleuse-" + rid + "@example.com";

        postRegister(registerBody("Once Only", email, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());
        String otp = userRepository.findByEmailIgnoreCase(email).orElseThrow().getEmailOtp();

        // First use: verified + session minted.
        postVerify(email, otp).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // The code is consumed. Replaying the SAME code must not mint another
        // session — that would let an intercepted OTP be reused forever.
        postVerify(email, otp)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("Register -> 201 with NO session: OTP stored, account unverified")
    void registerIssuesNoSessionUntilVerified() throws Exception {
        String email = "pending-" + runId() + "@example.com";

        postRegister(registerBody("Pending User", email, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requiresEmailVerification").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                // No credential material of any kind leaves the endpoint.
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getEmailOtp()).matches("\\d{6}");   // real 6-digit code, in the DB
        assertThat(user.getEmailOtpAttempts()).isZero();
        assertThat(user.getEmailOtpCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Login before verification -> 403 EMAIL_NOT_VERIFIED; after the code, 200")
    void loginBlockedUntilVerified() throws Exception {
        String email = "gate-" + runId() + "@example.com";
        postRegister(registerBody("Gate User", email, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());

        postLogin(email, "Str0ngPass!x")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.success").value(false));

        String otp = userRepository.findByEmailIgnoreCase(email).orElseThrow().getEmailOtp();
        postVerify(email, otp).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.emailVerified").value(true))
                .andExpect(jsonPath("$.data.businessSetupCompleted").value(false));

        // Normal flow resumes: the very same credentials now work.
        postLogin(email, "Str0ngPass!x")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.emailVerified").value(true));
    }

    @Test
    @DisplayName("Wrong OTP -> 400, attempt counted; 5th failure burns the code")
    void wrongOtpCountedAndBurned() throws Exception {
        String email = "brute-" + runId() + "@example.com";
        postRegister(registerBody("Brute User", email, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());

        String real = userRepository.findByEmailIgnoreCase(email).orElseThrow().getEmailOtp();
        String wrong = "000000".equals(real) ? "999999" : "000000";
        for (int i = 1; i <= 5; i++) {
            postVerify(email, wrong)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid or expired code"));
        }
        User burnt = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        // Each failure must have been PERSISTED, not rolled back with the throw.
        assertThat(burnt.getEmailOtpAttempts()).isGreaterThanOrEqualTo(5);

        // Budget exhausted: even the CORRECT code is now refused.
        postVerify(email, real)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Too many failed attempts. Please request a new code."));
        assertThat(burnt.isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("Expired OTP -> 400 asking for a new one")
    void expiredOtpRejected() throws Exception {
        String email = "stale-" + runId() + "@example.com";
        postRegister(registerBody("Stale User", email, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        String otp = user.getEmailOtp();
        user.setEmailOtpCreatedAt(java.time.LocalDateTime.now().minusMinutes(30));
        userRepository.save(user);

        postVerify(email, otp)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That code has expired. Please request a new one."));
    }

    @Test
    @DisplayName("Resend rotates the code — the old one stops working")
    void resendRotatesCode() throws Exception {
        String email = "resend-" + runId() + "@example.com";
        postRegister(registerBody("Resend User", email, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());
        User initial = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        String first = initial.getEmailOtp();

        // Age the code past the 15-min per-account resend cooldown, otherwise
        // the resend is (correctly) throttled and would not rotate.
        initial.setEmailOtpCreatedAt(java.time.LocalDateTime.now().minusMinutes(16));
        userRepository.save(initial);

        postResend(email).andExpect(status().isOk());

        User after = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(after.getEmailOtp()).isNotEqualTo(first);
        assertThat(after.getEmailOtpAttempts()).isZero();

        postVerify(email, first).andExpect(status().isBadRequest());       // stale
        postVerify(email, after.getEmailOtp()).andExpect(status().isOk()); // current
    }

    @Test
    @DisplayName("Resend inside the 15-min window is throttled — code is NOT rotated or re-sent")
    void resendThrottledWithinWindow() throws Exception {
        String email = "throttle-" + runId() + "@example.com";
        postRegister(registerBody("Throttle User", email, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());
        User initial = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        String original = initial.getEmailOtp();
        // Code was JUST sent (emailOtpCreatedAt = now) — inside the 15-min window.

        // Response is still the generic success (enumeration-safe), with a wait hint.
        MvcResult res = postResend(email).andExpect(status().isOk()).andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(body).contains("new 6-digit code is on its way");
        // The current code must survive — the owner may be mid-entry on it.
        User after = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(after.getEmailOtp()).isEqualTo(original);
        // And it must still verify: throttling never burns the working code.
        postVerify(email, original).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Resend never reveals whether the address has an account")
    void resendIsEnumerationSafe() throws Exception {
        String known = "known-" + runId() + "@example.com";
        postRegister(registerBody("Known", known, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());

        // Compare the MESSAGE only — the envelope carries a per-request
        // timestamp, so whole-body equality would be a false negative.
        String knownMsg = postResend(known).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        String unknownMsg = postResend("nobody-" + runId() + "@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(messageOf(knownMsg)).isEqualTo(messageOf(unknownMsg));
        assertThat(messageOf(knownMsg)).doesNotContain(known); // never echoes the address back as a verdict
    }

    private String messageOf(String body) throws Exception {
        return objectMapper.readTree(body).path("message").asText();
    }

    @Test
    @DisplayName("A verified account cannot re-mint a session via verify-email — 400, sign-in instead")
    void verifyIsIdempotentOnceVerified() throws Exception {
        String email = "twice-" + runId() + "@example.com";
        postRegister(registerBody("Twice User", email, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());
        String otp = userRepository.findByEmailIgnoreCase(email).orElseThrow().getEmailOtp();

        postVerify(email, otp).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // Second call with the SAME code. This used to re-issue a session with
        // no further proof — i.e. anyone holding just the address could do it
        // from any machine. The code is spent; the account is proven; the only
        // way in now is an ordinary sign-in.
        postVerify(email, otp)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());

        // The account itself is untouched and still fully usable via login.
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().isEmailVerified()).isTrue();
        postLogin(email, "Str0ngPass!x").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Verify for an unknown address -> 400, indistinguishable from a bad code")
    void verifyUnknownEmailRejected() throws Exception {
        postVerify("ghost-" + runId() + "@example.com", "123456")
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ success

    @Test
    @DisplayName("Valid registration -> 201, verification pending, stub business provisioned, USER role")
    void registerSuccess() throws Exception {
        String email = "reg-ok-" + runId() + "@example.com";

        MvcResult result = postRegister(registerBody("Test Owner", email, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requiresEmailVerification").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(600))
                .andReturn();

        // The registration response must never leak credential material.
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("password").doesNotContain("Str0ngPass");

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isBusinessSetupCompleted()).isFalse();
        assertThat(user.getBusinessId()).isNotNull();
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getRawPassword()).isNull(); // public accounts keep NO plaintext copy

        var business = businessRepository.findById(user.getBusinessId()).orElseThrow();
        assertThat(business.getBusinessName()).isEqualTo("Test Owner's Business");
        assertThat(business.getOwnerName()).isEqualTo("Test Owner");
    }

    @Test
    @DisplayName("Username is derived from the email and uniquified on collision")
    void usernameDerivedFromEmail() throws Exception {
        String rid = runId();
        String first = "john.doe." + rid + "@gmail.com";
        String second = "john.doe." + rid + "@yahoo.com";

        postRegister(registerBody("John Doe", first, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());
        postRegister(registerBody("John Doe", second, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());

        User u1 = userRepository.findByEmailIgnoreCase(first).orElseThrow();
        User u2 = userRepository.findByEmailIgnoreCase(second).orElseThrow();
        assertThat(u1.getUsername()).matches("john\\.doe\\." + rid + "(-\\d+)?");
        assertThat(u2.getUsername()).isNotEqualTo(u1.getUsername());
        assertThat(u2.getUsername()).startsWith("john.doe." + rid);
        assertThat(u1.getUsername()).hasSizeBetween(3, 50);
    }

    @Test
    @DisplayName("Overlong email local part still yields a valid 3-50 char username")
    void longLocalPartTruncated() throws Exception {
        String local = "very.long.local.part.for.truncation.check.".repeat(2).substring(0, 60);
        String email = local + "@example.com";

        postRegister(registerBody("Long Name", email, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getUsername()).hasSizeBetween(3, 50);
    }

    @Test
    @DisplayName("Email is normalized (trimmed + lowercased) and login then works in any casing")
    void emailNormalizedAndLoginCaseInsensitive() throws Exception {
        String email = "mixed.case." + runId() + "@example.com";

        postRegister(registerBody("Case Tester", "  " + email.toUpperCase() + "  ", "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(email));

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getEmail()).isEqualTo(email); // stored lowercase, trimmed

        // Login with a differently-cased email must still authenticate — once
        // the (also case-insensitive) verification code has been entered.
        String otp = user.getEmailOtp();
        postVerify(email.toUpperCase(), otp).andExpect(status().isOk());
        postLogin(email.toUpperCase(), "Str0ngPass!x")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    // --------------------------------------------------------------- validation

    @Test
    @DisplayName("Missing required fields -> 400 with per-field errors")
    void missingFieldsRejected() throws Exception {
        postRegister("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.confirmPassword").isNotEmpty());
    }

    @Test
    @DisplayName("Invalid email -> 400 with email field error")
    void invalidEmailRejected() throws Exception {
        postRegister(registerBody("Bad Email", "not-an-email", "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
    }

    @Test
    @DisplayName("Weak password (< 8 chars) -> 400; nothing persisted")
    void weakPasswordRejected() throws Exception {
        String email = "weak-pw-" + runId() + "@example.com";

        postRegister(registerBody("Weak Pw", email, "abc123", "abc123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());

        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    @DisplayName("Password / confirm mismatch -> 400; nothing persisted")
    void passwordMismatchRejected() throws Exception {
        String email = "mismatch-" + runId() + "@example.com";

        postRegister(registerBody("Mismatch", email, "Str0ngPass!x", "Different123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passwords do not match"));

        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    @DisplayName("Malformed JSON body -> 400, no stack trace leaked")
    void malformedJsonRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", freshIp())
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // -------------------------------------------------------------- duplicates

    @Test
    @DisplayName("Re-register over an UNVERIFIED account -> overwrites it (201), no lockout, no extra business")
    void unverifiedAccountCanBeReRegistered() throws Exception {
        String rid = runId();
        String email = "reclaim-" + rid + "@example.com";

        // First sign-up — account is created but never verified.
        postRegister(registerBody("First Try", email, "FirstPass!x1", "FirstPass!x1"))
                .andExpect(status().isCreated());
        User first = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        String firstOtp = first.getEmailOtp();
        Long businessId = first.getBusinessId();

        // Same email again (never verified) with a NEW password -> must OVERWRITE,
        // not reject. This is the lockout fix: an address that never proved inbox
        // ownership has no standing to block a retry.
        postRegister(registerBody("Second Try", email, "SecondPass!x2", "SecondPass!x2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requiresEmailVerification").value(true));

        User second = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(second.getName()).isEqualTo("Second Try");
        assertThat(second.isEmailVerified()).isFalse();
        // Same account row + same business reused (no orphaned stub business).
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getBusinessId()).isEqualTo(businessId);
        // Password actually rotated to the new one.
        assertThat(passwordEncoder.matches("SecondPass!x2", second.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("FirstPass!x1", second.getPassword())).isFalse();
        // OTP rotated: the first email's code is dead, the new one works.
        assertThat(second.getEmailOtp()).isNotEqualTo(firstOtp);

        // Still exactly one account + one business for this address.
        long accounts = userRepository.findAll().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email)).count();
        assertThat(accounts).isEqualTo(1);
        long businesses = businessRepository.findAll().stream()
                .filter(b -> b.getId().equals(businessId)).count();
        assertThat(businesses).isEqualTo(1);

        // The OLD code is dead; the NEW code verifies.
        postVerify(email, firstOtp).andExpect(status().isBadRequest());
        postVerify(email, second.getEmailOtp()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Re-register over a VERIFIED account -> 409, original untouched")
    void verifiedAccountIsProtectedAsDuplicate() throws Exception {
        String rid = runId();
        String email = "locked-" + rid + "@example.com";

        String token = registerVerified("Verified Owner", email);
        assertThat(token).isNotBlank();
        User verified = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        // Verified = proved inbox ownership = genuine duplicate now.
        postRegister(registerBody("Impostor", email, "SomeOtherPass!1", "SomeOtherPass!1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        // The verified account is completely untouched.
        User after = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(after.getName()).isEqualTo("Verified Owner");
        assertThat(after.isEmailVerified()).isTrue();
        assertThat(after.getPassword()).isEqualTo(verified.getPassword());
    }

    @Test
    @DisplayName("Duplicate email (any casing) -> 409, no second account, no second business")
    void duplicateEmailRejected() throws Exception {
        String rid = runId();
        String email = "dup-" + rid + "@example.com";

        // Verify the FIRST account so it is a genuine (protected) duplicate below.
        registerVerified("Dupe One", email);

        // Same email, different case + surrounding whitespace = same identity.
        postRegister(registerBody("Dupe Two", "  " + email.toUpperCase() + "  ", "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        long accounts = userRepository.findAll().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email)).count();
        assertThat(accounts).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrent duplicate registration -> exactly one account, loser rolls back entirely")
    void concurrentDuplicateRegistration() throws Exception {
        String rid = runId();
        String email = "race-" + rid + "@example.com";
        String body = registerBody("Race Tester", email, "Str0ngPass!x", "Str0ngPass!x");

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger created = new AtomicInteger(0);
        AtomicInteger conflicted = new AtomicInteger(0);
        AtomicInteger unexpected = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            // Same body: the two attempts genuinely race for the same email.
            String ip = freshIp();
            pool.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    int status = mockMvc.perform(post("/api/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .header("X-Forwarded-For", ip)
                                    .content(body))
                            .andReturn().getResponse().getStatus();
                    if (status == 201) {
                        created.incrementAndGet();
                    } else if (status == 409) {
                        conflicted.incrementAndGet();
                    } else {
                        unexpected.incrementAndGet();
                    }
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(created.get()).as("exactly one 201").isEqualTo(1);
        assertThat(conflicted.get()).as("the other attempt must be 409").isEqualTo(1);
        assertThat(unexpected.get()).as("no other status allowed").isZero();

        long accounts = userRepository.findAll().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email)).count();
        assertThat(accounts).isEqualTo(1);
        long businesses = businessRepository.findAll().stream()
                .filter(b -> "Race Tester's Business".equals(b.getBusinessName()))
                .count();
        assertThat(businesses).as("loser's stub business must roll back").isEqualTo(1);
    }

    // ------------------------------------------------------- security properties

    @Test
    @DisplayName("Privileged fields in the payload are ignored — public request can never self-promote")
    void privilegedFieldsIgnored() throws Exception {
        String rid = runId();
        String email = "inject-" + rid + "@example.com";
        String body = "{"
                + "\"name\":\"Sneaky Actor\","
                + "\"email\":\"" + email + "\","
                + "\"password\":\"Str0ngPass!x\","
                + "\"confirmPassword\":\"Str0ngPass!x\","
                + "\"role\":\"ADMIN\","
                + "\"username\":\"hacker\","
                + "\"businessId\":99999,"
                + "\"businessSetupCompleted\":true,"
                + "\"mustChangePassword\":false,"
                + "\"rawPassword\":\"leak\","
                + "\"tokenVersion\":99,"
                + "\"id\":1"
                + "}";

        postRegister(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requiresEmailVerification").value(true));

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isBusinessSetupCompleted()).isFalse();
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getRawPassword()).isNull();
        assertThat(user.getUsername()).isNotEqualTo("hacker");
        assertThat(user.getBusinessId()).isNotEqualTo(99999L);
        assertThat(user.getTokenVersion()).isZero();
        assertThat(user.isEmailVerified()).isFalse(); // nor can it skip verification
    }

    @Test
    @DisplayName("Password is BCrypt-hashed, never stored or echoed in plaintext")
    void passwordHashed() throws Exception {
        String email = "hash-" + runId() + "@example.com";
        String raw = "Str0ngPass!x";

        postRegister(registerBody("Hash Tester", email, raw, raw))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getPassword()).isNotEqualTo(raw);
        assertThat(user.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches(raw, user.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("wrong", user.getPassword())).isFalse();
    }

    @Test
    @DisplayName("Protected APIs stay protected: anon 401, registered USER 403 on admin")
    void protectedEndpointsRemainProtected() throws Exception {
        String email = "guard-" + runId() + "@example.com";
        String token = registerVerified("Guard Tester", email);
        assertThat(token).isNotBlank();

        // Anonymous -> 401 on business APIs.
        mockMvc.perform(get("/api/business/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized());

        // Authenticated non-admin -> 403 on admin surface.
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // ... but the registered user's own tenant endpoint works.
        mockMvc.perform(get("/api/business/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tenant isolation: a registered user cannot read another tenant's data")
    void tenantIsolationHolds() throws Exception {
        String rid = runId();
        String tokenA = registerVerified("Tenant A", "tenant-a-" + rid + "@example.com");
        // B never verifies — nothing about B's account should matter to A.
        postRegister(registerBody("Tenant B", "tenant-b-" + rid + "@example.com", "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());

        User userB = userRepository.findByEmailIgnoreCase("tenant-b-" + rid + "@example.com").orElseThrow();
        Invoice invoiceB = seedInvoice(userB);

        // B's invoice is invisible to A (404, not even detectable).
        mockMvc.perform(get("/api/invoices/" + invoiceB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // A resolves to A's own business, never B's.
        Long businessA = userRepository
                .findByEmailIgnoreCase("tenant-a-" + rid + "@example.com").orElseThrow().getBusinessId();
        assertThat(businessA).isNotEqualTo(userB.getBusinessId());
        mockMvc.perform(get("/api/business/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(businessA));
    }

    // ------------------------------------------- existing accounts stay intact

    @Test
    @DisplayName("Existing seeded USER still logs in with the existing password")
    void existingUserStillLogsIn() throws Exception {
        // V25 seeds rshardware / RSHARDWARE into every fresh schema, incl. tests.
        postLogin("rshardware2210@gmail.com", "RSHARDWARE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("Existing seeded ADMIN still logs in with unchanged credentials and role")
    void existingAdminStillLogsIn() throws Exception {
        postLogin("insideinvoice", "invoice6688inside")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("A newly registered user signs in only after the code, then the normal flow resumes")
    void registeredUserCanLogIn() throws Exception {
        String email = "journey-" + runId() + "@example.com";
        postRegister(registerBody("Journey User", email, "Str0ngPass!x", "Str0ngPass!x"))
                .andExpect(status().isCreated());

        // Blocked while unverified — and the refusal is a 403 with a code the
        // SPA can branch on, never a 401 (which would trigger a forced logout).
        postLogin(email, "Str0ngPass!x")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        String otp = userRepository.findByEmailIgnoreCase(email).orElseThrow().getEmailOtp();
        postVerify(email, otp).andExpect(status().isOk());

        postLogin(email, "Str0ngPass!x")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.email").value(email));

        // Wrong password still fails the normal way.
        postLogin(email, "wrong-password")
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Existing login contract untouched: wrong password on seeded account -> 401")
    void existingLoginRejectsBadPassword() throws Exception {
        postLogin("rshardware2210@gmail.com", "definitely-wrong")
                .andExpect(status().isUnauthorized());
    }
}
