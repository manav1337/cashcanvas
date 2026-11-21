package com.cashcanvas.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Inject Clock instead of calling Instant.now() so tests can freeze time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
