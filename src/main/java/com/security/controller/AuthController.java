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

import java.util.Map;

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
@CrossOrigin(origins = { "http://localhost:4200", "https://fronlogin-production.up.railway.app" })
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
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            User user = userService.findByEmail(loginRequest.getEmail()).orElse(null);
            if (user == null || !passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "Credenciales inválidas"));
            }

            UserResponse userResponse = userService.convertToUserResponse(user);

            // Si el usuario tiene cualquier método 2FA activado, pide 2FA y NO envía el
            // token
            boolean hasTwoFactorEnabled = Boolean.TRUE.equals(user.getGoogleAuthEnabled()) ||
                    Boolean.TRUE.equals(user.getSmsEnabled()) ||
                    Boolean.TRUE.equals(user.getEmailEnabled()) ||
                    Boolean.TRUE.equals(user.getBackupCodesEnabled());

            if (hasTwoFactorEnabled) {
                Map<String, Object> data = new HashMap<>();
                data.put("twoFactorRequired", true);
                data.put("user", userResponse);

                // Indicar qué métodos 2FA están disponibles
                Map<String, Boolean> availableMethods = new HashMap<>();
                availableMethods.put("GOOGLE_AUTHENTICATOR", Boolean.TRUE.equals(user.getGoogleAuthEnabled()));
                availableMethods.put("SMS", Boolean.TRUE.equals(user.getSmsEnabled()));
                availableMethods.put("EMAIL", Boolean.TRUE.equals(user.getEmailEnabled()));
                availableMethods.put("BACKUP_CODE", Boolean.TRUE.equals(user.getBackupCodesEnabled()));
                data.put("availableMethods", availableMethods);

                return ResponseEntity.ok(new ApiResponse(true, "Two-factor authentication required", data));
            }

            // Si NO tiene 2FA, genera el token y responde normalmente
            String token = jwtTokenProvider.generateTokenFromUserId(
                    user.getId(),
                    user.getEmail(),
                    user.getRoles().stream().map(role -> role.getName().name()).collect(Collectors.toSet()));
            JwtAuthResponse jwtResponse = new JwtAuthResponse();
            jwtResponse.setAccessToken(token);
            jwtResponse.setTokenType("Bearer");
            jwtResponse.setUser(userResponse);
            return ResponseEntity.ok(new ApiResponse(true, "Login successful", jwtResponse));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error en login: " + e.getMessage()));
        }
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

}
