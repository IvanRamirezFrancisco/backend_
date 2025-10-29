package com.security.controller;

import com.security.service.PasswordResetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = { "http://localhost:4200", "https://fronlogin-production.up.railway.app" })
public class PasswordResetController {

    @Autowired
    private PasswordResetService passwordResetService;

    /**
     * Solicitar reset de contraseña
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String email) {
        try {
            boolean success = passwordResetService.requestPasswordReset(email);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message",
                        "Si el email está registrado, recibirás un enlace de recuperación en tu bandeja de entrada."));
            } else {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "No se encontró una cuenta asociada a ese email. Verifica e intenta nuevamente."));
            }

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Error interno del servidor. Intenta nuevamente más tarde."));
        }
    }

    /**
     * Validar token de reset
     */
    @GetMapping("/validate-reset-token")
    public ResponseEntity<?> validateResetToken(@RequestParam String token) {
        try {
            boolean isValid = passwordResetService.validateResetToken(token);

            if (isValid) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Token válido"));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "El enlace de reset ha expirado o es inválido. Solicita uno nuevo."));
            }

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Error al validar el token"));
        }
    }

    /**
     * Resetear contraseña
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam String token, @RequestParam String password) {
        try {
            // Validar que la contraseña no esté vacía
            if (password == null || password.trim().length() < 8) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "La contraseña debe tener al menos 8 caracteres"));
            }

            boolean success = passwordResetService.resetPassword(token, password);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message",
                        "¡Contraseña actualizada exitosamente! Ya puedes iniciar sesión con tu nueva contraseña."));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "El enlace de reset ha expirado o es inválido. Solicita uno nuevo."));
            }

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Error al actualizar la contraseña. Intenta nuevamente."));
        }
    }
}