package com.security.controller;

import com.security.dto.request.ResendVerificationRequest;
import com.security.dto.request.LoginRequest;
import com.security.dto.request.RegisterRequest;
import com.security.dto.request.VerifyEmailRequest;
import com.security.dto.response.ApiResponse;
import com.security.dto.response.JwtAuthResponse;
import com.security.dto.response.UserResponse;
import com.security.service.AuthService;
import com.security.service.VerificationService;
import com.security.service.UserService;
import com.security.service.PasswordResetService;
import com.security.service.LoginSecurityService;
import com.security.service.SessionManagementService;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

import com.security.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.security.security.JwtTokenProvider;

import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
// CORS se maneja globalmente en SecurityConfig
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private VerificationService verificationService; // ← AÑADIR ESTA INYECCIÓN}}
    @Autowired
    private UserService userService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private LoginSecurityService loginSecurityService;

    @Autowired
    private SessionManagementService sessionManagementService;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            UserResponse user = authService.getUserFromToken(token);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "Usuario no autenticado"));
            }
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error obteniendo usuario: " + e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            UserResponse user = authService.registerUser(registerRequest);
            return ResponseEntity.ok(new ApiResponse(true,
                    "User registered successfully. Please check your email to verify your account.", user));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        try {
            String email = loginRequest.getEmail();
            String clientIp = getClientIP(request);

            // ===== VERIFICACIÓN DE BLOQUEO POR FUERZA BRUTA =====
            // Verificar si la cuenta está bloqueada por intentos fallidos
            if (loginSecurityService.isAccountLocked(email)) {
                long remainingMinutes = loginSecurityService.getLockoutRemainingMinutes(email);
                String message = String.format(
                        "Cuenta bloqueada temporalmente por múltiples intentos fallidos. " +
                                "Intenta de nuevo en %d minutos.",
                        remainingMinutes > 0 ? remainingMinutes : 1);

                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse(false, message));
            }

            // También verificar bloqueo por IP
            if (loginSecurityService.isAccountLocked(clientIp)) {
                long remainingMinutes = loginSecurityService.getLockoutRemainingMinutes(clientIp);
                String message = String.format(
                        "Demasiados intentos desde esta IP. Intenta de nuevo en %d minutos.",
                        remainingMinutes > 0 ? remainingMinutes : 1);

                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ApiResponse(false, message));
            }

            // ===== AUTENTICACIÓN =====
            User user = userService.findByEmail(email).orElse(null);
            if (user == null || !passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
                // Registrar intento fallido por email e IP
                loginSecurityService.recordFailedAttempt(email);
                loginSecurityService.recordFailedAttempt(clientIp);

                // Verificar si ahora está bloqueado para dar mensaje apropiado
                if (loginSecurityService.isAccountLocked(email)) {
                    long remainingMinutes = loginSecurityService.getLockoutRemainingMinutes(email);
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(new ApiResponse(false,
                                    String.format("Has excedido el número máximo de intentos. " +
                                            "Cuenta bloqueada por %d minutos.", remainingMinutes)));
                }

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "Credenciales inválidas"));
            }

            // ===== LOGIN EXITOSO - Limpiar intentos fallidos =====
            loginSecurityService.clearFailedAttempts(email);
            loginSecurityService.clearFailedAttempts(clientIp);

            UserResponse userResponse = userService.convertToUserResponse(user);

            // ===== LÓGICA DE 2FA CORREGIDA =====
            // Solo Google Auth y Email son métodos ACTIVOS de 2FA
            // Backup Codes son un método de RESPALDO (no activan 2FA por sí solos)
            boolean hasGoogleAuth = Boolean.TRUE.equals(user.getGoogleAuthEnabled());
            boolean hasEmail2FA = Boolean.TRUE.equals(user.getEmailEnabled());
            boolean hasBackupCodes = Boolean.TRUE.equals(user.getBackupCodesEnabled());

            // 2FA se requiere SOLO si hay al menos un método principal activo (Google o
            // Email)
            boolean requiresTwoFactor = hasGoogleAuth || hasEmail2FA;

            if (requiresTwoFactor) {
                Map<String, Object> data = new HashMap<>();
                data.put("twoFactorRequired", true);
                data.put("user", userResponse);

                // Indicar qué métodos 2FA están disponibles
                Map<String, Boolean> availableMethods = new HashMap<>();
                availableMethods.put("GOOGLE_AUTHENTICATOR", hasGoogleAuth);
                availableMethods.put("EMAIL", hasEmail2FA);
                // Backup codes disponibles como alternativa solo si existen Y hay otro método
                // activo
                availableMethods.put("BACKUP_CODE", hasBackupCodes);
                data.put("availableMethods", availableMethods);

                return ResponseEntity.ok(new ApiResponse(true, "Two-factor authentication required", data));
            }

            // Si NO tiene métodos 2FA principales activos (Google/Email), login normal
            // Los backup codes quedan almacenados pero inactivos hasta que se active otro
            // método

            // Si NO tiene 2FA, genera el token con manejo de sesiones y responde normalmente
            String token = jwtTokenProvider.generateTokenFromUserId(
                    user.getId(),
                    user.getEmail(),
                    user.getRoles().stream().map(role -> role.getName().name()).collect(Collectors.toSet()));
            
            // Obtener información de sesiones activas para incluir en la respuesta
            long activeSessions = sessionManagementService.getActiveSessionCount(user.getEmail());
            
            JwtAuthResponse jwtResponse = new JwtAuthResponse();
            jwtResponse.setAccessToken(token);
            jwtResponse.setTokenType("Bearer");
            jwtResponse.setUser(userResponse);
            
            // Agregar información adicional sobre sesiones
            Map<String, Object> sessionInfo = new HashMap<>();
            sessionInfo.put("activeSessions", activeSessions);
            sessionInfo.put("maxSessions", 2); // Configurado en application.yml
            sessionInfo.put("sessionInfo", "Sesiones activas: " + activeSessions + "/2");
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("jwtResponse", jwtResponse);
            responseData.put("sessionManagement", sessionInfo);
            
            return ResponseEntity.ok(new ApiResponse(true, "Inicio de sesión exitoso", responseData));
        } catch (Exception e) {
            // Traducir mensajes de error técnicos a español
            String errorMessage = traducirError(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, errorMessage));
        }
    }

    /**
     * Traduce mensajes de error técnicos a español amigable
     */
    private String traducirError(String mensaje) {
        if (mensaje == null) {
            return "Error interno del servidor. Por favor intenta de nuevo.";
        }

        // Mapeo de errores comunes
        if (mensaje.contains("Transaction silently rolled back")) {
            return "Error temporal en el servidor. Por favor intenta de nuevo.";
        }
        if (mensaje.contains("Connection refused") || mensaje.contains("connect")) {
            return "Error de conexión con el servidor. Por favor intenta más tarde.";
        }
        if (mensaje.contains("timeout") || mensaje.contains("Timeout")) {
            return "El servidor tardó demasiado en responder. Intenta de nuevo.";
        }
        if (mensaje.contains("Unauthorized") || mensaje.contains("401")) {
            return "Credenciales inválidas. Verifica tu correo y contraseña.";
        }
        if (mensaje.contains("locked") || mensaje.contains("bloqueada")) {
            return mensaje; // Ya está en español
        }

        // Si no es un error conocido, mostrar mensaje genérico
        return "Ocurrió un error inesperado. Por favor intenta de nuevo.";
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7); // Remove "Bearer " prefix
            JwtAuthResponse jwtResponse = authService.refreshToken(token);
            return ResponseEntity.ok(jwtResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody VerifyEmailRequest request) {
        try {
            System.out.println("📧 Verificando email con token: " + request.getToken());

            // Usar el método del UserService que creamos
            userService.verifyEmailToken(request.getToken());

            System.out.println("✅ Email verificado exitosamente");
            return ResponseEntity
                    .ok(new ApiResponse(true, "¡Email verificado exitosamente! Ya puedes iniciar sesión."));
        } catch (IllegalArgumentException e) {
            System.err.println("❌ Token inválido: " + e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "El enlace de verificación es inválido o ha expirado."));
        } catch (Exception e) {
            System.err.println("❌ Error verificando email: " + e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Error al verificar el email. Por favor intenta nuevamente."));
        }
    }

    // ENDPOINTS DE PASSWORD RESET MOVIDOS A PasswordResetController
    // Para evitar conflictos de mapping duplicado

    /////////
    /// REENVÍO DE EMAIL DE VERIFICACIÓN
    @GetMapping("/verify")
    public ResponseEntity<?> verifyAccount(@RequestParam("token") String token) {
        try {
            // Usar el método del UserService que creamos
            userService.verifyEmailToken(token);

            // Redirigir al frontend con mensaje de éxito
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("http://localhost:4200/login?verified=true"))
                    .build();
        } catch (Exception e) {
            // Redirigir al frontend con mensaje de error
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("http://localhost:4200/login?error=verification_failed"))
                    .build();
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody ResendVerificationRequest request) {
        try {
            verificationService.resendVerificationEmail(request.getEmail());
            return ResponseEntity.ok(new ApiResponse(true,
                    "Verification email sent successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ===== MÉTODOS DE GOOGLE AUTHENTICATOR REMOVIDOS =====
    //
    // ESTOS ENDPOINTS SE MOVIERON A TwoFactorController PARA MANTENER
    // LA SEPARACIÓN DE RESPONSABILIDADES Y EVITAR CONFLICTOS:
    //
    // AuthController se enfoca ÚNICAMENTE en:
    // - Register/Login básico
    // - Verificación de email
    // - Gestión de tokens JWT
    //
    // TODA LA LÓGICA 2FA/TOTP AHORA ESTÁ EN:
    // - TwoFactorController: Endpoints 2FA (/api/2fa/*)
    // - TwoFactorService: Orquestación 2FA
    // - TotpService: Lógica TOTP pura
    //
    // NUEVOS ENDPOINTS CORRECTOS PARA GOOGLE AUTHENTICATOR:
    // - POST /api/2fa/google/enable (habilitar)
    // - GET /api/2fa/google/qrcode (obtener QR)
    // - POST /api/2fa/google/confirm (confirmar setup)
    // - POST /api/2fa/verify (login con 2FA)
    // - GET /api/2fa/debug/validate-totp (debugging)
    //
    // ¡Esto garantiza que el secret sea CONSISTENTE entre BD y QR!

    @GetMapping("/check-username/{username}")
    public ResponseEntity<?> checkUsernameAvailability(@PathVariable("username") String username) {
        try {
            boolean exists = userService.existsByUsername(username);
            Map<String, Object> response = new HashMap<>();
            response.put("available", !exists);
            response.put("username", username);

            if (exists) {
                response.put("message", "Username is already taken");
            } else {
                response.put("message", "Username is available");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error checking username availability: " + e.getMessage()));
        }
    }

    /**
     * Obtiene la IP real del cliente considerando proxies y balanceadores de carga
     */
    private String getClientIP(HttpServletRequest request) {
        // Lista de headers que pueden contener la IP real del cliente
        String[] headersToCheck = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        for (String header : headersToCheck) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For puede contener múltiples IPs separadas por coma
                // La primera es la IP del cliente original
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        // Si no hay headers de proxy, usar la IP directa
        return request.getRemoteAddr();
    }

    // ===== GESTIÓN DE SESIONES - REQUISITOS DE RÚBRICA =====

    /**
     * REQUISITO 2: Logout - Cierra sesión específica en un dispositivo
     * Invalida la sesión actual pero mantiene otras sesiones activas
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        try {
            String token = jwtTokenProvider.getTokenFromRequest(request);
            
            if (token != null && jwtTokenProvider.validateToken(token)) {
                String jti = jwtTokenProvider.getJtiFromJWT(token);
                String email = jwtTokenProvider.getEmailFromJWT(token);
                
                if (jti != null) {
                    // Invalidar sesión específica
                    sessionManagementService.invalidateSession(jti);
                    
                    return ResponseEntity.ok(Map.of(
                        "message", "Sesión cerrada exitosamente",
                        "sessionClosed", jti,
                        "user", email
                    ));
                }
            }
            
            return ResponseEntity.ok(Map.of("message", "Logout exitoso"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error durante logout: " + e.getMessage()));
        }
    }

    /**
     * REQUISITO 2: Logout desde todos los dispositivos
     * Cierra todas las sesiones activas del usuario
     */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutFromAllDevices(HttpServletRequest request) {
        try {
            String token = jwtTokenProvider.getTokenFromRequest(request);
            
            if (token != null && jwtTokenProvider.validateToken(token)) {
                String email = jwtTokenProvider.getEmailFromJWT(token);
                
                // Cerrar todas las sesiones del usuario
                sessionManagementService.invalidateAllUserSessions(email);
                
                return ResponseEntity.ok(Map.of(
                    "message", "Todas las sesiones han sido cerradas",
                    "user", email,
                    "action", "logout-all-devices"
                ));
            }
            
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Token inválido"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error cerrando sesiones: " + e.getMessage()));
        }
    }

    /**
     * Ver sesiones activas del usuario actual
     */
    @GetMapping("/sessions")
    public ResponseEntity<?> getActiveSessions(HttpServletRequest request) {
        try {
            String token = jwtTokenProvider.getTokenFromRequest(request);
            
            if (token != null && jwtTokenProvider.validateToken(token)) {
                String email = jwtTokenProvider.getEmailFromJWT(token);
                String currentJti = jwtTokenProvider.getJtiFromJWT(token);
                
                var sessions = sessionManagementService.getUserActiveSessions(email);
                long activeCount = sessionManagementService.getActiveSessionCount(email);
                
                // Crear respuesta con información de sesiones
                Map<String, Object> response = new HashMap<>();
                response.put("activeSessions", activeCount);
                response.put("maxAllowedSessions", 2);
                response.put("currentSessionId", currentJti);
                response.put("sessions", sessions.stream().map(session -> {
                    Map<String, Object> sessionInfo = new HashMap<>();
                    sessionInfo.put("id", session.getJwtTokenId());
                    sessionInfo.put("deviceInfo", extractDeviceInfo(session.getUserAgent()));
                    sessionInfo.put("ipAddress", session.getIpAddress());
                    sessionInfo.put("createdAt", session.getCreatedAt());
                    sessionInfo.put("lastActivity", session.getLastActivity());
                    sessionInfo.put("isCurrent", session.getJwtTokenId().equals(currentJti));
                    return sessionInfo;
                }).collect(java.util.stream.Collectors.toList()));
                
                return ResponseEntity.ok(new ApiResponse(true, "Sesiones obtenidas exitosamente", response));
            }
            
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Token inválido"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error obteniendo sesiones: " + e.getMessage()));
        }
    }

    /**
     * Cerrar una sesión específica por ID
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<?> closeSpecificSession(@PathVariable String sessionId, HttpServletRequest request) {
        try {
            String token = jwtTokenProvider.getTokenFromRequest(request);
            
            if (token != null && jwtTokenProvider.validateToken(token)) {
                String email = jwtTokenProvider.getEmailFromJWT(token);
                
                // Verificar que la sesión pertenece al usuario actual
                var sessionInfo = sessionManagementService.getSessionInfo(sessionId);
                if (sessionInfo.isPresent() && sessionInfo.get().getUser().getEmail().equals(email)) {
                    sessionManagementService.invalidateSession(sessionId);
                    
                    return ResponseEntity.ok(Map.of(
                        "message", "Sesión cerrada exitosamente",
                        "sessionId", sessionId
                    ));
                } else {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "Sesión no encontrada o no autorizada"));
                }
            }
            
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Token inválido"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error cerrando sesión: " + e.getMessage()));
        }
    }

    // Método utilitario para extraer información del dispositivo
    private String extractDeviceInfo(String userAgent) {
        if (userAgent == null) return "Desconocido";
        
        if (userAgent.contains("Mobile") || userAgent.contains("Android") || userAgent.contains("iPhone")) {
            return "Móvil";
        } else if (userAgent.contains("Tablet") || userAgent.contains("iPad")) {
            return "Tablet";
        } else {
            return "Escritorio";
        }
    }

}
