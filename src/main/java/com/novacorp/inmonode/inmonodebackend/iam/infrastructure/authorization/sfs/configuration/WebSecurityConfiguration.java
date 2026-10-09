package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.configuration;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.pipeline.JwtAuthenticationFilter;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.pipeline.SecurityErrorHandlers;
import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.ratelimiting.ApiRateLimitFilter;
import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.ratelimiting.IpRateLimiter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.auditing.FinancialAuditFilter;
import org.springframework.web.filter.CorsFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

/**
 * Stateless JWT security for the whole modular monolith. Business modules restrict endpoints by role
 * with {@code @PreAuthorize("hasRole('BUYER')")} and similar annotations.
 *
 * <p>CORS admits only the web portal origins (US-38), so browser preflights are answered before
 * authentication runs.</p>
 */
@Configuration
@EnableMethodSecurity
public class WebSecurityConfiguration {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/**",
            "/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    /**
     * Read-only endpoints open without a session: the published catalog (US-15) and load-balancer health (US-34).
     * Writes on the same paths stay protected by {@code @PreAuthorize}.
     */
    private static final String[] PUBLIC_READ_ENDPOINTS = {
            "/health",
            "/api/v1/projects",
            "/api/v1/projects/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokenService,
                                                    ApiRateLimitFilter apiRateLimitFilter, @Value("${audit.enabled:true}") boolean auditEnabled) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(SecurityErrorHandlers.unauthorized())
                        .accessDeniedHandler(SecurityErrorHandlers.forbidden()))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_READ_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenService), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiRateLimitFilter, CorsFilter.class)
                .addFilterAfter(new FinancialAuditFilter(auditEnabled), JwtAuthenticationFilter.class)
                .build();
    }

    @Bean
    public ApiRateLimitFilter apiRateLimitFilter(
            @Value("${api.rate-limit.enabled:true}") boolean enabled,
            @Value("${api.rate-limit.catalog-per-minute:150}") int catalogLimit,
            @Value("${api.rate-limit.pdf-per-minute:10}") int pdfLimit,
            @Value("${api.rate-limit.general-per-minute:60}") int apiLimit,
            @Value("${api.rate-limit.max-tracked-ips:10000}") int maxTrackedIps) {
        return new ApiRateLimitFilter(new IpRateLimiter(Clock.systemUTC(), catalogLimit, pdfLimit, apiLimit,
                maxTrackedIps), enabled);
    }

    /** Register only in Spring Security, after CORS; avoid a second invocation as a container filter. */
    @Bean
    public FilterRegistrationBean<ApiRateLimitFilter> rateLimitFilterRegistration(ApiRateLimitFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * US-38: preflights from the configured portal origins are allowed; any other origin is rejected.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${authorization.cors.allowed-origins}") String[] allowedOrigins) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins)
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("Total-Count", "Retry-After"));
        configuration.setMaxAge(Duration.ofHours(1));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
