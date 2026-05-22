package com.insideinvoice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

@OpenAPIDefinition(
        info = @Info(
                title = "Inside Invoice API",
                description = "REST API for Inside Invoice - SaaS Multi-Tenant Invoicing Platform",
                version = "1.0.0",
                contact = @Contact(
                        name = "Inside Invoice",
                        email = "support@insideinvoice.com"
                ),
                license = @License(
                        name = "Private License"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local Development"),
                @Server(url = "${app.base-url}", description = "Production")
        },
        security = {
                @SecurityRequirement(name = "bearerAuth")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        description = "JWT authentication",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}
