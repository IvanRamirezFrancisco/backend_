package com.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailConfigDebug {

    @Value("${spring.mail.host}")
    private String mailHost;

    @Value("${spring.mail.port}")
    private String mailPort;

    @Value("${spring.mail.username}")
    private String mailUsername;

    @Bean
    public ApplicationRunner debugMailConfig() {
        return args -> {
            System.out.println("=================================================");
            System.out.println("🔧 DEBUG: MAIL CONFIGURATION AT STARTUP");
            System.out.println("=================================================");
            System.out.println("📧 MAIL_HOST: " + mailHost);
            System.out.println("📧 MAIL_PORT: " + mailPort);
            System.out.println("📧 MAIL_USERNAME: " + mailUsername);
            System.out.println("📧 Expected Brevo Host: smtp-relay.brevo.com");
            System.out.println("📧 Expected Brevo Port: 587");
            System.out.println("📧 Expected Brevo Username: 9a5a33001@smtp-brevo.com");
            System.out.println("=================================================");
        };
    }
}