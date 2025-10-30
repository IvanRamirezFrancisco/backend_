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
import java.util.Map;

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
            String secret = twoFactorService.enableGoogleAuthenticator(userPrincipal.getId());
            return ResponseEntity.ok(new ApiResponse(true,
                    "Google Authenticator setup initiated. Get QR code from /api/2fa/google/qrcode",
                    Map.of("secret", secret)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/google/qrcode")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getQRCode(@CurrentUser UserPrincipal userPrincipal) {
        try {
            String qrCodeBase64 = twoFactorService.generateQRCode(userPrincipal.getId());
            return ResponseEntity.ok(new ApiResponse(true,
                    "QR Code generated successfully",
                    Map.of("qrCode", "data:image/png;base64," + qrCodeBase64)));
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
                if (user.getEmailEnabled() == null || !user.getEmailEnabled()) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "Email 2FA is not enabled for this user"));
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
                // CORREGIDO: Usar el método que existe en TwoFactorService
                isValid = twoFactorService.confirmGoogleAuthenticator(user.getId(), code);
            } else if ("EMAIL".equals(method)) {
                // CORREGIDO: Usar el método que existe en TwoFactorService
                isValid = twoFactorService.verifyEmailCode(user.getId(), code);
            } else if ("SMS".equals(method)) {
                // NUEVO: Verificación por SMS
                isValid = twoFactorService.verifySmsCode(user.getId(), code);
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false,
                                "Invalid verification method. Supported: GOOGLE_AUTHENTICATOR, EMAIL, SMS"));
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
                        .body(new ApiResponse(false, "Verification code is required"));
            }

            boolean isValid = twoFactorService.confirmGoogleAuthenticator(userPrincipal.getId(), code);

            if (isValid) {
                return ResponseEntity.ok(new ApiResponse(true,
                        "Google Authenticator enabled successfully!"));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid verification code"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
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
}