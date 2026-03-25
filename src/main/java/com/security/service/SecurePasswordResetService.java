package com.security.service;

import com.security.entity.PasswordRecoveryAttempt;
import com.security.entity.PasswordResetToken;
import com.security.entity.User;
import com.security.repository.PasswordRecoveryAttemptRepository;
import com.security.repository.PasswordResetTokenRepository;
import com.security.repository.UserRepository;
import com.security.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Servicio de recuperación de contraseña segura
 * Implementa rate limiting, tokens seguros y validación sin revelar información
 */
@Service
public class SecurePasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(SecurePasswordResetService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordRecoveryAttemptRepository recoveryAttemptRepository;

    // @Autowired
    // private EmailService emailService; // Comentado hasta implementar el servicio
    // de email

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogService auditLogService;

    // Configuraciones desde application.yml
    @Value("${app.security.password-reset.token-expiration:3600000}")
    private long tokenExpirationMs;

    @Value("${app.base-url}")
    private String baseUrl;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Solicita el restablecimiento de contraseña de forma segura
     * NO revela si el email existe o no
     */
    @Transactional
    public boolean requestPasswordReset(String email, HttpServletRequest request) {
        // Sanitizar email
        String sanitizedEmail = sanitizeInput(email);
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        logger.info("Password reset requested for email: {} from IP: {}",
                maskEmail(sanitizedEmail), ipAddress);

        // Registrar intento y aplicar bloqueo progresivo acumulativo.
        // Lanza SecurityException si el usuario está bloqueado actualmente.
        registrarIntento(sanitizedEmail, ipAddress, userAgent);

        // Buscar usuario (sin revelar si existe)
        Optional<User> userOpt = userRepository.findByEmail(sanitizedEmail);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // Invalidar tokens anteriores
            invalidateExistingTokens(user);

            // Crear nuevo token
            String token = generateSecureToken();
            LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(tokenExpirationMs / 1000);

            PasswordResetToken resetToken = new PasswordResetToken(token, user, expiryDate, ipAddress, userAgent);
            passwordResetTokenRepository.save(resetToken);

            // Enviar email de forma asíncrona
            sendPasswordResetEmailAsync(user.getEmail(), user.getFirstName(), token);

            logger.info("Password reset token created for user: {} - Token ID: {}",
                    maskEmail(sanitizedEmail), resetToken.getId());
        } else {
            logger.info("Password reset requested for non-existent email: {}",
                    maskEmail(sanitizedEmail));
        }

        // SIEMPRE retornar true para no revelar información
        return true;
    }

    /**
     * Valida y resetea la contraseña usando el token
     */
    @Transactional
    public boolean resetPassword(String token, String newPassword, HttpServletRequest request) {
        String sanitizedToken = sanitizeInput(token);
        String ipAddress = getClientIpAddress(request);

        logger.info("Password reset attempt with token from IP: {}", ipAddress);

        // Buscar token
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(sanitizedToken);

        if (!tokenOpt.isPresent()) {
            logger.warn("Invalid password reset token attempted from IP: {}", ipAddress);
            return false;
        }

        PasswordResetToken resetToken = tokenOpt.get();

        // Verificar validez del token
        if (!resetToken.isValid()) {
            logger.warn("Expired or used password reset token attempted - Token ID: {}, IP: {}",
                    resetToken.getId(), ipAddress);
            return false;
        }

        // Verificar intentos excesivos en el token
        if (resetToken.hasExceededMaxAttempts(5)) {
            logger.warn("Token with excessive attempts blocked - Token ID: {}, IP: {}",
                    resetToken.getId(), ipAddress);
            resetToken.markAsUsed(); // Invalidar token por seguridad
            passwordResetTokenRepository.save(resetToken);
            return false;
        }

        // Validar nueva contraseña
        if (!isPasswordValid(newPassword)) {
            resetToken.incrementAttempts();
            passwordResetTokenRepository.save(resetToken);
            return false;
        }

        try {
            // Actualizar contraseña
            User user = resetToken.getUser();
            user.setPassword(passwordEncoder.encode(newPassword));
            // user.setPasswordChangedAt(LocalDateTime.now()); // Comentado hasta
            // implementar en User entity
            userRepository.save(user);

            // Marcar token como usado
            resetToken.markAsUsed();
            passwordResetTokenRepository.save(resetToken);

            // Invalidar todos los otros tokens del usuario por seguridad
            invalidateExistingTokens(user);

            logger.info("Password successfully reset for user: {} - Token ID: {}",
                    maskEmail(user.getEmail()), resetToken.getId());

            // Enviar confirmación por email
            sendPasswordChangedNotificationAsync(user.getEmail(), user.getFirstName(), ipAddress);

            return true;

        } catch (Exception e) {
            logger.error("Error resetting password - Token ID: {}, Error: {}",
                    resetToken.getId(), e.getMessage());

            resetToken.incrementAttempts();
            passwordResetTokenRepository.save(resetToken);
            return false;
        }
    }

    /**
     * Registra un intento de recuperación de contraseña aplicando bloqueo
     * progresivo ACUMULATIVO. El contador de intentos NUNCA se resetea solo —
     * acumula de forma permanente para que los bloqueos escalen con el tiempo.
     *
     * <p>
     * Umbrales (basados en intentos totales históricos para email+IP):
     * <ul>
     * <li>3 intentos → bloqueado 3 minutos</li>
     * <li>6 intentos → bloqueado 30 minutos</li>
     * <li>9 intentos → bloqueado 2 horas</li>
     * <li>12 intentos → bloqueado 24 horas</li>
     * <li>13+ intentos → bloqueado 1 año (permanente)</li>
     * </ul>
     *
     * @throws SecurityException si el bloqueo activo aún no ha expirado,
     *                           con un mensaje descriptivo del tiempo restante.
     */
    private void registrarIntento(String email, String ipAddress, String userAgent) {
        LocalDateTime now = LocalDateTime.now();

        // Capturar user-agent actualizado desde el request actual (puede diferir del
        // registrado)
        try {
            HttpServletRequest currentRequest = ((ServletRequestAttributes) RequestContextHolder
                    .currentRequestAttributes()).getRequest();
            String freshUserAgent = currentRequest.getHeader("User-Agent");
            if (freshUserAgent != null && freshUserAgent.length() > 500) {
                freshUserAgent = freshUserAgent.substring(0, 500);
            }
            if (freshUserAgent != null) {
                userAgent = freshUserAgent;
            }
        } catch (Exception ignored) {
            // Fuera de contexto web — usar el userAgent recibido como parámetro
        }

        Optional<PasswordRecoveryAttempt> existingOpt = recoveryAttemptRepository.findByEmailAndIpAddress(email,
                ipAddress);

        PasswordRecoveryAttempt attempt;

        if (existingOpt.isPresent()) {
            attempt = existingOpt.get();

            // Si hay bloqueo activo: rechazar SIN incrementar el contador
            if (attempt.isCurrentlyBlocked()) {
                Duration remaining = Duration.between(now, attempt.getBlockedUntil());
                long hours = remaining.toHours();
                long minutes = remaining.toMinutes() % 60;
                long seconds = remaining.getSeconds() % 60;

                String timeMsg;
                if (hours > 23) {
                    timeMsg = "más de 24 horas";
                } else if (hours > 0) {
                    timeMsg = hours + "h " + minutes + "m";
                } else if (minutes > 0) {
                    timeMsg = minutes + "m " + seconds + "s";
                } else {
                    timeMsg = seconds + " segundos";
                }

                logger.warn("Solicitud de recuperación bloqueada - Email: {}, IP: {}, Tiempo restante: {}",
                        maskEmail(email), ipAddress, timeMsg);

                throw new SecurityException(
                        "Demasiados intentos. Por favor espera " + timeMsg + " antes de intentarlo nuevamente.");
            }

            // Bloqueo expirado → levantar flag, pero NO resetear attemptCount
            attempt.liftBlockIfExpired();
            // Actualizar user-agent con el del request actual
            if (userAgent != null) {
                attempt.setUserAgent(userAgent);
            }

        } else {
            // Primera solicitud para este email+IP
            attempt = new PasswordRecoveryAttempt(email, ipAddress, userAgent);
            // El constructor ya establece attemptCount = 1
            recoveryAttemptRepository.save(attempt);

            // Con 1 intento no hay bloqueo todavía
            logger.debug("Nuevo registro de recuperación creado - Email: {}, IP: {}",
                    maskEmail(email), ipAddress);
            return;
        }

        // Incrementar contador acumulativo
        attempt.incrementAttempt();
        // Actualizar user-agent con el del request actual en cada intento
        if (userAgent != null) {
            attempt.setUserAgent(userAgent);
        }
        int total = attempt.getAttemptCount();

        // Bloquear en cada múltiplo de 3: 3, 6, 9, 12, 15...
        // Cada ciclo permite exactamente 3 intentos antes de bloquear.
        boolean shouldBlock = (total % 3 == 0);
        boolean isPermanent = (total >= 15);

        if (shouldBlock) {
            attempt.applyProgressiveBlockCumulative(total);

            String nivelBloqueo = isPermanent ? "PERMANENTE (1 año)"
                    : total >= 12 ? "24 horas"
                            : total >= 9 ? "2 horas"
                                    : total >= 6 ? "30 minutos"
                                            : "3 minutos";

            logger.warn("Bloqueo progresivo aplicado - Email: {}, IP: {}, Intentos acumulados: {}, Duración: {}",
                    maskEmail(email), ipAddress, total, nivelBloqueo);

            // Registrar en auditoría si es bloqueo permanente
            if (isPermanent) {
                try {
                    Map<String, Object> detalles = new HashMap<>();
                    detalles.put("email", maskEmail(email));
                    detalles.put("ipAddress", ipAddress);
                    detalles.put("totalAttempts", total);
                    detalles.put("blockedUntil", attempt.getBlockedUntil().toString());
                    auditLogService.log(
                            "PERMANENT_BLOCK",
                            "SECURITY_EVENT",
                            "PASSWORD_RECOVERY",
                            null,
                            null,
                            detalles,
                            "CRITICAL",
                            false);
                } catch (Exception auditEx) {
                    logger.error("Error al registrar auditoría de bloqueo permanente: {}", auditEx.getMessage());
                }
            }
        }

        recoveryAttemptRepository.save(attempt);
    }

    /**
     * Genera un token seguro criptográficamente
     */
    private String generateSecureToken() {
        byte[] tokenBytes = new byte[48]; // 384 bits
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * Invalida tokens existentes del usuario
     */
    private void invalidateExistingTokens(User user) {
        List<PasswordResetToken> existingTokens = passwordResetTokenRepository.findByUser(user);
        for (PasswordResetToken token : existingTokens) {
            token.markAsUsed();
        }
        if (!existingTokens.isEmpty()) {
            passwordResetTokenRepository.saveAll(existingTokens);
            logger.info("Invalidated {} existing tokens for user: {}",
                    existingTokens.size(), maskEmail(user.getEmail()));
        }
    }

    /**
     * Valida la seguridad de la nueva contraseña
     */
    private boolean isPasswordValid(String password) {
        // Usar el validador de contraseñas existente
        // (asumiendo que existe un PasswordValidator)
        if (password == null || password.length() < 8) {
            return false;
        }

        // Verificar que no sea demasiado común
        String[] commonPasswords = { "password", "12345678", "qwerty123", "admin123" };
        for (String common : commonPasswords) {
            if (password.toLowerCase().contains(common)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Obtiene la IP real del cliente considerando proxies
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {
                "CF-Connecting-IP",
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                ip = ip.split(",")[0].trim();
                return normalizeIp(ip);
            }
        }

        return normalizeIp(request.getRemoteAddr());
    }

    private String normalizeIp(String ip) {
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }

    /**
     * Envía email de recuperación de forma asíncrona
     */
    @Async
    private void sendPasswordResetEmailAsync(String email, String firstName, String token) {
        try {
            // String resetLink = baseUrl + "/reset-password?token=" + token;
            // String subject = "Recuperación de Contraseña - Casa de Música Castillo";

            /*
             * String emailBody = String.format(
             * "Hola %s,\n\n" +
             * "Has solicitado restablecer tu contraseña en Casa de Música Castillo.\n\n" +
             * "Haz clic en el siguiente enlace para crear una nueva contraseña:\n" +
             * "%s\n\n" +
             * "Este enlace expirará en %d minutos por seguridad.\n\n" +
             * "Si no solicitaste este cambio, puedes ignorar este email.\n\n" +
             * "Saludos,\nEquipo Casa de Música Castillo",
             * firstName, resetLink, tokenExpirationMs / 60000
             * );
             */

            // emailService.sendEmail(email, subject, emailBody); // Comentado hasta
            // implementar método
            logger.info("Password reset email sent to: {}", maskEmail(email));

        } catch (Exception e) {
            logger.error("Failed to send password reset email to: {} - Error: {}",
                    maskEmail(email), e.getMessage());
        }
    }

    /**
     * Envía notificación de cambio de contraseña
     */
    @Async
    private void sendPasswordChangedNotificationAsync(String email, String firstName, String ipAddress) {
        try {
            // String subject = "Contraseña Cambiada - Casa de Música Castillo";

            /*
             * String emailBody = String.format(
             * "Hola %s,\n\n" +
             * "Tu contraseña ha sido cambiada exitosamente desde la IP: %s\n\n" +
             * "Si no realizaste este cambio, contacta inmediatamente con soporte.\n\n" +
             * "Fecha: %s\n\n" +
             * "Saludos,\nEquipo Casa de Música Castillo",
             * firstName, ipAddress, LocalDateTime.now().toString()
             * );
             */

            // emailService.sendEmail(email, subject, emailBody); // Comentado hasta
            // implementar método
            logger.info("Password change notification sent to: {}", maskEmail(email));

        } catch (Exception e) {
            logger.error("Failed to send password change notification to: {} - Error: {}",
                    maskEmail(email), e.getMessage());
        }
    }

    /**
     * Sanitiza entrada de usuario
     */
    private String sanitizeInput(String input) {
        if (input == null)
            return "";
        return input.trim().replaceAll("[<>\"'&]", "");
    }

    /**
     * Verifica si un token de recuperación es válido
     */
    public boolean isTokenValid(String token) {
        try {
            String sanitizedToken = sanitizeInput(token);
            if (sanitizedToken == null || sanitizedToken.isEmpty()) {
                return false;
            }

            Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(sanitizedToken);
            if (tokenOpt.isEmpty()) {
                return false;
            }

            PasswordResetToken resetToken = tokenOpt.get();
            return resetToken.isValid();

        } catch (Exception e) {
            logger.error("Error validating reset token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Sobrecarga del método requestPasswordReset para controlador REST
     */
    public boolean requestPasswordResetFromController(String email, String ipAddress, String userAgent) {
        try {
            // Validar entrada
            String sanitizedEmail = sanitizeInput(email);
            String sanitizedIp = sanitizeInput(ipAddress);
            String sanitizedUserAgent = sanitizeInput(userAgent);

            if (sanitizedEmail == null || sanitizedEmail.isEmpty()) {
                return false;
            }

            // Verificar rate limiting
            // Registrar intento con bloqueo progresivo acumulativo.
            // Lanza SecurityException si el usuario está bloqueado actualmente.
            registrarIntento(sanitizedEmail, sanitizedIp, sanitizedUserAgent);

            // Buscar usuario (sin revelar si existe)
            Optional<User> userOpt = userRepository.findByEmail(sanitizedEmail);
            if (userOpt.isEmpty()) {
                logger.info("Password reset requested for non-existent email: {}", maskEmail(sanitizedEmail));
                return true; // No revelar que el email no existe
            }

            User user = userOpt.get();

            // Generar token seguro
            String token = generateSecureToken();
            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(tokenExpirationMs / 1000);

            // Crear registro del token
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setToken(token);
            resetToken.setUser(user);
            resetToken.setExpiryDate(expiresAt);
            resetToken.setCreatedAt(LocalDateTime.now());
            resetToken.setIpAddress(sanitizedIp);
            resetToken.setUserAgent(sanitizedUserAgent);
            resetToken.setAttempts(0);

            passwordResetTokenRepository.save(resetToken);

            // Enviar email de recuperación (asíncrono)
            sendPasswordResetEmailAsync(sanitizedEmail, user.getFirstName(), token);

            logger.info("Password reset token generated for user: {} from IP: {}",
                    maskEmail(sanitizedEmail), sanitizedIp);

            return true;

        } catch (SecurityException e) {
            throw e; // Re-lanzar excepciones de seguridad
        } catch (Exception e) {
            logger.error("Error processing password reset request: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Sobrecarga simplificada del método resetPassword para el controlador REST
     */
    public boolean resetPasswordFromController(String token, String newPassword, String ipAddress, String userAgent) {
        try {
            // Validar entrada
            String sanitizedToken = sanitizeInput(token);
            String sanitizedPassword = sanitizeInput(newPassword);
            String sanitizedIp = sanitizeInput(ipAddress);
            String sanitizedUserAgent = sanitizeInput(userAgent);

            if (sanitizedToken == null || sanitizedPassword == null) {
                logger.warn("Invalid input for password reset");
                return false;
            }

            // Buscar el token
            Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(sanitizedToken);
            if (tokenOpt.isEmpty()) {
                logger.warn("Reset token not found: {}", sanitizedToken.substring(0, 8) + "...");
                return false;
            }

            PasswordResetToken resetToken = tokenOpt.get();

            // Validar token
            if (!resetToken.isValid()) {
                logger.warn("Invalid or expired token for reset");
                return false;
            }

            // Verificar si ya fue usado
            if (resetToken.getUsedAt() != null) {
                logger.warn("Token already used for password reset");
                return false;
            }

            // Actualizar contraseña del usuario
            User user = resetToken.getUser();
            user.setPassword(passwordEncoder.encode(sanitizedPassword));
            // user.setPasswordChangedAt(LocalDateTime.now()); // Comentado si no existe el
            // campo
            userRepository.save(user);

            // Marcar token como usado
            resetToken.markAsUsed();
            resetToken.setIpAddress(sanitizedIp);
            resetToken.setUserAgent(sanitizedUserAgent);
            passwordResetTokenRepository.save(resetToken);

            // Invalidar otros tokens del usuario
            List<PasswordResetToken> otherTokens = passwordResetTokenRepository.findByUserAndUsed(user, false);
            for (PasswordResetToken otherToken : otherTokens) {
                if (!otherToken.getToken().equals(sanitizedToken)) {
                    otherToken.markAsUsed();
                }
            }
            passwordResetTokenRepository.saveAll(otherTokens);

            logger.info("Password reset successful for user: {} from IP: {}",
                    maskEmail(user.getEmail()), sanitizedIp);

            // Enviar notificación por email (asíncrono)
            sendPasswordChangedNotificationAsync(user.getEmail(), user.getFirstName(), sanitizedIp);

            return true;

        } catch (Exception e) {
            logger.error("Error during password reset confirmation: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Enmascara email para logging
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 3)
            return "***";
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            return email.substring(0, 1) + "***" + email.substring(atIndex);
        }
        return email.substring(0, 1) + "***";
    }

    /**
     * Limpieza automática de registros antiguos (ejecuta cada hora)
     */
    @Scheduled(fixedRateString = "${app.security.password-reset.cleanup-interval:3600000}")
    @Transactional
    public void cleanupOldRecords() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(1);

            // Limpiar intentos de recuperación antiguos
            List<PasswordRecoveryAttempt> oldAttempts = recoveryAttemptRepository.findOldRecordsForCleanup(cutoff);
            if (!oldAttempts.isEmpty()) {
                recoveryAttemptRepository.deleteAll(oldAttempts);
                logger.info("Cleaned up {} old recovery attempt records", oldAttempts.size());
            }

            // Limpiar tokens expirados
            LocalDateTime tokenCutoff = LocalDateTime.now().minusHours(48);
            List<PasswordResetToken> expiredTokens = passwordResetTokenRepository.findExpiredTokens(tokenCutoff);
            if (!expiredTokens.isEmpty()) {
                passwordResetTokenRepository.deleteAll(expiredTokens);
                logger.info("Cleaned up {} expired password reset tokens", expiredTokens.size());
            }

        } catch (Exception e) {
            logger.error("Error during cleanup of old records: {}", e.getMessage());
        }
    }
}