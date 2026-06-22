package com.security.service;

import com.security.entity.User;
import com.security.enums.TwoFactorType;
import com.security.repository.UserRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TwoFactorService {
    private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);

    @Autowired
    private UserService userService;

    @Autowired
    private TotpService totpService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private BackupCodeService backupCodeService;

    // Cache temporal para códigos de email (en producción usar Redis)
    private final Map<Long, String> emailCodes = new HashMap<>();
    private final Map<Long, LocalDateTime> emailCodeExpiry = new HashMap<>();

    private static final SecureRandom secureRandom = new SecureRandom();

    // ===== GOOGLE AUTHENTICATOR (TOTP) =====

    /**
     * Setup completo de Google Authenticator.
     * Usa repositorio directo para evitar conflictos JPA/transaccional.
     */
    public Map<String, Object> setupGoogleAuthenticatorComplete(Long userId) {
        log.debug("Iniciando setup Google Authenticator para usuario ID {}", userId);

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            if (user.getGoogleAuthEnabled() != null && user.getGoogleAuthEnabled()) {
                throw new RuntimeException("Google Authenticator ya está habilitado para este usuario");
            }

            String secret = totpService.generateSecretKey();
            String qrCodeBase64 = totpService.generateQRCodeBase64(secret, user.getEmail());

            try {
                user.setGoogleAuthSecret(secret);
                user.setTwoFactorType(TwoFactorType.GOOGLE_AUTHENTICATOR);
                user.setGoogleAuthEnabled(false); // Se activa después de confirmación
                userRepository.save(user);
                log.debug("Secret de Google Authenticator guardado en BD para usuario ID {}", userId);
            } catch (Exception saveException) {
                log.error("Error guardando configuración Google Authenticator para usuario ID {}: {}",
                        userId, saveException.getMessage(), saveException);
                throw new RuntimeException("Error guardando configuración: " + saveException.getMessage());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("secret", secret);
            result.put("manualEntryKey", secret);
            result.put("qrCode", "data:image/png;base64," + qrCodeBase64);
            result.put("issuer", "AuthSystem");
            result.put("accountName", user.getEmail());
            result.put("instructions",
                    "1. Escanea el QR con Google Authenticator\n2. O ingresa la clave manual\n3. Confirma con un código de 6 dígitos");
            result.put("nextStep", "Usa POST /api/two-factor/google/confirm con el código generado");
            result.put("setupTime", System.currentTimeMillis());

            log.info("Setup Google Authenticator completado para usuario ID {}", userId);
            return result;

        } catch (Exception e) {
            log.error("Error en setup Google Authenticator para usuario ID {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Error configurando Google Authenticator: " + e.getMessage());
        }
    }

    /**
     * Habilita Google Authenticator y devuelve solo el secret.
     */
    public String enableGoogleAuthenticator(Long userId) {
        try {
            log.debug("Habilitando Google Auth para usuario ID {}", userId);
            Map<String, Object> setup = setupGoogleAuthenticatorComplete(userId);
            return (String) setup.get("secret");
        } catch (Exception e) {
            log.error("Error habilitando Google Authenticator para usuario ID {}: {}", userId, e.getMessage());
            throw new RuntimeException("Error habilitando Google Authenticator: " + e.getMessage());
        }
    }

    /**
     * Genera QR Code usando el secret exacto de la BD.
     */
    @Transactional(readOnly = true)
    public String generateQRCode(Long userId) {
        User user = userService.getUserById(userId);

        if (user.getGoogleAuthSecret() == null || user.getGoogleAuthSecret().isEmpty()) {
            throw new RuntimeException("Please enable Google Authenticator first by calling /api/2fa/google/enable");
        }

        try {
            log.debug("Generando QR Code para usuario ID {}", userId);
            String secretFromDB = user.getGoogleAuthSecret();
            String qrCodeBase64 = totpService.generateQRCodeBase64(secretFromDB, user.getEmail());
            log.debug("QR Code generado exitosamente para usuario ID {}", userId);
            return qrCodeBase64;
        } catch (Exception e) {
            log.error("Error generando QR code para usuario ID {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Error generating QR code: " + e.getMessage(), e);
        }
    }

    /**
     * Verifica código y activa Google Authenticator.
     * Usa repositorio directo para evitar conflictos JPA.
     */
    public boolean confirmGoogleAuthenticator(Long userId, String code) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            if (user.getGoogleAuthSecret() == null || user.getGoogleAuthSecret().isEmpty()) {
                throw new RuntimeException("Google Authenticator no configurado. Ejecuta /setup primero.");
            }

            log.debug("Confirmando Google Authenticator para usuario ID {}", userId);
            boolean isValid = totpService.verifyCode(user.getGoogleAuthSecret(), code);

            if (isValid) {
                try {
                    user.setTwoFactorEnabled(true);
                    user.setGoogleAuthEnabled(true);
                    userRepository.save(user);
                    log.info("Google Authenticator activado para usuario ID {}", userId);
                    return true;
                } catch (Exception saveError) {
                    log.error("Error guardando activación de Google Auth para usuario ID {}: {}",
                            userId, saveError.getMessage(), saveError);
                    throw new RuntimeException("Error activando Google Authenticator: " + saveError.getMessage());
                }
            } else {
                log.warn("Código Google Authenticator inválido para usuario ID {}", userId);
                return false;
            }

        } catch (Exception e) {
            log.error("Error confirmando Google Authenticator para usuario ID {}: {}", userId, e.getMessage());
            throw new RuntimeException("Error confirmando Google Authenticator: " + e.getMessage());
        }
    }

    /**
     * Verificación durante login — NO actualiza la base de datos.
     */
    public boolean verifyGoogleAuthenticatorForLogin(Long userId, String code) {
        User user = userService.getUserById(userId);

        if (user.getGoogleAuthSecret() == null || !user.getGoogleAuthEnabled()) {
            log.warn("Intento de verificar Google Auth no habilitado para usuario ID {}", userId);
            return false;
        }

        log.debug("Verificando Google Auth en login para usuario ID {}", userId);
        boolean isValid = totpService.verifyCode(user.getGoogleAuthSecret(), code);
        log.debug("Resultado verificación Google Auth para usuario ID {}: {}", userId, isValid);
        return isValid;
    }

    // ===== EMAIL 2FA =====

    public void sendEmailCode(Long userId) {
        User user = userService.getUserById(userId);

        // Genera código de 6 dígitos
        String code = String.format("%06d", secureRandom.nextInt(1000000));

        // Almacena con expiración de 5 minutos
        emailCodes.put(userId, code);
        emailCodeExpiry.put(userId, LocalDateTime.now().plusMinutes(5));

        emailService.send2FACodeEmail(user, code);
    }

    public boolean verifyEmailCode(Long userId, String code) {
        String storedCode = emailCodes.get(userId);
        LocalDateTime expiry = emailCodeExpiry.get(userId);

        if (storedCode == null || expiry == null) {
            return false;
        }

        if (LocalDateTime.now().isAfter(expiry)) {
            emailCodes.remove(userId);
            emailCodeExpiry.remove(userId);
            return false;
        }

        boolean isValid = storedCode.equals(code);

        if (isValid) {
            emailCodes.remove(userId);
            emailCodeExpiry.remove(userId);
        }

        return isValid;
    }

    public boolean verifyToken(Long userId, String token) {
        User user = userService.getUserById(userId);

        if (!user.getTwoFactorEnabled()) {
            return false;
        }

        if (user.getTwoFactorType() == null) {
            return false;
        }

        switch (user.getTwoFactorType()) {
            case GOOGLE_AUTHENTICATOR:
                return totpService.verifyCode(user.getGoogleAuthSecret(), token);
            case EMAIL:
                return verifyEmailCode(userId, token);
            default:
                return false;
        }
    }

    public void disableTwoFactor(Long userId) {
        User user = userService.getUserById(userId);
        user.setTwoFactorEnabled(false);
        user.setGoogleAuthSecret(null);
        user.setGoogleAuthEnabled(false);
        user.setEmailEnabled(false);
        user.setTwoFactorType(null);
        userService.save(user);

        emailCodes.remove(userId);
        emailCodeExpiry.remove(userId);
    }

    // ===== MÉTODOS LEGACY (para compatibilidad) =====

    public String generateSecret() {
        return totpService.generateSecretKey();
    }

    public String generateToken(Long userId) {
        String token = String.format("%06d", secureRandom.nextInt(1000000));
        emailCodes.put(userId, token);
        emailCodeExpiry.put(userId, LocalDateTime.now().plusMinutes(5));
        return token;
    }

    public void enableTwoFactor(Long userId) {
        String secret = generateSecret();
        User user = userService.getUserById(userId);
        user.setGoogleAuthSecret(secret);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorType(TwoFactorType.GOOGLE_AUTHENTICATOR);
        userService.save(user);
    }

    public void enableEmailTwoFactor(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setEmailEnabled(true);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorType(TwoFactorType.EMAIL);
        userRepository.save(user);

        log.info("Email 2FA habilitado para usuario ID {}", userId);
    }

    public String getQRCodeUrl(Long userId, String issuer) {
        User user = userService.getUserById(userId);
        if (user.getGoogleAuthSecret() == null) {
            throw new RuntimeException("Two-factor authentication is not enabled for this user");
        }
        return totpService.generateQRCodeImageUri(user.getGoogleAuthSecret(), user.getEmail());
    }

    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        emailCodes.entrySet().removeIf(entry -> {
            Long userId = entry.getKey();
            LocalDateTime expiry = emailCodeExpiry.get(userId);
            return expiry == null || now.isAfter(expiry);
        });
        emailCodeExpiry.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }

    public void cleanupOldUsedTokens() {
        cleanupExpiredTokens();
    }

    // ===== MÉTODOS ESPECÍFICOS PARA MÚLTIPLES 2FA =====

    /**
     * Desactivar método específico de 2FA
     */
    public void disableSpecificTwoFactor(Long userId, String method) {
        User user = userService.getUserById(userId);
        boolean wasDisabled = false;

        switch (method.toUpperCase()) {
            case "GOOGLE":
            case "GOOGLE_AUTHENTICATOR":
                if (user.getGoogleAuthEnabled() != null && user.getGoogleAuthEnabled()) {
                    user.setGoogleAuthEnabled(false);
                    user.setGoogleAuthSecret(null);
                    wasDisabled = true;
                }
                break;
            case "EMAIL":
                if (user.getEmailEnabled() != null && user.getEmailEnabled()) {
                    user.setEmailEnabled(false);
                    wasDisabled = true;
                    emailCodes.remove(userId);
                    emailCodeExpiry.remove(userId);
                }
                break;
            default:
                throw new RuntimeException("Invalid 2FA method: " + method);
        }

        if (!wasDisabled) {
            throw new RuntimeException(method + " two-factor authentication is not enabled");
        }

        if (!hasAnyTwoFactorEnabled(user)) {
            user.setTwoFactorEnabled(false);
            user.setTwoFactorType(null);
        }

        userService.save(user);
    }

    /**
     * Verificar si el usuario tiene algún método 2FA activo
     */
    private boolean hasAnyTwoFactorEnabled(User user) {
        return (user.getGoogleAuthEnabled() != null && user.getGoogleAuthEnabled()) ||
                (user.getEmailEnabled() != null && user.getEmailEnabled());
    }

    /**
     * Obtener métodos 2FA disponibles para el usuario
     */
    public Map<String, Boolean> getAvailableTwoFactorMethods(Long userId) {
        User user = userService.getUserById(userId);
        Map<String, Boolean> methods = new HashMap<>();

        methods.put("GOOGLE_AUTHENTICATOR", user.getGoogleAuthEnabled() != null && user.getGoogleAuthEnabled());
        methods.put("EMAIL", user.getEmailEnabled() != null && user.getEmailEnabled());
        methods.put("BACKUP_CODES", user.getBackupCodesEnabled() != null && user.getBackupCodesEnabled());

        return methods;
    }

    // ===== BACKUP CODES =====

    public boolean verifyBackupCode(Long userId, String code) {
        return backupCodeService.verifyBackupCode(userId, code);
    }

    public List<String> generateBackupCodes(Long userId) {
        return backupCodeService.generateBackupCodes(userId);
    }

    public Map<String, Object> getBackupCodeStats(Long userId) {
        return backupCodeService.getBackupCodeStats(userId);
    }

    public Map<String, String> generateQRCodeWithSecret(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            String secret = user.getGoogleAuthSecret();
            if (secret == null || secret.isEmpty()) {
                throw new RuntimeException("No hay secreto configurado para el usuario");
            }

            String qrCodeImage = totpService.generateQRCodeBase64(secret, user.getEmail());

            Map<String, String> result = new HashMap<>();
            result.put("qrCode", "data:image/png;base64," + qrCodeImage);
            result.put("secret", secret);
            result.put("manualEntryKey", secret);

            return result;

        } catch (Exception e) {
            throw new RuntimeException("Error al generar código QR: " + e.getMessage(), e);
        }
    }

    public String getOtpAuthUrlForDebug(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            String secret = user.getGoogleAuthSecret();
            if (secret == null || secret.isEmpty()) {
                throw new RuntimeException("No hay secreto configurado para el usuario");
            }

            return totpService.generateQRCodeImageUri(secret, user.getEmail());

        } catch (Exception e) {
            throw new RuntimeException("Error al obtener URL OTP: " + e.getMessage(), e);
        }
    }

    /**
     * Genera un código TOTP válido actual (solo para debugging/testing).
     */
    public String generateCurrentValidCode(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            String secret = user.getGoogleAuthSecret();
            if (secret == null || secret.isEmpty()) {
                throw new RuntimeException("No hay secreto configurado para el usuario");
            }

            log.debug("Generando código TOTP de testing para usuario ID {}", userId);
            return totpService.generateCurrentValidCode(secret);

        } catch (Exception e) {
            throw new RuntimeException("Error al generar código actual: " + e.getMessage(), e);
        }
    }

    /**
     * Valida todo el flujo TOTP del usuario.
     */
    public Map<String, Object> validateCompleteTotp(Long userId) {
        try {
            User user = userService.getUserById(userId);
            Map<String, Object> validation = new HashMap<>();

            log.debug("Validación completa TOTP para usuario ID {}", userId);

            String secret = user.getGoogleAuthSecret();
            boolean hasSecret = secret != null && !secret.isEmpty();
            validation.put("hasSecret", hasSecret);

            if (hasSecret) {
                validation.put("secretLength", secret.length());
                validation.put("secretPreview", secret.substring(0, Math.min(4, secret.length())) + "...");

                String otpAuthUrl = totpService.generateOtpAuthUrl(secret, user.getEmail());
                validation.put("otpAuthUrl", otpAuthUrl);

                String currentCode = totpService.generateCurrentValidCode(secret);
                validation.put("currentValidCode", currentCode);

                boolean selfValidation = totpService.verifyCode(secret, currentCode);
                validation.put("selfValidation", selfValidation);

                log.debug("Validación TOTP usuario ID {} — secretOk={}, selfValidation={}", userId, hasSecret, selfValidation);
            }

            validation.put("isEnabled", user.getGoogleAuthEnabled());
            validation.put("isConfigured", user.getTwoFactorEnabled());

            return validation;

        } catch (Exception e) {
            log.error("Error en validación completa TOTP para usuario ID {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Error validating TOTP flow: " + e.getMessage(), e);
        }
    }
}