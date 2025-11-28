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
import java.util.Collections;

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
     * Configuración CORS robusta para producción en Railway
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Orígenes permitidos explícitamente
        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:4200",
            "http://localhost:4300", 
            "http://127.0.0.1:4200",
            "https://fronlogin-production.up.railway.app"
        ));
        
        // También usar patrones para flexibilidad
        config.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "https://*.railway.app",
            "https://*.up.railway.app"
        ));
        
        // Métodos HTTP permitidos
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));
        
        // Headers permitidos - TODOS
        config.setAllowedHeaders(Collections.singletonList("*"));
        
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
            
            // Headers de seguridad
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
                .contentTypeOptions(contentType -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(false)
                    .preload(false)))
            
            // Autorización de endpoints
            .authorizeHttpRequests(authz -> authz
                // CORS preflight - SIEMPRE permitir OPTIONS primero
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // ========== ENDPOINTS PÚBLICOS ==========
                // Autenticación
                .requestMatchers("/api/auth/**").permitAll()
                
                // 2FA endpoints para login (sin autenticación)
                .requestMatchers(HttpMethod.POST, "/api/2fa/verify").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/2fa/send-login-code").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/api/2fa/**").permitAll()
                
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
                // 2FA setup/config (requiere estar logueado)
                .requestMatchers("/api/2fa/**").authenticated()
                
                // Test endpoints
                .requestMatchers("/api/test/protected").authenticated()
                .requestMatchers("/api/test/admin").hasRole("ADMIN")
                
                // Usuarios
                .requestMatchers("/api/users/**").authenticated()
                
                // Todo lo demás requiere autenticación
                .anyRequest().authenticated());

        // Agregar providers y filtros
        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}