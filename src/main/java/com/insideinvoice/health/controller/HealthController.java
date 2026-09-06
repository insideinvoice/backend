package com.insideinvoice.health.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Health", description = "Health check endpoints")
public class HealthController {

    @GetMapping("/heartbeat")
    @Operation(summary = "Health check - no auth required")
    public ResponseEntity<Map<String, String>> heartbeat() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
