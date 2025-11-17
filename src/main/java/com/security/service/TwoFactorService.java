package com.security.service;

import com.security.entity.User;
import com.security.enums.TwoFactorType;
import com.security.repository.UserRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
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

    @Autowired
    private BackupCodeService backupCodeService;

    // Cache temporal para códigos de email/SMS (en producción usar Redis)
    private final Map<Long, String> emailCodes = new HashMap<>();
    private final Map<Long, LocalDateTime> emailCodeExpiry = new HashMap<>();

    private static final SecureRandom secureRandom = new SecureRandom();

    // ===== GOOGLE AUTHENTICATOR (TOTP) =====

    /**
     * MÉTODO SIN TRANSACCIONES: Setup completo de Google Authenticator
     * RETORNA: Map con secret, QR y toda la información necesaria
     * NOTA: Sin @Transactional para evitar conflictos JPA
     */
    public Map<String, Object> setupGoogleAuthenticatorComplete(Long userId) {
        System.out.println("🚀 === SETUP GOOGLE AUTHENTICATOR (SIN TRANSACCIONES) ===");
        System.out.println("  - Usuario ID: " + userId);

        try {
            // 1. Cargar usuario usando UserService (más robusto)
            User user = userService.getUserById(userId);
            System.out.println("  - Usuario encontrado: " + user.getEmail());

            // 2. Verificar si ya está configurado
            if (user.getGoogleAuthEnabled() != null && user.getGoogleAuthEnabled()) {
                throw new RuntimeException("Google Authenticator ya está habilitado para este usuario");
            }

            // 3. Generar secret ANTES de cualquier operación de BD
            String secret = totpService.generateSecretKey();
            System.out.println("  - Secret generado: " + secret.substring(0, 4) + "... (length: " + secret.length() + ")");

            // 4. Generar QR ANTES de guardar en BD (para evitar rollback)
            String qrCodeBase64 = totpService.generateQRCodeBase64(secret, user.getEmail());
            System.out.println("  ✅ QR Code generado antes de guardar");

            // 5. Guardar en BD usando operación atómica simple
            try {
                // Usar una operación SQL directa más simple
                user.setGoogleAuthSecret(secret);
                user.setTwoFactorType(TwoFactorType.GOOGLE_AUTHENTICATOR);
                user.setGoogleAuthEnabled(false); // Se activa después de confirmación
                
                // Usar userService.save que maneja transacciones internamente
                userService.save(user);
                System.out.println("  ✅ Secret guardado en BD exitosamente");
                
            } catch (Exception saveException) {
                System.err.println("❌ Error guardando en BD: " + saveException.getMessage());
                saveException.printStackTrace();
                throw new RuntimeException("Error guardando configuración: " + saveException.getMessage());
            }

            // 6. Preparar respuesta completa (todo generado exitosamente)
            Map<String, Object> result = new HashMap<>();
            result.put("secret", secret);
            result.put("manualEntryKey", secret);
            result.put("qrCode", "data:image/png;base64," + qrCodeBase64);
            result.put("issuer", "AuthSystem");
            result.put("accountName", user.getEmail());
            result.put("instructions", "1. Escanea el QR con Google Authenticator\n2. O ingresa la clave manual\n3. Confirma con un código de 6 dígitos");
            result.put("nextStep", "Usa POST /api/two-factor/google/confirm con el código generado");
            result.put("setupTime", System.currentTimeMillis());

            System.out.println("  🎉 Setup completo exitoso - Sin errores JPA");
            return result;

        } catch (Exception e) {
            System.err.println("❌ Error en setup: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error configurando Google Authenticator: " + e.getMessage());
        }
    }

    /**
     * MÉTODO MEJORADO: Llama al setup completo y devuelve solo el secret
     * EVITA duplicación de lógica y transacciones conflictivas
     */
    public String enableGoogleAuthenticator(Long userId) {
        try {
            System.out.println("🔧 Habilitando Google Auth (delegando a setupCompleto)");
            
            // Usar el método completo que ya funciona sin transacciones
            Map<String, Object> setup = setupGoogleAuthenticatorComplete(userId);
            String secret = (String) setup.get("secret");
            
            System.out.println("✅ Secret obtenido del setup completo: " + secret.substring(0, 4) + "...");
            return secret;

        } catch (Exception e) {
            System.err.println("❌ Error habilitando: " + e.getMessage());
            throw new RuntimeException("Error habilitando Google Authenticator: " + e.getMessage());
        }
    }

    /**
     * MÉTODO CRÍTICO: Genera QR Code usando el secret EXACTO de la BD
     * FLUJO: Leer secret de BD → Usar TotpService para generar QR → Retornar base64
     */
    @Transactional(readOnly = true)
    public String generateQRCode(Long userId) {
        User user = userService.getUserById(userId);

        if (user.getGoogleAuthSecret() == null || user.getGoogleAuthSecret().isEmpty()) {
            throw new RuntimeException("Please enable Google Authenticator first by calling /api/2fa/google/enable");
        }

        try {
            System.out.println("🖼️  === GENERANDO QR CODE ===");
            System.out.println("  - Usuario ID: " + userId);
            System.out.println("  - Email: " + user.getEmail());

            // CRÍTICO: Usar el secret EXACTO guardado en la BD (sin regenerar)
            String secretFromDB = user.getGoogleAuthSecret();
            System.out.println("  - Secret de BD: " + secretFromDB.substring(0, 4) + "... (usando EXACTO de BD)");

            // Generar QR usando TotpService (única fuente de verdad para formato)
            String qrCodeBase64 = totpService.generateQRCodeBase64(secretFromDB, user.getEmail());

            System.out.println("  ✅ QR Code generado exitosamente");
            System.out.println("  📱 Este QR contiene el MISMO secret que está en la BD");

            return qrCodeBase64;
        } catch (Exception e) {
            System.err.println("❌ ERROR generando QR code para user " + userId + ": " + e.getMessage());
            throw new RuntimeException("Error generating QR code: " + e.getMessage(), e);
        }
    }

    /**
     * CONFIRMACIÓN SIN TRANSACCIONES: Verifica código y activa Google Authenticator
     */
    public boolean confirmGoogleAuthenticator(Long userId, String code) {
        try {
            // Usar UserService en lugar de Repository directo
            User user = userService.getUserById(userId);

            if (user.getGoogleAuthSecret() == null || user.getGoogleAuthSecret().isEmpty()) {
                throw new RuntimeException("Google Authenticator no configurado. Ejecuta /setup primero.");
            }

            System.out.println("🔐 === CONFIRMANDO GOOGLE AUTHENTICATOR (SIN TRANSACCIONES) ===");
            System.out.println("  - Usuario: " + user.getEmail());
            System.out.println("  - Código: " + code);

            // Verificar código TOTP
            boolean isValid = totpService.verifyCode(user.getGoogleAuthSecret(), code);

            if (isValid) {
                try {
                    // Activar 2FA usando UserService (maneja transacciones internamente)
                    user.setTwoFactorEnabled(true);
                    user.setGoogleAuthEnabled(true);
                    userService.save(user);

                    System.out.println("  ✅ Google Auth ACTIVADO exitosamente");
                    return true;
                } catch (Exception saveError) {
                    System.err.println("❌ Error guardando activación: " + saveError.getMessage());
                    throw new RuntimeException("Error activando Google Authenticator: " + saveError.getMessage());
                }
            } else {
                System.out.println("  ❌ Código inválido");
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Error confirmando: " + e.getMessage());
            throw new RuntimeException("Error confirmando Google Authenticator: " + e.getMessage());
        }
    }

    /**
     * Método SOLO para verificación durante login - NO actualiza la base de datos
     */
    public boolean verifyGoogleAuthenticatorForLogin(Long userId, String code) {
        User user = userService.getUserById(userId);

        if (user.getGoogleAuthSecret() == null || !user.getGoogleAuthEnabled()) {
            System.err.println("❌ Google Authenticator not enabled for user " + userId);
            return false;
        }

        System.out.println("🔑 Verificando Google Auth para login:");
        System.out.println("  - Usuario ID: " + userId);
        System.out.println("  - Google Auth habilitado: " + user.getGoogleAuthEnabled());
        System.out.println("  - Tiene secret: " + (user.getGoogleAuthSecret() != null));

        boolean isValid = totpService.verifyCode(user.getGoogleAuthSecret(), code);

        System.out.println("  - Resultado verificación: " + isValid);

        return isValid;
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
        methods.put("BACKUP_CODES", user.getBackupCodesEnabled() != null && user.getBackupCodesEnabled());

        return methods;
    }

    // ===== BACKUP CODES =====

    /**
     * Verificar código de respaldo usando el servicio especializado
     */
    public boolean verifyBackupCode(Long userId, String code) {
        return backupCodeService.verifyBackupCode(userId, code);
    }

    /**
     * Generar códigos de backup
     */
    public List<String> generateBackupCodes(Long userId) {
        return backupCodeService.generateBackupCodes(userId);
    }

    /**
     * Obtener estadísticas de backup codes
     */
    public Map<String, Object> getBackupCodeStats(Long userId) {
        return backupCodeService.getBackupCodeStats(userId);
    }

    /**
     * Genera el código QR y la información asociada para configurar Google
     * Authenticator
     */
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

    /**
     * Obtiene la URL OTP para debug
     */
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
     * MÉTODO DE DEBUGGING: Genera un código TOTP válido actual para testing
     */
    public String generateCurrentValidCode(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            String secret = user.getGoogleAuthSecret();
            if (secret == null || secret.isEmpty()) {
                throw new RuntimeException("No hay secreto configurado para el usuario");
            }

            // Usar TotpService para generar el código (única fuente de verdad)
            String currentCode = totpService.generateCurrentValidCode(secret);

            System.out.println("🧪 === CÓDIGO DE TESTING GENERADO ===");
            System.out.println("  - Usuario: " + user.getEmail());
            System.out.println("  - Secret: " + secret.substring(0, 4) + "...");
            System.out.println("  - Código actual: " + currentCode);
            System.out.println("  💡 Usa este código para probar en Google Authenticator");

            return currentCode;

        } catch (Exception e) {
            throw new RuntimeException("Error al generar código actual: " + e.getMessage(), e);
        }
    }

    /**
     * MÉTODO DE VALIDACIÓN COMPLETA: Valida todo el flujo TOTP
     */
    public Map<String, Object> validateCompleteTotp(Long userId) {
        try {
            User user = userService.getUserById(userId);
            Map<String, Object> validation = new HashMap<>();

            System.out.println("🔍 === VALIDACIÓN COMPLETA TOTP ===");
            System.out.println("  - Usuario: " + user.getEmail());

            // Validar secret en BD
            String secret = user.getGoogleAuthSecret();
            boolean hasSecret = secret != null && !secret.isEmpty();
            validation.put("hasSecret", hasSecret);

            if (hasSecret) {
                validation.put("secretLength", secret.length());
                validation.put("secretPreview", secret.substring(0, Math.min(4, secret.length())) + "...");

                // Generar URL otpauth
                String otpAuthUrl = totpService.generateOtpAuthUrl(secret, user.getEmail());
                validation.put("otpAuthUrl", otpAuthUrl);

                // Generar código actual
                String currentCode = totpService.generateCurrentValidCode(secret);
                validation.put("currentValidCode", currentCode);

                // Verificar que el código generado sea válido
                boolean selfValidation = totpService.verifyCode(secret, currentCode);
                validation.put("selfValidation", selfValidation);

                System.out.println("  ✅ Secret válido: " + hasSecret);
                System.out.println("  📏 Length: " + secret.length());
                System.out.println("  🔗 URL: " + otpAuthUrl);
                System.out.println("  🔢 Código actual: " + currentCode);
                System.out.println("  🔍 Auto-validación: " + selfValidation);
            }

            validation.put("isEnabled", user.getGoogleAuthEnabled());
            validation.put("isConfigured", user.getTwoFactorEnabled());

            return validation;

        } catch (Exception e) {
            System.err.println("❌ Error en validación completa: " + e.getMessage());
            throw new RuntimeException("Error validating TOTP flow: " + e.getMessage(), e);
        }
    }
}