package com.security.controller;

import java.util.Optional;

import com.security.dto.response.ApiResponse;
import com.security.dto.response.JwtAuthResponse;
import com.security.dto.response.UserResponse;
import com.security.security.JwtTokenProvider;
import com.security.entity.User;
import com.security.security.CurrentUser;
import com.security.security.UserPrincipal;
import com.security.service.TwoFactorService;
import com.security.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.security.service.BackupCodeService;

@RestController
@RequestMapping("/api/2fa")
public class TwoFactorController {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorController.class);

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private UserService userService;

    // ===== GOOGLE AUTHENTICATOR =====

    @PostMapping("/google/enable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> enableGoogleAuthenticator(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            Map<String, Object> setupInfo = twoFactorService.setupGoogleAuthenticatorComplete(userPrincipal.getId());
            return ResponseEntity
                    .ok(new ApiResponse(true, "Google Authenticator setup initiated. Scan QR code.", setupInfo));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Error configurando Google Authenticator: " + e.getMessage()));
        }
    }

    @PostMapping("/google/disable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> disableGoogleAuthenticator(@CurrentUser UserPrincipal userPrincipal) {
        try {
            twoFactorService.disableSpecificTwoFactor(userPrincipal.getId(), "GOOGLE_AUTHENTICATOR");
            return ResponseEntity.ok(new ApiResponse(true,
                    "Google Authenticator desactivado exitosamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ===== EMAIL 2FA =====

    @PostMapping("/email/enable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> enableEmail2FA(@CurrentUser UserPrincipal userPrincipal) {
        try {
            twoFactorService.enableEmailTwoFactor(userPrincipal.getId());
            return ResponseEntity.ok(new ApiResponse(true,
                    "Verificación por Email activada exitosamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/email/disable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> disableEmail2FA(@CurrentUser UserPrincipal userPrincipal) {
        try {
            twoFactorService.disableSpecificTwoFactor(userPrincipal.getId(), "EMAIL");
            return ResponseEntity.ok(new ApiResponse(true,
                    "Verificación por Email desactivada exitosamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/google/setup")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> setupGoogleAuthenticator(@CurrentUser UserPrincipal userPrincipal) {
        try {
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getQRCode(@CurrentUser UserPrincipal userPrincipal) {
        try {
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
    @PreAuthorize("isAuthenticated()")
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

            Optional<User> userOptional = userService.findByEmail(email);
            if (!userOptional.isPresent()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "User not found"));
            }

            User user = userOptional.get();

            if ("EMAIL".equals(method)) {
                if (user.getEmailEnabled() == null || !user.getEmailEnabled()) {
                    log.info("Auto-habilitando Email 2FA para usuario ID {}", user.getId());
                    twoFactorService.enableEmailTwoFactor(user.getId());
                    user = userService.getUserById(user.getId());
                }
                twoFactorService.sendEmailCode(user.getId());
                return ResponseEntity.ok(new ApiResponse(true, "Email code sent successfully"));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid method. Supported: EMAIL"));
            }

        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("Connection timed out") ||
                    e.getMessage().contains("Mail server connection failed"))) {
                return ResponseEntity.status(503)
                        .body(new ApiResponse(false,
                                "Error al enviar código 2FA por email. El servidor de correo no está disponible."));
            }
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Error al enviar código 2FA: " + e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyTwoFactor(@RequestBody(required = false) Map<String, String> request,
            HttpServletRequest httpRequest) {
        String email = null;
        String method = null;

        try {
            log.debug("Inicio verificación 2FA");

            if (request == null) {
                log.warn("Request body nulo en /verify");
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Request body es requerido"));
            }

            email = request.get("email");
            String code = request.get("code");
            method = request.get("method");

            log.debug("Verificación 2FA - método: {}", method);

            if (email == null || code == null || method == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Email, código y método son requeridos"));
            }

            Optional<User> userOptional = userService.findByEmail(email);
            if (!userOptional.isPresent()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Usuario no encontrado"));
            }

            User user = userOptional.get();
            boolean isValid = false;

            if ("GOOGLE_AUTHENTICATOR".equals(method)) {
                if (user.getGoogleAuthSecret() == null || user.getGoogleAuthSecret().isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "Google Authenticator no está configurado"));
                }
                isValid = twoFactorService.verifyGoogleAuthenticatorForLogin(user.getId(), code);

            } else if ("EMAIL".equals(method)) {
                isValid = twoFactorService.verifyEmailCode(user.getId(), code);

            } else if ("BACKUP_CODE".equals(method)) {
                isValid = twoFactorService.verifyBackupCode(user.getId(), code);

            } else {
                log.warn("Método 2FA inválido recibido: {}", method);
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Método inválido"));
            }

            if (isValid) {
                log.info("Login 2FA exitoso para usuario ID {} usando método {}", user.getId(), method);
                UserPrincipal userPrincipal = UserPrincipal.create(user);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userPrincipal, null, userPrincipal.getAuthorities());

                String token = jwtTokenProvider.generateToken(authentication, httpRequest);

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

                return ResponseEntity.ok(new ApiResponse(true, "Autenticación exitosa", jwtResponse));
            } else {
                log.warn("Código 2FA inválido para usuario ID {} con método {}", user.getId(), method);
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Código de verificación inválido"));
            }

        } catch (Exception e) {
            log.error("Excepción en /verify 2FA - método: {}, error: {}", method, e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Error interno al verificar el código 2FA"));
        }
    }

    @PostMapping("/google/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> confirmGoogleAuthenticator(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {
        try {
            String code = request.get("code");
            if (code == null || code.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Código de verificación requerido"));
            }

            if (!code.matches("\\d{6}")) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "El código debe tener 6 dígitos"));
            }

            boolean isValid = twoFactorService.confirmGoogleAuthenticator(userPrincipal.getId(), code);

            if (isValid) {
                Map<String, Object> result = new HashMap<>();
                result.put("message", "Google Authenticator habilitado exitosamente!");
                result.put("googleAuthEnabled", true);
                result.put("info", "Puedes generar códigos de respaldo por separado si lo deseas desde el dashboard.");

                return ResponseEntity.ok(new ApiResponse(true,
                        "Google Authenticator habilitado exitosamente!", result));
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

    @PostMapping("/email/send")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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

    // ===== GENERAL =====

    @PostMapping("/disable")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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

    @GetMapping("/dashboard-summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDashboardSummary(@CurrentUser UserPrincipal userPrincipal) {
        try {
            User user = userService.getUserById(userPrincipal.getId());
            Map<String, Boolean> methods = twoFactorService.getAvailableTwoFactorMethods(userPrincipal.getId());

            Map<String, Object> summary = new HashMap<>();
            summary.put("methods", methods);
            summary.put("twoFactorEnabled", user.getTwoFactorEnabled() != null ? user.getTwoFactorEnabled() : false);

            Map<String, String> methodInfo = new HashMap<>();
            methodInfo.put("GOOGLE_AUTHENTICATOR", "Autenticación con app móvil (Google Authenticator, Authy, etc.)");
            methodInfo.put("SMS", "Códigos por mensaje de texto");
            methodInfo.put("EMAIL", "Códigos por correo electrónico");
            methodInfo.put("BACKUP_CODES", "Códigos de respaldo de un solo uso");
            summary.put("methodDescriptions", methodInfo);

            return ResponseEntity.ok(new ApiResponse(true,
                    "Dashboard 2FA summary retrieved", summary));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ===== DEBUGGING ENDPOINTS =====

    @GetMapping("/debug/validate-totp")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> generateBackupCodes(@CurrentUser UserPrincipal userPrincipal) {
        try {
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
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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
