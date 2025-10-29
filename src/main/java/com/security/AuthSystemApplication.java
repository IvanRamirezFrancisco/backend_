package com.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AuthSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthSystemApplication.class, args);
    }
}
