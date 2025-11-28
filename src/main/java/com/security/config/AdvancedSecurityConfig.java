package com.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Configuración avanzada de seguridad con cabeceras HTTP, HTTPS y protección
 * CSRF
 * DESHABILITADO TEMPORALMENTE - Railway maneja SSL automáticamente
 * Para reactivar, cambiar el profile a @Profile("production-manual")
 */
@Configuration
@EnableWebSecurity
@Profile("production-manual-disabled")  // Deshabilitado - causa conflictos con Railway
public class AdvancedSecurityConfig implements WebMvcConfigurer {

        @Value("${app.security.https.force:false}")
        private boolean forceHttps;

        @Value("${app.security.csp.policy:default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' https:; connect-src 'self' https:; frame-ancestors 'none';}")
        private String cspPolicy;

        @Value("${app.cors.allowed-origins:http://localhost:4200}")
        private String[] allowedOrigins;

        @Bean
        public SecurityFilterChain advancedSecurityFilterChain(HttpSecurity http) throws Exception {
                return http
                                // HTTPS Configuration
                                .requiresChannel(channel -> {
                                        if (forceHttps) {
                                                channel.requestMatchers(r -> true).requiresSecure();
                                        }
                                })

                                // Security Headers (usando sintaxis actualizada de Spring Security 6.x)
                                .headers(headers -> headers
                                                // Content Security Policy
                                                .contentSecurityPolicy(csp -> csp
                                                                .policyDirectives(cspPolicy))

                                                // X-Frame-Options
                                                .frameOptions(frame -> frame.deny())

                                                // X-Content-Type-Options
                                                .contentTypeOptions(content -> {
                                                })

                                                // HTTP Strict Transport Security
                                                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                                                                .maxAgeInSeconds(31536000) // 1 año
                                                                .includeSubDomains(true)
                                                                .preload(true))

                                                // Referrer Policy
                                                .referrerPolicy(referrer -> referrer
                                                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))

                                                // Cabeceras adicionales de seguridad
                                                .addHeaderWriter((request, response) -> {
                                                        response.setHeader("X-XSS-Protection", "1; mode=block");
                                                        response.setHeader("Permissions-Policy",
                                                                        "camera=(), microphone=(), geolocation=(), payment=()");
                                                        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
                                                        response.setHeader("Cross-Origin-Embedder-Policy",
                                                                        "require-corp");
                                                        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
                                                        response.setHeader("Cross-Origin-Resource-Policy",
                                                                        "cross-origin");
                                                }))

                                // CSRF Protection (habilitada para producción)
                                .csrf(csrf -> csrf
                                                .csrfTokenRepository(
                                                                org.springframework.security.web.csrf.CookieCsrfTokenRepository
                                                                                .withHttpOnlyFalse())
                                                .ignoringRequestMatchers(
                                                                "/api/auth/login",
                                                                "/api/auth/register",
                                                                "/api/auth/password-reset/**",
                                                                "/api/public/**"))

                                // Session Management
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(
                                                                org.springframework.security.config.http.SessionCreationPolicy.STATELESS)
                                                .maximumSessions(5) // Máximo 5 sesiones por usuario
                                                .maxSessionsPreventsLogin(false) // No prevenir login, invalidar sesión
                                                                                 // más antigua
                                )

                                // Exception Handling
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                                        response.getWriter()
                                                                        .write("{\"error\": \"Unauthorized access\"}");
                                                })
                                                .accessDeniedHandler((request, response, accessDeniedException) -> {
                                                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                                        response.getWriter().write("{\"error\": \"Access denied\"}");
                                                }))

                                .build();
        }

        /**
         * Configuración CORS con cabeceras de seguridad
         */
        @Override
        public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                                .allowedOrigins(allowedOrigins)
                                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                                .allowedHeaders("*")
                                .allowCredentials(true)
                                .exposedHeaders("Authorization", "X-CSRF-TOKEN")
                                .maxAge(3600);
        }
}