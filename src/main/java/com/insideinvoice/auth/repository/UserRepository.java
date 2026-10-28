package com.insideinvoice.auth.repository;

import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * Admin user list: search on name/email/username plus the "Active" toggle.
     * {@code activeOnly} narrows to users who generated an invoice since
     * {@code cutoff} via an EXISTS subquery that rides
     * {@code idx_invoices_created_by_created_at} (V35) instead of loading every
     * invoice to filter client-side. {@code q = ''} matches everyone.
     */
    @Query("SELECT u FROM User u WHERE (:q = '' "
            + "OR LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "AND (:activeOnly = false "
            + "OR EXISTS (SELECT i.id FROM Invoice i WHERE i.createdBy = u.id AND i.createdAt >= :cutoff))")
    Page<User> search(@Param("q") String q, @Param("activeOnly") boolean activeOnly,
            @Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.email = :login OR u.username = :login")
    Optional<User> findByEmailOrUsername(@Param("login") String login);

    /**
     * Case-insensitive login fallback — consulted only when the exact-match
     * query above misses, so existing behavior is unchanged. Backed by the V39
     * LOWER() unique indexes, which guarantee a single match can exist. Needed
     * because public registration normalizes emails to lowercase while legacy
     * accounts keep their as-submitted casing.
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:login) OR LOWER(u.username) = LOWER(:login)")
    Optional<User> findByEmailOrUsernameIgnoreCase(@Param("login") String login);

    Optional<User> findByIdAndBusinessId(Long id, Long businessId);

    Optional<User> findByResetPasswordToken(String resetPasswordToken);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /**
     * Case-insensitive probes backing Instagram-style uniqueness: "John" and
     * "john" are the same handle, A@b.com and a@b.com the same account. The
     * lower() unique indexes from V39 enforce the same rule at the DB level.
     */
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    /** Case-insensitive email lookup (registration/normalization tests, admin tooling). */
    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findTop10ByOrderByCreatedAtDesc();

    /** Monthly platform signup counts since cutoff (row 0 = month timestamp). Admin new-users chart. */
    @org.springframework.data.jpa.repository.Query(value = "SELECT DATE_TRUNC('month', created_at) AS m, COUNT(*) "
            + "FROM users WHERE created_at >= :cutoff GROUP BY 1 ORDER BY 1", nativeQuery = true)
    List<Object[]> monthlySignupCount(@Param("cutoff") OffsetDateTime cutoff);

    /**
     * Primary user record of a business (lowest id first is the account that
     * created/owns it). Used by the admin workspace to resolve "on behalf of"
     * ownership when the admin creates data inside a user's business.
     */
    List<User> findByBusinessIdOrderByIdAsc(Long businessId);

    long countByRole(Role role);

    long countByBusinessId(Long businessId);

    @Modifying
    @Query("UPDATE User u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.id = :id")
    int incrementTokenVersion(@Param("id") Long id);

    /**
     * Atomic bump of the sign-up OTP attempt counter, in its OWN transaction so
     * it survives the failure the caller is about to throw. Doing this as a
     * plain save() on a @Transactional verify method would roll the increment
     * back with the exception and leave the 6-digit code brute-forceable.
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.emailOtpAttempts = COALESCE(u.emailOtpAttempts, 0) + 1 WHERE u.id = :id")
    int incrementEmailOtpAttempts(@Param("id") Long id);

    /**
     * Atomically flips an account to verified, but ONLY while the exact code we
     * just validated is still the live one. A concurrent resend or a
     * re-registration rotates emailOtp; if that happened between our read and
     * this write, 0 rows match and the caller must treat the code as stale.
     * This is the commit step of verify-email: the constant-time comparison is
     * the check, this conditional UPDATE is the race-proof act.
     *
     * <p>{@code clearAutomatically = true} is not optional here: under
     * OpenEntityManagerInView the request-scoped persistence context still
     * caches the pre-update entity, so the caller's re-read (which builds the
     * JWT) would otherwise mint a session from the stale {@code emailVerified =
     * false} row. The clear forces the re-read to hit the database.</p>
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE User u SET u.emailVerified = true, u.emailOtp = null, "
            + "u.emailOtpAttempts = 0, u.emailOtpCreatedAt = null "
            + "WHERE u.id = :id AND u.emailOtp = :otp AND u.emailVerified = false")
    int markEmailVerifiedIfOtpMatches(@Param("id") Long id, @Param("otp") String otp);

    /**
     * Clears a sign-up code ONLY if it is still the code we read (expired or
     * attempt-capped). Full-entity save() here would write back every stale
     * column — including a password a concurrent re-registration just changed.
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.emailOtp = null, u.emailOtpAttempts = 0, u.emailOtpCreatedAt = null "
            + "WHERE u.id = :id AND u.emailOtp = :otp")
    int clearEmailOtpIfMatches(@Param("id") Long id, @Param("otp") String otp);

    /**
     * Atomic bump of the password-reset OTP attempt counter, gated on the code
     * still being live (a resend rotates it; a stale failure must not count
     * against the owner's fresh code).
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.otpAttempts = COALESCE(u.otpAttempts, 0) + 1 "
            + "WHERE u.id = :id AND u.tmpOtp = :otp")
    int incrementTmpOtpAttempts(@Param("id") Long id, @Param("otp") String otp);

    /** Atomic reset of the reset-OTP attempt counter on a successful check. */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.otpAttempts = 0 WHERE u.id = :id AND u.tmpOtp = :otp")
    int resetTmpOtpAttempts(@Param("id") Long id, @Param("otp") String otp);

    /**
     * Clears a password-reset code ONLY if it is still the live one — same
     * stale-write protection as clearEmailOtpIfMatches.
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.tmpOtp = null, u.otpAttempts = 0, u.otpCreatedAt = null "
            + "WHERE u.id = :id AND u.tmpOtp = :otp")
    int clearTmpOtpIfMatches(@Param("id") Long id, @Param("otp") String otp);

    /**
     * Consumes the password-reset OTP and sets the new hash in ONE atomic step,
     * gated on the code we validated still being live. Also bumps tokenVersion:
     * a password reset must sign out every existing session (a reset is often
     * the response to a stolen token). The conditional WHERE means a code
     * rotated mid-flight matches 0 rows instead of silently applying a password
     * against a stale read.
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.password = :password, u.rawPassword = :rawPassword, "
            + "u.tmpOtp = null, u.otpAttempts = 0, u.otpCreatedAt = null, "
            + "u.mustChangePassword = false, u.resetPasswordToken = null, "
            + "u.tokenVersion = u.tokenVersion + 1 "
            + "WHERE u.id = :id AND u.tmpOtp = :otp")
    int resetPasswordIfOtpMatches(@Param("id") Long id, @Param("otp") String otp,
                                  @Param("password") String password,
                                  @Param("rawPassword") String rawPassword);

    /**
     * Overwrites an UNVERIFIED self-service account in place when the same email
     * is re-registered (fresh password, fresh OTP). Guarded by
     * {@code WHERE emailVerified = false} so it is race-safe without optimistic
     * locking: if the account is verified between the caller's read and this
     * write, the update matches 0 rows and the caller treats it as a genuine
     * duplicate. Reuses the row (and its business) rather than inserting, so an
     * abandoned sign-up never locks the address out or piles up stub businesses.
     *
     * <p>{@code clearAutomatically = true} so the caller's immediate re-read
     * (inside the same OpenEntityManagerInView request) cannot hand back the
     * pre-overwrite entity still sitting in the persistence context.</p>
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE User u SET u.name = :name, u.password = :password, "
            + "u.emailOtp = :otp, u.emailOtpAttempts = 0, u.emailOtpCreatedAt = :now, "
            + "u.emailVerified = false, u.mustChangePassword = false, "
            + "u.businessSetupCompleted = false, u.tokenVersion = u.tokenVersion + 1 "
            + "WHERE u.id = :id AND u.emailVerified = false")
    int overwriteUnverifiedUser(@Param("id") Long id, @Param("name") String name,
                                @Param("password") String password, @Param("otp") String otp,
                                @Param("now") LocalDateTime now);
}
