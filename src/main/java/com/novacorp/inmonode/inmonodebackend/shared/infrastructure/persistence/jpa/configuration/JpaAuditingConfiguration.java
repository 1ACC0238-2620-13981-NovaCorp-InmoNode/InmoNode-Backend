package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.persistence.jpa.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing for {@code createdAt} and {@code updatedAt} fields.
 *
 * <p>Kept outside the application class so that test slices such as
 * {@code @WebMvcTest} do not require a JPA metamodel.</p>
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {
}
