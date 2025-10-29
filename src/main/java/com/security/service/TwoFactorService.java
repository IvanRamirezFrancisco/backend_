package com.security.service;

import com.security.entity.User;
import com.security.enums.TwoFactorType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.security.entity.User;
import com.security.repository.UserRepository;

import com.security.repository.UserRepository;

@Service
@Transactional
public class TwoFactorService {

    @Autowired
    private UserService userService;

    @Autowired
    private TotpService totpService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SmsService smsService;

    // Cache temporal para códigos de email/SMS (en producción usar Redis)
    private final Map<Long, String> emailCodes = new HashMap<>();
    private final Map<Long, LocalDateTime> emailCodeExpiry = new HashMap<>();

    private static final SecureRandom secureRandom = new SecureRandom();

    // ===== GOOGLE AUTHENTICATOR (TOTP) =====

    public String enableGoogleAuthenticator(Long userId) {
        User user = userService.getUserById(userId);

        // CORRECCIÓN: Solo verificar si Google Auth ya está activo
        if (user.getGoogleAuthEnabled() != null && user.getGoogleAuthEnabled()) {
            throw new RuntimeException("Google Authenticator is already enabled");
        }

        // Generate secret
        String secret = totpService.generateSecretKey();

        // Save secret to user (but don't enable 2FA yet)
        user.setGoogleAuthSecret(secret);
        user.setTwoFactorType(TwoFactorType.GOOGLE_AUTHENTICATOR);
        userService.save(user);

        return secret;
    }

    public String generateQRCode(Long userId) {
        User user = userService.getUserById(userId);

        if (user.getGoogleAuthSecret() == null) {
            throw new RuntimeException("Please enable Google Authenticator first");
        }

        try {
            return totpService.generateQRCodeBase64(user.getGoogleAuthSecret(), user.getEmail());
        } catch (Exception e) {
            throw new RuntimeException("Error generating QR code", e);
        }
    }

    public boolean confirmGoogleAuthenticator(Long userId, String code) {
        User user = userService.getUserById(userId);

        if (user.getGoogleAuthSecret() == null) {
            throw new RuntimeException("Google Authenticator not set up");
        }

        // boolean isValid = totpService.verifyCode(user.getTwoFactorSecret(), code);
        boolean isValid = totpService.verifyCode(user.getGoogleAuthSecret(), code);

        if (isValid) {
            // Enable Google Auth after successful verification
            user.setTwoFactorEnabled(true);
            user.setGoogleAuthEnabled(true);
            userService.save(user);
            return true;
        }

        return false;
    }

    // ===== EMAIL 2FA =====

    public void sendEmailCode(Long userId) {
        User user = userService.getUserById(userId);

        // Generate 6-digit code
        String code = String.format("%06d", secureRandom.nextInt(1000000));

        // Store with expiry (5 minutes)
        emailCodes.put(userId, code);
        emailCodeExpiry.put(userId, LocalDateTime.now().plusMinutes(5));

        // Send email
        emailService.send2FACodeEmail(user, code);
    }

    public boolean verifyEmailCode(Long userId, String code) {
        String storedCode = emailCodes.get(userId);
        LocalDateTime expiry = emailCodeExpiry.get(userId);

        if (storedCode == null || expiry == null) {
            return false;
        }

        if (LocalDateTime.now().isAfter(expiry)) {
            // Cleanup expired
            emailCodes.remove(userId);
            emailCodeExpiry.remove(userId);
            return false;
        }

        boolean isValid = storedCode.equals(code);

        if (isValid) {
            // Cleanup after successful verification
            emailCodes.remove(userId);
            emailCodeExpiry.remove(userId);
        }

        return isValid;
    }

    // ===== SMS 2FA =====

