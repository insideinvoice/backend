package com.insideinvoice.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWithPasswordResponse {

    private Long id;
    private String name;
    private String username;
    private String email;
    private String role;
    private String password;
    private Long businessId;
    private String createdAt;
    private String rawPassword;
    /** Timestamp of the user's most recent invoice (null = never generated one). */
    private String lastActivityAt;
    /** True when the user generated at least one invoice in the last 30 days. */
    private Boolean active;
}
