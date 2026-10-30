package com.quantumlock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuantumLockApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuantumLockApplication.class, args);
    }
}
