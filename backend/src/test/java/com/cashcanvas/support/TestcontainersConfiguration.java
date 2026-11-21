package com.cashcanvas.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real MySQL and Redis in throwaway Docker containers.
 *
 * @ServiceConnection tells Spring Boot to read host/port/credentials from the running container
 * and wire the DataSource / Redis connection to it, so no URLs are configured anywhere.
 * Because the containers are Spring beans, they start once per Spring context and are shared by
 * every test class that uses the same context (see AbstractIntegrationTest), which keeps the suite fast.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    }
}
