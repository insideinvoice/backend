package com.insideinvoice.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Current-user profile for GET /api/auth/me. Mirrors {@link JwtResponse}
 * minus the token fields, so the client can merge it into its stored user
 * snapshot without ever clobbering the session token with a null.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileResponse {

    private Long userId;
    private String name;
    private String username;
    private String email;
    private String role;
    private Long businessId;
    private boolean businessSetupCompleted;
}
