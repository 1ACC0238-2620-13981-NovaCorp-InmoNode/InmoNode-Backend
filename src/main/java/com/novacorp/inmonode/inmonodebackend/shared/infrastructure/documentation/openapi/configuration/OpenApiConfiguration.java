package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.documentation.openapi.configuration;

import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.documentation.openapi.annotations.OptionalAuthentication;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures the OpenAPI specification exposed by the platform.
 */
@Configuration
public class OpenApiConfiguration {
    /** Security scheme every protected operation requires unless it declares otherwise. */
    public static final String BEARER_AUTH = "bearerAuth";

    // Properties
    @Value("${spring.application.name}")
    String applicationName;

    @Value("${documentation.application.description}")
    String applicationDescription;

    @Value("${documentation.application.version}")
    String applicationVersion;

    // Methods

    /**
     * Builds the OpenAPI document used by Swagger UI and client generation tools.
     *
     * @return configured OpenAPI descriptor
     */
    @Bean
    public OpenAPI inmoNodePlatformOpenApi() {

        // General configuration
        var openApi = new OpenAPI();
        openApi
                .info(new Info()
                        .title(this.applicationName)
                        .description(this.applicationDescription)
                        .version(this.applicationVersion)
                        .contact(new Contact()
                                .name("NovaCorp - InmoNode")
                                .url("https://github.com/1ACC0238-2620-13981-NovaCorp-InmoNode")))
                .externalDocs(new ExternalDocumentation()
                        .description("InmoNode Project Report")
                        .url("https://github.com/1ACC0238-2620-13981-NovaCorp-InmoNode/InmoNode-Project-Report"));

        // Use a relative server URL so Swagger UI always targets the same origin
        // it is being served from (localhost, Docker or cloud), avoiding CORS issues.
        openApi.servers(List.of(
                new Server()
                        .url("/")
                        .description("Current environment")
        ));

        // Add a security scheme
        openApi.addSecurityItem(new SecurityRequirement()
                        .addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH,
                                new SecurityScheme()
                                        .name(BEARER_AUTH)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Bearer token for API authentication")));

        return openApi;
    }

    /**
     * Lists the operations marked {@link OptionalAuthentication} as callable without security or with
     * {@link #BEARER_AUTH}, instead of inheriting the global requirement.
     *
     * @return customizer applied to every documented operation
     */
    @Bean
    public OperationCustomizer optionalAuthenticationCustomizer() {
        return (operation, handlerMethod) -> {
            if (handlerMethod.hasMethodAnnotation(OptionalAuthentication.class)) {
                operation.setSecurity(List.of(new SecurityRequirement(), new SecurityRequirement().addList(BEARER_AUTH)));
            }
            return operation;
        };
    }
}