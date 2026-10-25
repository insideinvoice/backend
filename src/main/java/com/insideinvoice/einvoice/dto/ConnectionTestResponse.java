package com.insideinvoice.einvoice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConnectionTestResponse {
    private String connectionStatus;
    private String message;
    private boolean mock;
    private LocalDateTime testedAt;
}
