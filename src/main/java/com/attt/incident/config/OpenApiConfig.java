package com.attt.incident.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration.
 *
 * <p>Registers a Bearer JWT security scheme so that the Swagger UI
 * "Authorize" dialog accepts the raw token (without the "Bearer " prefix).
 * springdoc-openapi prepends "Bearer " automatically based on the scheme type.
 *
 * <h3>Usage in Swagger UI</h3>
 * <ol>
 *   <li>POST {@code /api/auth/login} to obtain an access token.</li>
 *   <li>Click <strong>Authorize 🔓</strong>.</li>
 *   <li>In the "bearerAuth" field, enter <strong>only the JWT token</strong>
 *       (without the "Bearer " prefix — the UI adds it automatically).</li>
 *   <li>Call any protected endpoint.</li>
 * </ol>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ATTT Incident Management API")
                        .description("Hệ thống tiếp nhận và xử lý sự cố an toàn thông tin")
                        .version("0.1.0"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(BEARER_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập JWT token (không cần tiền tố 'Bearer '). " +
                                                     "Lấy token từ POST /api/auth/login")));
    }
}
