package me.cleanbrain.relayhub.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal OpenAPI metadata — springdoc-openapi generates the actual paths/schemas from the
 * existing {@code @RestController}/record-DTO surface at runtime, so this bean only supplies what
 * it can't infer: a title/description, and the one real auth scheme ({@code /api/**} writes are
 * HTTP Basic — see SecurityConfig). Browsable at {@code /swagger-ui.html}, raw spec at
 * {@code /v3/api-docs}. See docs/architecture/api-contract.md.
 */
@Configuration
public class OpenApiConfig {

    private static final String BASIC_AUTH_SCHEME = "basicAuth";

    @Bean
    public OpenAPI relayHubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RelayHub API")
                        .description("Event-driven data integration platform: receives data-change events from "
                                + "Source systems, standardizes them internally, and delivers them to Target "
                                + "systems in each Target's own API contract. GET endpoints and /ingress/v1/** "
                                + "are public; every /api/** write requires HTTP Basic (role ADMIN) unless noted "
                                + "otherwise on the operation.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH_SCHEME))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes(BASIC_AUTH_SCHEME,
                                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")));
    }
}
