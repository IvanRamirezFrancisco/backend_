package com.security.controller;

import com.security.dto.request.ResendVerificationRequest;
import com.security.dto.request.LoginRequest;
import com.security.dto.request.RegisterRequest;
import com.security.dto.response.ApiResponse;
import com.security.dto.response.JwtAuthResponse;
import com.security.dto.response.UserResponse;
import com.security.service.AuthService;
import com.security.service.VerificationService;
import com.security.service.UserService;

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

            // Si el usuario tiene Google Authenticator activado, pide 2FA y NO envía el
            // token
            if (Boolean.TRUE.equals(user.getGoogleAuthEnabled())) {
                Map<String, Object> data = new HashMap<>();
                data.put("twoFactorRequired", true);
                data.put("user", userResponse);
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
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        try {
            // Usar el método del UserService que creamos
            userService.verifyEmailToken(token);
            return ResponseEntity.ok(new ApiResponse(true, "Email verified successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // @PostMapping("/forgot-password")
    // public ResponseEntity<?> forgotPassword(@RequestParam("email") String email)
    // {
    // try {
    // authService.resetPassword(email);
    // return ResponseEntity.ok(new ApiResponse(true, "Password reset email sent"));
    // } catch (Exception e) {
    // return ResponseEntity.badRequest()
    // .body(new ApiResponse(false, e.getMessage()));
    // }
    // }

    // @PostMapping("/reset-password")
    // public ResponseEntity<?> resetPassword(@RequestParam("token") String token,
    // @RequestParam("password") String newPassword) {
    // try {
    // authService.confirmPasswordReset(token, newPassword);
    // return ResponseEntity.ok(new ApiResponse(true, "Password reset
    // successfully"));
    // } catch (Exception e) {
    // return ResponseEntity.badRequest()
    // .body(new ApiResponse(false, e.getMessage()));
    // }
    // }

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

    @GetMapping("/google-auth/setup")
    public ResponseEntity<?> getGoogleAuthSetup(@RequestHeader("Authorization") String authHeader) {
        try {
            // Extrae el usuario autenticado desde el token JWT
            String token = authHeader.replace("Bearer ", "");
            UserResponse user = authService.getUserFromToken(token);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "Usuario no autenticado"));
            }

            // Genera el secreto y el QR para Google Authenticator
            String secret = authService.generateGoogleAuthSecret(user.getEmail());
            String qrCodeUrl = authService.generateGoogleAuthQrUrl(user.getEmail(), secret);

            // Guarda el secreto en la base de datos si es necesario
            authService.saveGoogleAuthSecret(user.getId(), secret);

            // Devuelve el QR y el secreto
            return ResponseEntity.ok(new ApiResponse(true, "Google Authenticator setup",
                    Map.of("qrCodeUrl", qrCodeUrl, "secret", secret)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error generando QR: " + e.getMessage()));
        }
    }

    @PostMapping("/google-auth/confirm")
    public ResponseEntity<?> confirmGoogleAuth(@RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        try {
            String token = authHeader.replace("Bearer ", "");
            UserResponse user = authService.getUserFromToken(token);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "Usuario no autenticado"));
            }
            String code = body.get("code");
            boolean valid = authService.verifyGoogleAuthCode(user.getId(), code);
            if (valid) {
                authService.enableGoogleAuthForUser(user.getId());
                return ResponseEntity.ok(new ApiResponse(true, "Google Authenticator activado"));
            } else {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Código inválido"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error confirmando Google Authenticator"));
        }
    }

}
