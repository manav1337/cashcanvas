package com.cashcanvas.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;

/**
 * Swagger UI at /swagger-ui.html. The "Authorize" button accepts the JWT from /auth/login.
 * Bearer auth is required globally; public controllers opt out with an empty @SecurityRequirements.
 */
@Configuration
@SecurityScheme(
        name = OpenApiConfig.BEARER_SCHEME,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI cashCanvasOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CashCanvas API")
                        .version("v1")
                        .description("Upload Indian bank statements (CSV/XLS), get categorised transactions "
                                + "and monthly spending insights. Errors use RFC 7807 problem+json with a stable 'code'."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