    public void enableSmsTwoFactor(Long userId, String phoneNumber) {
        User user = userService.getUserById(userId);

        // CORRECCIÓN: Solo verificar si SMS ya está activo, no otros métodos
        if (user.getSmsEnabled() != null && user.getSmsEnabled()) {
            throw new RuntimeException("SMS two-factor authentication is already enabled");
        }

        // Validar formato del número
        if (!smsService.isValidPhoneNumber(phoneNumber)) {
            throw new RuntimeException("Invalid phone number format. Use format: +1234567890");
        }

        // Normalizar y guardar el número de teléfono
        String normalizedPhone = smsService.normalizePhoneNumber(phoneNumber);
        user.setPhone(normalizedPhone);
        user.setTwoFactorType(TwoFactorType.SMS);
        userService.save(user);

        // Enviar código de verificación inicial
        smsService.sendVerificationCode(user, normalizedPhone);
    }

    public boolean confirmSmsTwoFactor(Long userId, String code) {
        User user = userService.getUserById(userId);

        if (user.getPhone() == null || user.getPhone().trim().isEmpty()) {
            throw new RuntimeException("No phone number configured for SMS 2FA");
        }

        boolean isValid = smsService.verifyCode(user, user.getPhone(), code);

        if (isValid) {
            // Habilitar 2FA después de verificación exitosa
            user.setTwoFactorEnabled(true);
            user.setSmsEnabled(true);
            userService.save(user);
            return true;
        }

        return false;
    }

    public void sendSmsCode(Long userId) {
        User user = userService.getUserById(userId);

        if (user.getSmsEnabled() == null || !user.getSmsEnabled() || user.getPhone() == null) {
            throw new RuntimeException("SMS 2FA is not enabled for this user");
        }

        // Enviar código al número guardado del usuario
        smsService.sendLoginVerificationCode(user);
    }

    public boolean verifySmsCode(Long userId, String code) {
        User user = userService.getUserById(userId);

        if (user.getSmsEnabled() == null || !user.getSmsEnabled()) {
            return false;
        }

        return smsService.verifyLoginCode(user, code);
    }

    // ===== VERIFICATION METHODS =====

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
            case SMS:
                return verifySmsCode(userId, token);
            default:
                return false;
        }
    }

    public void disableTwoFactor(Long userId) {
        User user = userService.getUserById(userId);
        user.setTwoFactorEnabled(false);
        user.setGoogleAuthSecret(null);
        user.setGoogleAuthEnabled(false);
        user.setSmsEnabled(false);
        user.setEmailEnabled(false);
        user.setTwoFactorType(null);
        userService.save(user);

        // Cleanup any pending codes
        emailCodes.remove(userId);
        emailCodeExpiry.remove(userId);

        // Cleanup SMS codes
        smsService.cleanupExpiredCodes();
    }

    // ===== MÉTODOS LEGACY (para compatibilidad) =====

    public String generateSecret() {
        return totpService.generateSecretKey();
    }

    public String generateToken(Long userId) {
        // Generate 6-digit token for email 2FA
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

        // Habilitar Email 2FA
        user.setEmailEnabled(true);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorType(TwoFactorType.EMAIL);

        // Guardar cambios
        userRepository.save(user);

        // Log para debugging
        System.out.println("Email 2FA enabled for user: " + user.getEmail());
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
        // For in-memory implementation, this is handled automatically
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
            case "SMS":
                if (user.getSmsEnabled() != null && user.getSmsEnabled()) {
                    user.setSmsEnabled(false);
                    wasDisabled = true;
                }
                break;
            case "EMAIL":
                if (user.getEmailEnabled() != null && user.getEmailEnabled()) {
                    user.setEmailEnabled(false);
                    wasDisabled = true;
                    // Limpiar códigos pendientes
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

        // Si no quedan métodos 2FA activos, desactivar el flag global
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
                (user.getSmsEnabled() != null && user.getSmsEnabled()) ||
                (user.getEmailEnabled() != null && user.getEmailEnabled());
    }

    /**
     * Obtener métodos 2FA disponibles para el usuario
     */
    public Map<String, Boolean> getAvailableTwoFactorMethods(Long userId) {
        User user = userService.getUserById(userId);
        Map<String, Boolean> methods = new HashMap<>();

        methods.put("GOOGLE_AUTHENTICATOR", user.getGoogleAuthEnabled() != null && user.getGoogleAuthEnabled());
        methods.put("SMS", user.getSmsEnabled() != null && user.getSmsEnabled());
        methods.put("EMAIL", user.getEmailEnabled() != null && user.getEmailEnabled());

        return methods;
    }
}