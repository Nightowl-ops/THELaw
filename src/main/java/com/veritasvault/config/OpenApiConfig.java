package com.veritasvault.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 / Swagger documentation configuration.
 * Configures global JWT Bearer authentication scheme across the interactive Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Bean
    public OpenAPI veritasVaultOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VeritasVault REST API")
                        .description("Digital Evidence Vault & Legal Chain-of-Custody Management System API Documentation")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("VeritasVault Engineering")
                                .email("support@veritasvault.com"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")))
                // Applies JWT authorization globally across all endpoints in the UI
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter your Bearer JWT token obtained from POST /api/auth/login")));
    }
}