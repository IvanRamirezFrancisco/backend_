package com.security.controller;

import java.util.Optional;
import java.util.stream.Collectors;

import com.security.dto.response.ApiResponse;
import com.security.dto.response.JwtAuthResponse;
import com.security.dto.response.UserResponse;
import com.security.security.JwtTokenProvider;
import com.security.entity.User;
import com.security.security.CurrentUser;
import com.security.security.UserPrincipal;
import com.security.service.TwoFactorService;
import com.security.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.security.service.BackupCodeService;

@RestController
@RequestMapping("/api/2fa")
@CrossOrigin(origins = "*")
public class TwoFactorController {

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private UserService userService;

    // ===== GOOGLE AUTHENTICATOR =====

    @PostMapping("/google/enable")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> enableGoogleAuthenticator(@CurrentUser UserPrincipal userPrincipal) {
        try {
            // Setup completo en una sola llamada
            Map<String, Object> setupData = twoFactorService.setupGoogleAuthenticatorComplete(userPrincipal.getId());
            
            return ResponseEntity.ok(new ApiResponse(true,
                    "Google Authenticator configurado exitosamente. Escanea el QR con tu app y confirma con un código de 6 dígitos.",
                    setupData));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Error configurando Google Authenticator: " + e.getMessage()));
        }
    }

    @PostMapping("/google/setup")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> setupGoogleAuthenticator(@CurrentUser UserPrincipal userPrincipal) {
        try {
            // Setup completo en una sola llamada
            Map<String, Object> setupData = twoFactorService.setupGoogleAuthenticatorComplete(userPrincipal.getId());
            
            return ResponseEntity.ok(new ApiResponse(true,
                    "Google Authenticator configurado exitosamente. Escanea el QR con tu app y confirma con un código de 6 dígitos.",
                    setupData));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Error configurando Google Authenticator: " + e.getMessage()));
        }
    }

