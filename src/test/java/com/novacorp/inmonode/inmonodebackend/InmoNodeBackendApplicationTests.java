package com.novacorp.inmonode.inmonodebackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the whole application against a real PostgreSQL: validates the Flyway migrations and the JPA mapping.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class InmoNodeBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
