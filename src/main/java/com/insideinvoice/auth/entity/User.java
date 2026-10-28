package com.insideinvoice.auth.entity;

import com.insideinvoice.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "username")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "business_setup_completed", nullable = false)
    private boolean businessSetupCompleted;

    @Column(name = "reset_password_token")
    private String resetPasswordToken;

    @Column(name = "raw_password")
    private String rawPassword;

    @Column(name = "must_change_password")
    private boolean mustChangePassword;

    @Column(name = "tmp_otp")
    private String tmpOtp;

    @Column(name = "otp_attempts")
    private Integer otpAttempts;

    @Column(name = "otp_created_at")
    private java.time.LocalDateTime otpCreatedAt;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    /**
     * Email ownership proof for self-service sign-ups. False only on accounts
     * created by {@code POST /api/auth/register} that have not yet entered the
     * OTP mailed to their inbox — login refuses those. Pre-existing accounts
     * are backfilled to true by V53.
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    // Own OTP columns (NOT tmp_otp): forgot-password must keep working while an
    // account is mid-verification, so the flows cannot share a code or an
    // attempt budget.

    @Column(name = "email_otp")
    private String emailOtp;

    @Column(name = "email_otp_attempts")
    private Integer emailOtpAttempts;

    @Column(name = "email_otp_created_at")
    private java.time.LocalDateTime emailOtpCreatedAt;
}