    @GetMapping("/google/qrcode")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getQRCode(@CurrentUser UserPrincipal userPrincipal) {
        try {
            // Validar que el usuario tenga un secreto configurado
            User user = userService.getUserById(userPrincipal.getId());
            if (user.getGoogleAuthSecret() == null || user.getGoogleAuthSecret().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Google Authenticator not enabled. Please enable it first."));
            }

            String qrCodeBase64 = twoFactorService.generateQRCode(userPrincipal.getId());
            String manualCode = user.getGoogleAuthSecret();

            Map<String, String> data = new HashMap<>();
            data.put("qrCode", "data:image/png;base64," + qrCodeBase64);
            data.put("manualEntryKey", manualCode);
            data.put("issuer", "AuthSystem");
            data.put("accountName", user.getEmail());

            return ResponseEntity.ok(new ApiResponse(true, "QR Code generated successfully", data));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/google/manual-code")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getManualCode(@CurrentUser UserPrincipal userPrincipal) {
        try {
            User user = userService.getUserById(userPrincipal.getId());
            if (user.getGoogleAuthSecret() == null || user.getGoogleAuthSecret().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Google Authenticator not enabled. Please enable it first."));
            }

            Map<String, String> data = new HashMap<>();
            data.put("manualEntryKey", user.getGoogleAuthSecret());
            data.put("issuer", "AuthSystem");
            data.put("accountName", user.getEmail());

            return ResponseEntity.ok(new ApiResponse(true, "Manual code retrieved successfully", data));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ===== VERIFICACIÓN UNIVERSAL 2FA =====

    @PostMapping("/send-login-code")
    public ResponseEntity<?> sendLoginCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String method = request.get("method");

            if (email == null || method == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Email and method are required"));
            }

            // Buscar usuario por email
            Optional<User> userOptional = userService.findByEmail(email);
            if (!userOptional.isPresent()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "User not found"));
            }

            User user = userOptional.get();

            if ("SMS".equals(method)) {
                if (user.getSmsEnabled() == null || !user.getSmsEnabled()) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "SMS 2FA is not enabled for this user"));
                }
                twoFactorService.sendSmsCode(user.getId());
                return ResponseEntity.ok(new ApiResponse(true, "SMS code sent successfully"));
            } else if ("EMAIL".equals(method)) {
                // AUTO-HABILITAR Email 2FA si no está habilitado durante el login
                if (user.getEmailEnabled() == null || !user.getEmailEnabled()) {
                    System.out.println("📧 Auto-habilitando Email 2FA para login de usuario: " + user.getEmail());
                    twoFactorService.enableEmailTwoFactor(user.getId());
                    // Recargar usuario después de habilitar
                    user = userService.getUserById(user.getId());
                }
                twoFactorService.sendEmailCode(user.getId());
                return ResponseEntity.ok(new ApiResponse(true, "Email code sent successfully"));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid method. Supported: SMS, EMAIL"));
            }

        } catch (Exception e) {
            // Manejo específico para errores de email
            if (e.getMessage().contains("Connection timed out") ||
                    e.getMessage().contains("Mail server connection failed")) {
                return ResponseEntity.status(503)
                        .body(new ApiResponse(false,
                                "Error al enviar código 2FA por email. El servidor de correo no está disponible. Por favor, usa SMS como alternativa."));
            }
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Error al enviar código 2FA: " + e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyTwoFactor(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code = request.get("code");
            String method = request.get("method");

            if (email == null || code == null || method == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Email, code and method are required"));
            }

            // Buscar usuario por email
            Optional<User> userOptional = userService.findByEmail(email);
            if (!userOptional.isPresent()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "User not found"));
            }

            User user = userOptional.get();
            boolean isValid = false;

            if ("GOOGLE_AUTHENTICATOR".equals(method)) {
                // CORRECCIÓN CRÍTICA: Usar método específico para login que NO modifica BD
                isValid = twoFactorService.verifyGoogleAuthenticatorForLogin(user.getId(), code);
            } else if ("EMAIL".equals(method)) {
                // CORREGIDO: Usar el método que existe en TwoFactorService
                isValid = twoFactorService.verifyEmailCode(user.getId(), code);
            } else if ("SMS".equals(method)) {
                // NUEVO: Verificación por SMS
                isValid = twoFactorService.verifySmsCode(user.getId(), code);
            } else if ("BACKUP_CODE".equals(method)) {
                // NUEVO: Verificación por código de backup
                isValid = twoFactorService.verifyBackupCode(user.getId(), code);
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false,
                                "Invalid verification method. Supported: GOOGLE_AUTHENTICATOR, EMAIL, SMS, BACKUP_CODE"));
            }

            if (isValid) {
                // CORREGIDO: Crear UserPrincipal y Authentication
                UserPrincipal userPrincipal = UserPrincipal.create(user);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userPrincipal, null, userPrincipal.getAuthorities());
                String token = jwtTokenProvider.generateToken(authentication);

                // Crear respuesta con token
                JwtAuthResponse jwtResponse = new JwtAuthResponse();
                jwtResponse.setAccessToken(token);
                jwtResponse.setTokenType("Bearer");
                jwtResponse.setExpiresIn(86400L);

                UserResponse userResponse = new UserResponse();
                userResponse.setId(user.getId());
                userResponse.setEmail(user.getEmail());
                userResponse.setFirstName(user.getFirstName());
                userResponse.setLastName(user.getLastName());
                userResponse.setTwoFactorEnabled(user.getTwoFactorEnabled());

                jwtResponse.setUser(userResponse);
                jwtResponse.setTwoFactorRequired(false);

                return ResponseEntity.ok(new ApiResponse(true,
                        "Two-factor authentication successful", jwtResponse));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid verification code"));
            }

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }
    /////////////////////////////////////////////////

    @PostMapping("/google/confirm")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> confirmGoogleAuthenticator(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {
        try {
            String code = request.get("code");
            if (code == null || code.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Código de verificación requerido"));
            }

            // Validar formato del código (6 dígitos)
            if (!code.matches("\\d{6}")) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "El código debe tener 6 dígitos"));
            }

            boolean isValid = twoFactorService.confirmGoogleAuthenticator(userPrincipal.getId(), code);

            if (isValid) {
                // Generar códigos de backup automáticamente después de habilitar Google Auth
                try {
                    List<String> backupCodes = backupCodeService.generateBackupCodes(userPrincipal.getId());

                    Map<String, Object> result = new HashMap<>();
                    result.put("message", "Google Authenticator habilitado exitosamente!");
                    result.put("backupCodes", backupCodes);
                    result.put("backupCodesWarning",
                            "Guarda estos códigos de respaldo en un lugar seguro. Solo se pueden usar una vez.");

                    return ResponseEntity.ok(new ApiResponse(true,
                            "Google Authenticator habilitado exitosamente!", result));
                } catch (Exception backupError) {
                    // Si falla la generación de backup codes, aún consideramos exitoso el Google
                    // Auth
                    return ResponseEntity.ok(new ApiResponse(true,
                            "Google Authenticator habilitado exitosamente! (Nota: No se pudieron generar códigos de respaldo)"));
                }
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false,
                                "Código de verificación inválido. Verifica que la hora de tu dispositivo sea correcta."));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Error confirmando Google Authenticator: " + e.getMessage()));
        }
    }

    // ===== EMAIL 2FA =====

    @PostMapping("/email/enable")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> enableEmailTwoFactor(@CurrentUser UserPrincipal userPrincipal) {
        try {
            twoFactorService.enableEmailTwoFactor(userPrincipal.getId());
            return ResponseEntity.ok(new ApiResponse(true,
                    "Email 2FA enabled successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ===== EMAIL 2FA =====

    @PostMapping("/email/send")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> sendEmailCode(@CurrentUser UserPrincipal userPrincipal) {
        try {
            twoFactorService.sendEmailCode(userPrincipal.getId());
            return ResponseEntity.ok(new ApiResponse(true,
                    "Verification code sent to your email"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/email/verify")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> verifyEmailCode(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {
        try {
            String code = request.get("code");
            if (code == null || code.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Verification code is required"));
            }

            boolean isValid = twoFactorService.verifyEmailCode(userPrincipal.getId(), code);

            if (isValid) {
                return ResponseEntity.ok(new ApiResponse(true,
                        "Email verification successful!"));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid or expired verification code"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ===== SMS 2FA =====

    @PostMapping("/sms/setup/send-code")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> setupSmsAndSendCode(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {
        try {
            String phoneNumber = request.get("phoneNumber");
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Phone number is required"));
            }

            twoFactorService.enableSmsTwoFactor(userPrincipal.getId(), phoneNumber);
            return ResponseEntity.ok(new ApiResponse(true,
                    "SMS verification code sent to " + phoneNumber));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/sms/setup/verify-code")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> confirmSmsSetup(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {
        try {
            String code = request.get("code");
            if (code == null || code.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Verification code is required"));
            }

            boolean isValid = twoFactorService.confirmSmsTwoFactor(userPrincipal.getId(), code);

            if (isValid) {
                return ResponseEntity.ok(new ApiResponse(true,
                        "SMS Two-Factor Authentication enabled successfully!"));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid or expired verification code"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/sms/send")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> sendSmsCode(@CurrentUser UserPrincipal userPrincipal) {
        try {
            twoFactorService.sendSmsCode(userPrincipal.getId());
            return ResponseEntity.ok(new ApiResponse(true,
                    "SMS verification code sent to your phone"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/sms/verify")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> verifySmsCode(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {
        try {
            String code = request.get("code");
            if (code == null || code.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Verification code is required"));
            }

            boolean isValid = twoFactorService.verifySmsCode(userPrincipal.getId(), code);

            if (isValid) {
                return ResponseEntity.ok(new ApiResponse(true,
                        "SMS verification successful!"));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid or expired verification code"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ===== GENERAL =====

    @PostMapping("/disable")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> disableTwoFactor(@CurrentUser UserPrincipal userPrincipal) {
        try {
            twoFactorService.disableTwoFactor(userPrincipal.getId());
            return ResponseEntity.ok(new ApiResponse(true,
                    "Two-factor authentication disabled successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getTwoFactorStatus(@CurrentUser UserPrincipal userPrincipal) {
        try {
            User user = userService.getUserById(userPrincipal.getId());

            Map<String, Object> status = new HashMap<>();
            status.put("enabled", user.getTwoFactorEnabled() != null ? user.getTwoFactorEnabled() : false);
            status.put("type", user.getTwoFactorType() != null ? user.getTwoFactorType().toString() : "none");
            status.put("hasSecret", user.getTwoFactorSecret() != null);

            return ResponseEntity.ok(new ApiResponse(true,
                    "Two-factor status retrieved", status));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ===== NUEVOS ENDPOINTS PARA MÚLTIPLES MÉTODOS 2FA =====

    @PostMapping("/disable/{method}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> disableSpecificTwoFactor(
            @PathVariable String method,
            @CurrentUser UserPrincipal userPrincipal) {
        try {
            twoFactorService.disableSpecificTwoFactor(userPrincipal.getId(), method);
            return ResponseEntity.ok(new ApiResponse(true,
                    method + " two-factor authentication disabled successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/methods")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getAvailableTwoFactorMethods(@CurrentUser UserPrincipal userPrincipal) {
        try {
            Map<String, Boolean> methods = twoFactorService.getAvailableTwoFactorMethods(userPrincipal.getId());
            return ResponseEntity.ok(new ApiResponse(true,
                    "Available 2FA methods retrieved", methods));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ===== DEBUGGING ENDPOINTS =====

    @GetMapping("/debug/validate-totp")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> validateTotpFlow(@CurrentUser UserPrincipal userPrincipal) {
        try {
            Map<String, Object> validation = twoFactorService.validateCompleteTotp(userPrincipal.getId());
            return ResponseEntity.ok(new ApiResponse(true,
                    "TOTP validation completed", validation));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/debug/generate-test-code")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> generateTestCode(@CurrentUser UserPrincipal userPrincipal) {
        try {
            String currentCode = twoFactorService.generateCurrentValidCode(userPrincipal.getId());

            Map<String, String> data = new HashMap<>();
            data.put("currentCode", currentCode);
            data.put("instructions", "Use this code within 30 seconds to test TOTP verification");
            data.put("timestamp", String.valueOf(System.currentTimeMillis()));

            return ResponseEntity.ok(new ApiResponse(true,
                    "Test code generated successfully", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @Autowired
    private BackupCodeService backupCodeService;

    // ===== BACKUP CODES =====

    @GetMapping("/backup-codes/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getBackupCodesStatus(@CurrentUser UserPrincipal userPrincipal) {
        try {
            Map<String, Object> status = backupCodeService.getBackupCodeStats(userPrincipal.getId());
            return ResponseEntity.ok(new ApiResponse(true,
                    "Backup codes status retrieved", status));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/backup-codes/generate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> generateBackupCodes(@CurrentUser UserPrincipal userPrincipal) {
        try {
            // Generar códigos de backup usando el servicio especializado
            List<String> backupCodes = backupCodeService.generateBackupCodes(userPrincipal.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("codes", backupCodes);
            result.put("count", backupCodes.size());
            result.put("createdAt", System.currentTimeMillis());
            result.put("warning",
                    "Guarda estos códigos en un lugar seguro. No se pueden recuperar y solo se pueden usar una vez.");
            result.put("instructions",
                    "Cada código solo se puede usar una vez. Guárdalos en un lugar seguro y accesible.");

            return ResponseEntity.ok(new ApiResponse(true,
                    "Backup codes generated successfully. Save them securely!", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/backup-codes/verify")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> verifyBackupCode(@CurrentUser UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {
        try {
            String code = request.get("code");
            if (code == null || code.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Backup code is required"));
            }

            boolean isValid = backupCodeService.verifyBackupCode(userPrincipal.getId(), code);

            if (isValid) {
                // Obtener estadísticas actualizadas
                Map<String, Object> stats = backupCodeService.getBackupCodeStats(userPrincipal.getId());

                Map<String, Object> result = new HashMap<>();
                result.put("valid", true);
                result.put("remainingCodes", stats.get("available"));

                if ((Long) stats.get("available") == 0) {
                    result.put("warning",
                            "¡Este era tu último código de backup! Genera nuevos códigos inmediatamente.");
                } else if ((Long) stats.get("available") <= 2) {
                    result.put("warning", "Te quedan pocos códigos de backup. Considera generar nuevos.");
                }

                return ResponseEntity.ok(new ApiResponse(true,
                        "Backup code verified successfully", result));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid or already used backup code"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/backup-codes/disable")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> disableBackupCodes(@CurrentUser UserPrincipal userPrincipal) {
        try {
            backupCodeService.disableBackupCodes(userPrincipal.getId());
            return ResponseEntity.ok(new ApiResponse(true,
                    "Backup codes disabled successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }
}