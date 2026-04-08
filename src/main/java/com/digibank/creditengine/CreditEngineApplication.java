package com.digibank.creditengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Credit Engine microservice.
 *
 * This is a stateless REST API — no database, no session state.
 * All inputs arrive via request body, all outputs are calculated on the fly.
 */
@SpringBootApplication
public class CreditEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditEngineApplication.class, args);
    }
}
