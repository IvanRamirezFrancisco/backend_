package com.security.controller;

import com.security.dto.request.LoginRequest;
import com.security.dto.response.ApiResponse;
import com.security.dto.response.JwtAuthResponse;
import com.security.service.LoginSecurityService;
import com.security.service.SecureJwtService;
import com.security.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador de autenticación con seguridad avanzada
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class SecureAuthController {

    private static final Logger logger = LoggerFactory.getLogger(SecureAuthController.class);

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private AuthService authService;

    @Autowired
    private SecureJwtService jwtService;

    @Autowired
    private LoginSecurityService loginSecurityService;

    /**
     * Endpoint para obtener token CSRF
     */
    @GetMapping("/csrf-token")
    public ResponseEntity<ApiResponse> getCsrfToken(HttpServletRequest request) {
        try {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                return ResponseEntity.ok(new ApiResponse(true, csrfToken.getToken()));
            } else {
                return ResponseEntity.ok(new ApiResponse(true, "CSRF not required for this endpoint"));
            }
        } catch (Exception e) {
            logger.error("Error getting CSRF token: {}", e.getMessage());
            return ResponseEntity.status(500).body(new ApiResponse(false, "Error retrieving CSRF token"));
        }
    }

    /**
     * Login seguro con protección contra ataques de fuerza bruta
     */
    @PostMapping("/secure-login")
    public ResponseEntity<ApiResponse> secureLogin(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        String identifier = loginRequest.getEmail();

        try {
            // Verificar si la cuenta está bloqueada
            if (loginSecurityService.isAccountLocked(identifier)) {
                long remainingMinutes = loginSecurityService.getLockoutRemainingMinutes(identifier);

                logger.warn("Login attempt for locked account: {} from IP: {}",
                        maskEmail(identifier), clientIp);

                return ResponseEntity.status(423).body(new ApiResponse(
                        false,
                        String.format("Cuenta bloqueada. Inténtalo de nuevo en %d minutos.", remainingMinutes)));
            }

            // Intentar autenticación
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()));

            // Login exitoso - limpiar intentos fallidos
            loginSecurityService.clearFailedAttempts(identifier);

            // Generar tokens
            String deviceInfo = userAgent;
            String accessToken = jwtService.generateAccessToken(authentication, deviceInfo);
            String refreshToken = jwtService.generateRefreshToken(
                    authentication.getName(), deviceInfo);

            // Registrar actividad de sesión
            loginSecurityService.recordSessionActivity(authentication.getName());

            // Configurar cookie segura para el token
            response.addHeader("Set-Cookie", createSecureCookie("accessToken", accessToken));
            response.addHeader("Set-Cookie", createSecureCookie("refreshToken", refreshToken));

            // Respuesta
            JwtAuthResponse authResponse = new JwtAuthResponse();
            authResponse.setAccessToken(accessToken);
            authResponse.setRefreshToken(refreshToken);
            authResponse.setTokenType("Bearer");
            authResponse.setExpiresIn(900L); // 15 minutos

            logger.info("Successful login for user: {} from IP: {}",
                    maskEmail(identifier), clientIp);

            return ResponseEntity.ok(new ApiResponse(true, "Login exitoso", authResponse));

        } catch (AuthenticationException e) {
            // Registrar intento fallido
            loginSecurityService.recordFailedAttempt(identifier);

            int failedAttempts = loginSecurityService.getFailedAttempts(identifier);

            logger.warn("Failed login attempt {} for user: {} from IP: {}",
                    failedAttempts, maskEmail(identifier), clientIp);

            return ResponseEntity.status(401).body(new ApiResponse(
                    false,
                    "Credenciales inválidas. Intento " + failedAttempts + " de 5."));

        } catch (Exception e) {
            logger.error("Error during secure login: {}", e.getMessage());
            return ResponseEntity.status(500).body(new ApiResponse(
                    false,
                    "Error interno del servidor"));
        }
    }

    /**
     * Logout seguro con invalidación de tokens
     */
    @PostMapping("/secure-logout")
    public ResponseEntity<ApiResponse> secureLogout(
            HttpServletRequest request,
            @RequestParam(value = "allDevices", defaultValue = "false") boolean allDevices) {

        try {
            String token = extractTokenFromRequest(request);
            if (token != null) {
                String userId = jwtService.getUserIdFromToken(token);

                if (allDevices && userId != null) {
                    // Invalidar todas las sesiones del usuario
                    loginSecurityService.invalidateAllUserSessions(userId);
                    logger.info("All sessions invalidated for user: {}", userId);
                } else {
                    // Invalidar solo esta sesión
                    jwtService.invalidateToken(token);
                    if (userId != null) {
                        loginSecurityService.invalidateSession(userId);
                    }
                    logger.info("Single session invalidated for user: {}", userId);
                }
            }

            return ResponseEntity.ok(new ApiResponse(true, "Logout exitoso"));

        } catch (Exception e) {
            logger.error("Error during secure logout: {}", e.getMessage());
            return ResponseEntity.status(500).body(new ApiResponse(
                    false,
                    "Error durante logout"));
        }
    }

    /**
     * Renovar token de acceso
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse> refreshAccessToken(
            @RequestBody String refreshToken,
            HttpServletRequest request) {

        try {
            String deviceInfo = request.getHeader("User-Agent");
            String newAccessToken = jwtService.renewAccessToken(refreshToken, deviceInfo);

            JwtAuthResponse authResponse = new JwtAuthResponse();
            authResponse.setAccessToken(newAccessToken);
            authResponse.setTokenType("Bearer");
            authResponse.setExpiresIn(900L); // 15 minutos

            return ResponseEntity.ok(new ApiResponse(true, "Token renovado", authResponse));

        } catch (Exception e) {
            logger.error("Error refreshing token: {}", e.getMessage());
            return ResponseEntity.status(401).body(new ApiResponse(
                    false,
                    "Token de actualización inválido"));
        }
    }

    /**
     * Verifica el estado de una sesión
     */
    @GetMapping("/session-status")
    public ResponseEntity<ApiResponse> getSessionStatus(HttpServletRequest request) {
        try {
            String token = extractTokenFromRequest(request);
            if (token != null && jwtService.validateToken(token)) {
                String userId = jwtService.getUserIdFromToken(token);
                boolean isActive = loginSecurityService.isSessionActive(userId);

                return ResponseEntity.ok(new ApiResponse(true, "Session active: " + isActive));
            } else {
                return ResponseEntity.status(401).body(new ApiResponse(false, "Invalid session"));
            }

        } catch (Exception e) {
            logger.error("Error checking session status: {}", e.getMessage());
            return ResponseEntity.status(500).body(new ApiResponse(false, "Error checking session"));
        }
    }

    /**
     * Extrae el token del request
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Crea una cookie segura
     */
    private String createSecureCookie(String name, String value) {
        return String.format("%s=%s; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=900",
                name, value);
    }

    /**
     * Obtiene la IP real del cliente
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String xRealIp = request.getHeader("X-Real-IP");
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");

        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
            return cfConnectingIp;
        }

        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Enmascara email para logging seguro
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 3)
            return "***";
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            return email.substring(0, 1) + "***" + email.substring(atIndex);
        }
        return email.substring(0, 1) + "***";
    }
}