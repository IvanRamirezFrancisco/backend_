package com.security.controller;

import com.security.dto.request.ChangePasswordRequest;
import com.security.dto.response.ApiResponse;
import com.security.dto.response.UserResponse;
import com.security.entity.User;
import com.security.security.CurrentUser;
import com.security.security.UserPrincipal;
import com.security.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
// CORS se maneja globalmente en SecurityConfig
public class UserController {

        @Autowired
        private UserService userService;

        @Autowired
        private PasswordEncoder passwordEncoder;

        /**
         * Obtener perfil del usuario actual autenticado
         */
        @GetMapping("/profile")
        @PreAuthorize("hasRole('USER')")
        public ResponseEntity<?> getUserProfile(@CurrentUser UserPrincipal userPrincipal) {
                try {
                        User user = userService.getUserById(userPrincipal.getId());

                        // Crear respuesta con datos del perfil
                        Map<String, Object> profileData = new HashMap<>();
                        profileData.put("id", user.getId());
                        profileData.put("email", user.getEmail());
                        profileData.put("firstName", user.getFirstName());
                        profileData.put("lastName", user.getLastName());
                        profileData.put("phone", user.getPhone());
                        profileData.put("enabled", user.getEnabled());

                        // Estados 2FA
                        profileData.put("twoFactorEnabled", user.getTwoFactorEnabled());
                        profileData.put("googleAuthEnabled", user.getGoogleAuthEnabled());
                        profileData.put("emailEnabled", user.getEmailEnabled());
                        profileData.put("backupCodesEnabled", user.getBackupCodesEnabled());

                        // Timestamps
                        profileData.put("createdAt", user.getCreatedAt());
                        profileData.put("updatedAt", user.getUpdatedAt());

                        return ResponseEntity.ok(new ApiResponse(true,
                                        "User profile retrieved successfully", profileData));

                } catch (Exception e) {
                        return ResponseEntity.badRequest()
                                        .body(new ApiResponse(false,
                                                        "Error retrieving user profile: " + e.getMessage()));
                }
        }

        /**
         * Obtener configuración de seguridad del usuario
         */
        @GetMapping("/security-settings")
        @PreAuthorize("hasRole('USER')")
        public ResponseEntity<?> getSecuritySettings(@CurrentUser UserPrincipal userPrincipal) {
                try {
                        User user = userService.getUserById(userPrincipal.getId());

                        Map<String, Object> securityData = new HashMap<>();
                        securityData.put("twoFactorEnabled",
                                        user.getTwoFactorEnabled() != null ? user.getTwoFactorEnabled() : false);
                        securityData.put("googleAuthEnabled",
                                        user.getGoogleAuthEnabled() != null ? user.getGoogleAuthEnabled() : false);
                        securityData.put("emailEnabled",
                                        user.getEmailEnabled() != null ? user.getEmailEnabled() : false);
                        securityData.put("backupCodesEnabled",
                                        user.getBackupCodesEnabled() != null ? user.getBackupCodesEnabled() : false);
                        securityData.put("twoFactorType",
                                        user.getTwoFactorType() != null ? user.getTwoFactorType().toString() : null);

                        return ResponseEntity.ok(new ApiResponse(true,
                                        "Security settings retrieved successfully", securityData));

                } catch (Exception e) {
                        return ResponseEntity.badRequest()
                                        .body(new ApiResponse(false,
                                                        "Error retrieving security settings: " + e.getMessage()));
                }
        }

        /**
         * Cambiar contraseña para usuarios autenticados
         */
        @PostMapping("/change-password")
        @PreAuthorize("hasRole('USER')")
        public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                        @CurrentUser UserPrincipal userPrincipal) {
                try {
                        // Obtener el usuario actual
                        User currentUser = userService.getUserById(userPrincipal.getId());

                        // Verificar la contraseña actual
                        if (!passwordEncoder.matches(request.getCurrentPassword(), currentUser.getPassword())) {
                                return ResponseEntity.badRequest()
                                                .body(new ApiResponse(false, "La contraseña actual es incorrecta"));
                        }

                        // Verificar que la nueva contraseña cumpla los requisitos
                        if (request.getNewPassword() == null || request.getNewPassword().trim().length() < 8) {
                                return ResponseEntity.badRequest().body(new ApiResponse(false,
                                                "La nueva contraseña debe tener al menos 8 caracteres"));
                        }

                        // Verificar que la nueva contraseña no sea igual a la actual
                        if (passwordEncoder.matches(request.getNewPassword(), currentUser.getPassword())) {
                                return ResponseEntity.badRequest().body(new ApiResponse(false,
                                                "La nueva contraseña debe ser diferente a la actual"));
                        }

                        // Actualizar la contraseña
                        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
                        userService.save(currentUser);

                        return ResponseEntity.ok(new ApiResponse(true, "Contraseña actualizada correctamente"));

                } catch (Exception e) {
                        System.err.println("Error en change-password: " + e.getMessage());
                        e.printStackTrace();
                        return ResponseEntity.status(500)
                                        .body(new ApiResponse(false, "Error interno del servidor"));
                }
        }
}