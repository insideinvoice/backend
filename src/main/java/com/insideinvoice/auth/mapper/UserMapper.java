package com.insideinvoice.auth.mapper;

import com.insideinvoice.auth.dto.response.JwtResponse;
import com.insideinvoice.auth.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public JwtResponse toJwtResponse(User user, String accessToken) {
        return JwtResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .name(user.getName())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .businessId(user.getBusinessId())
                .businessSetupCompleted(user.isBusinessSetupCompleted())
                .build();
    }
}
