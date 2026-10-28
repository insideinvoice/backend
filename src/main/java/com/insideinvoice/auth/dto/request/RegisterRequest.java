package com.insideinvoice.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Public self-service registration contract (POST /api/auth/register).
 *
 * <p>Deliberately minimal: only what an anonymous visitor can legitimately
 * supply. Unlike {@link SignupRequest} there is NO username (derived from the
 * email server-side), NO businessName (the stub business is provisioned
 * internally) and NO role field at all — a public request can never influence
 * account privileges. Password policy matches the self-service reset flow
 * (8-100), stronger than the admin-driven signup minimum.</p>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;

    /**
     * Canonical form applied at binding time (Jackson populates the DTO BEFORE
     * bean validation runs): trimmed + lowercased, so a pasted
     * " John@Example.COM " validates and dedupes against "john@example.com".
     * The service normalizes again as defense in depth.
     */
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
