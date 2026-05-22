package com.insideinvoice.auth.dto.response;

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
public class JwtResponse {

    private String accessToken;
    private String tokenType;
    private Long userId;
    private String name;
    private String username;
    private String email;
    private String role;
    private Long businessId;
    private boolean businessSetupCompleted;

    public JwtResponse(String accessToken, Long userId, String name, String username, String email,
                       String role, Long businessId, boolean businessSetupCompleted) {
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
        this.userId = userId;
        this.name = name;
        this.username = username;
        this.email = email;
        this.role = role;
        this.businessId = businessId;
        this.businessSetupCompleted = businessSetupCompleted;
    }
}
