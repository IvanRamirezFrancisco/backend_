package com.security.config;

import com.security.security.CustomUserDetailsService;
import com.security.security.JwtAuthenticationEntryPoint;
import com.security.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

        @Autowired
        private CustomUserDetailsService customUserDetailsService;

        @Autowired
        private JwtAuthenticationEntryPoint unauthorizedHandler;

        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter() {
                return new JwtAuthenticationFilter();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public DaoAuthenticationProvider authenticationProvider() {
                DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
                authProvider.setUserDetailsService(customUserDetailsService);
                authProvider.setPasswordEncoder(passwordEncoder());
                return authProvider;
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
                return authConfig.getAuthenticationManager();
        }

        /**
         * Configuración CORS robusta para producción en Render + Netlify
         */
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();

                // IMPORTANTE: Usar SOLO allowedOriginPatterns cuando allowCredentials es true
                // NO usar setAllowedOrigins con "*" porque causa error
                config.setAllowedOriginPatterns(Arrays.asList(
                                "http://localhost:4200",
                                "http://localhost:4300",
                                "http://localhost:*",
                                "http://127.0.0.1:*",
                                "https://casa-musica-castillo.netlify.app",
                                "https://bucolic-torrone-10f382.netlify.app",
                                "https://*.netlify.app",
                                "https://fronlogin-production.up.railway.app",
                                "https://*.railway.app",
                                "https://*.up.railway.app",
                                "https://*.onrender.com"));

                // Métodos HTTP permitidos
                config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));

                // Headers permitidos - TODOS
                config.setAllowedHeaders(Arrays.asList("*"));

                // Headers expuestos al cliente
                config.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Total-Count"));

                // Permitir credenciales (cookies, auth headers)
                config.setAllowCredentials(true);

                // Cache preflight por 1 hora
                config.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                // Configuración CORS
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                                // Deshabilitar CSRF para API REST
                                .csrf(csrf -> csrf.disable())

                                // Deshabilitar form login y http basic (evita redirecciones 302)
                                .formLogin(form -> form.disable())
                                .httpBasic(basic -> basic.disable())

                                // Manejo de excepciones - devolver 401 en lugar de redirigir
                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint(unauthorizedHandler))

                                // Sin sesiones (stateless para JWT)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // Headers de seguridad COMPLETOS para producción
                                .headers(headers -> headers
                                                // X-Frame-Options: Previene clickjacking
                                                .frameOptions(frameOptions -> frameOptions.deny())

                                                // X-Content-Type-Options: Previene MIME sniffing
                                                .contentTypeOptions(contentType -> contentType.disable())

                                                // X-XSS-Protection: Protección XSS del navegador
                                                .xssProtection(xss -> xss.headerValue(
                                                                org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))

                                                // Content-Security-Policy: Previene XSS y injection
                                                .contentSecurityPolicy(csp -> csp.policyDirectives(
                                                                "default-src 'self'; " +
                                                                                "script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
                                                                                +
                                                                                "style-src 'self' 'unsafe-inline'; " +
                                                                                "img-src 'self' data: https:; " +
                                                                                "font-src 'self' https:; " +
                                                                                "connect-src 'self' https:; " +
                                                                                "frame-ancestors 'none'"))

                                                // HTTP Strict Transport Security: Fuerza HTTPS
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .maxAgeInSeconds(31536000) // 1 año
                                                                .includeSubDomains(true)
                                                                .preload(true))

                                                // Referrer-Policy: Controla información de referencia
                                                .referrerPolicy(
                                                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))

                                // Autorización de endpoints
                                .authorizeHttpRequests(authz -> authz
                                                // CORS preflight - SIEMPRE permitir OPTIONS primero
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                                                // ========== ENDPOINTS PÚBLICOS ==========
                                                // Autenticación
                                                .requestMatchers("/api/auth/**").permitAll()

                                                // 2FA endpoints para login (sin autenticación) - MUY IMPORTANTE
                                                .requestMatchers("/api/2fa/verify").permitAll()
                                                .requestMatchers("/api/2fa/send-login-code").permitAll()

                                                // Health y test
                                                .requestMatchers("/api/test/public").permitAll()
                                                .requestMatchers("/api/test/health").permitAll()
                                                .requestMatchers("/actuator/**").permitAll()
                                                .requestMatchers("/error").permitAll()

                                                // Swagger/OpenAPI
                                                .requestMatchers("/swagger-ui/**").permitAll()
                                                .requestMatchers("/v3/api-docs/**").permitAll()
                                                .requestMatchers("/h2-console/**").permitAll()

                                                // ========== ENDPOINTS PROTEGIDOS ==========
                                                // Administración - Solo ADMIN/SUPER_ADMIN
                                                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                                                // 2FA setup/config (requiere estar logueado) - EXCLUYE los ya
                                                // permitidos
                                                .requestMatchers("/api/2fa/google/**").authenticated()
                                                .requestMatchers("/api/2fa/email/**").authenticated()
                                                .requestMatchers("/api/2fa/backup-codes/**").authenticated()
                                                .requestMatchers("/api/2fa/status").authenticated()

                                                // Test endpoints con RBAC
                                                .requestMatchers("/api/test/protected").authenticated()
                                                .requestMatchers("/api/test/admin").hasAnyRole("ADMIN", "SUPER_ADMIN")

                                                // Usuarios - usuarios normales pueden ver su perfil y cambiar
                                                // contraseña, admins todo
                                                .requestMatchers("/api/users/profile").authenticated()
                                                .requestMatchers("/api/users/change-password").authenticated()
                                                .requestMatchers("/api/users/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                                                // Todo lo demás requiere autenticación
                                                .anyRequest().authenticated());

                // Agregar providers y filtros
                http.authenticationProvider(authenticationProvider());
                http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}