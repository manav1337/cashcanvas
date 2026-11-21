package com.cashcanvas;

import org.springframework.boot.SpringApplication;

import com.cashcanvas.support.TestcontainersConfiguration;

/**
 * Optional: run this main() from your IDE to start the app against throwaway MySQL + Redis
 * containers, without docker-compose. Needs Docker running. Data is lost when you stop it.
 */
public class TestCashCanvasApplication {

    public static void main(String[] args) {
        SpringApplication.from(CashCanvasApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
