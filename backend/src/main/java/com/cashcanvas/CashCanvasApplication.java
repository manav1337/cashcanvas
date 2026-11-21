package com.cashcanvas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Login is handled by our own AuthService + JWT, so Spring Boot's default
 * in-memory user (and its "generated security password" log line) is switched off.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class CashCanvasApplication {

    public static void main(String[] args) {
        SpringApplication.run(CashCanvasApplication.class, args);
    }
}
