package com.novacorp.inmonode.inmonodebackend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Throwaway S3-compatible storage (RustFS, the same image as docker-compose) for the tests that upload files.
 * It is a real S3 server: it checks request signatures, as AWS S3 does. Import it next to
 * {@link TestcontainersConfiguration}; the bucket is created on startup.
 */
@TestConfiguration(proxyBeanMethods = false)
public class S3TestcontainersConfiguration {

    static final String ACCESS_KEY = "test-access";
    static final String SECRET_KEY = "test-secret-key";
    private static final int API_PORT = 9000;

    @Bean
    GenericContainer<?> s3Container() {
        return new GenericContainer<>("rustfs/rustfs:1.0.0")
                .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
                .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
                .withExposedPorts(API_PORT)
                .waitingFor(Wait.forHttp("/health").forPort(API_PORT));
    }

    @Bean
    DynamicPropertyRegistrar s3Properties(GenericContainer<?> s3Container) {
        return registry -> {
            var endpoint = "http://%s:%d".formatted(s3Container.getHost(), s3Container.getMappedPort(API_PORT));
            registry.add("storage.s3.endpoint", () -> endpoint);
            registry.add("storage.s3.public-endpoint", () -> endpoint);
            registry.add("storage.s3.access-key", () -> ACCESS_KEY);
            registry.add("storage.s3.secret-key", () -> SECRET_KEY);
            registry.add("storage.s3.path-style-access", () -> "true");
            registry.add("storage.s3.create-bucket", () -> "true");
        };
    }
}
