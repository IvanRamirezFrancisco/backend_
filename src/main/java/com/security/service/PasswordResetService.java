package com.security.service;

import com.security.entity.PasswordResetToken;
import com.security.entity.User;
import com.security.repository.PasswordResetTokenRepository;
import com.security.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@Transactional
public class PasswordResetService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Solicitar reset de contraseña
     */
    public boolean requestPasswordReset(String email) {
        try {
            // 1. Verificar si el usuario existe
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                // Por seguridad, no revelamos si el email existe o no
                // Pero internamente registramos que no se encontró
                System.out.println("Intento de reset para email no registrado: " + email);
                return false;
            }

            User user = userOpt.get();

            // 2. Invalidar tokens anteriores del usuario
            passwordResetTokenRepository.deleteAllByUser(user);

            // 3. Generar nuevo token seguro
            String token = generateSecureToken();

            // 4. Crear y guardar el token
            PasswordResetToken resetToken = new PasswordResetToken(token, user);
            passwordResetTokenRepository.save(resetToken);

            // 5. Enviar email usando el servicio con Brevo API
            try {
                emailService.sendPasswordResetEmail(user, token);
                System.out.println("Token de reset generado y email enviado para: " + email);
            } catch (Exception emailError) {
                System.err.println(
                        "Token generado pero error enviando email para: " + email + " - " + emailError.getMessage());
            }

            return true;

        } catch (Exception e) {
            System.err.println("Error al procesar solicitud de reset: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Validar token de reset
     */
    public boolean validateResetToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return false;
            }

            Optional<PasswordResetToken> resetTokenOpt = passwordResetTokenRepository.findByTokenAndUsedFalse(token);

            if (resetTokenOpt.isEmpty()) {
                System.out.println("Token no encontrado o ya usado: " + token);
                return false;
            }

            PasswordResetToken resetToken = resetTokenOpt.get();
            boolean isExpired = resetToken.isExpired();

            if (isExpired) {
                System.out.println("Token expirado: " + token);
                return false;
            }

            System.out.println("Token válido: " + token);
            return true;

        } catch (Exception e) {
            System.err.println("Error validando token: " + e.getMessage());
            return false;
        }
    }

    /**
     * Resetear contraseña con token
     */
    public boolean resetPassword(String token, String newPassword) {
        try {
            // 1. Validar parámetros
            if (token == null || token.trim().isEmpty()) {
                System.err.println("Token vacío o nulo");
                return false;
            }

            if (newPassword == null || newPassword.trim().length() < 8) {
                System.err.println("Contraseña inválida");
                return false;
            }

            // 2. Buscar token válido
            Optional<PasswordResetToken> resetTokenOpt = passwordResetTokenRepository.findByTokenAndUsedFalse(token);

            if (resetTokenOpt.isEmpty()) {
                System.err.println("Token no encontrado o ya usado");
                return false;
            }

            PasswordResetToken resetToken = resetTokenOpt.get();

            // 3. Verificar que no haya expirado
            if (resetToken.isExpired()) {
                System.err.println("Token expirado");
                return false;
            }

            // 4. Actualizar contraseña del usuario
            User user = resetToken.getUser();
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);

            // 5. Marcar token como usado
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);

            // 6. Enviar email de confirmación (opcional - la funcionalidad principal ya
            // funciona)
            try {
                // Note: We could implement a password changed notification method in
                // EmailService if needed
                System.out.println("Contraseña actualizada exitosamente para: " + user.getEmail());
            } catch (Exception emailError) {
                System.err.println("Contraseña actualizada pero error enviando notificación para: " + user.getEmail()
                        + " - " + emailError.getMessage());
            }

            return true;

        } catch (Exception e) {
            System.err.println("Error al resetear contraseña: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Generar token seguro
     */
    private String generateSecureToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * Obtener información del token (para debugging)
     */
    public Optional<User> getUserByToken(String token) {
        try {
            Optional<PasswordResetToken> resetTokenOpt = passwordResetTokenRepository.findByTokenAndUsedFalse(token);

            if (resetTokenOpt.isPresent()) {
                return Optional.of(resetTokenOpt.get().getUser());
            }

            return Optional.empty();
        } catch (Exception e) {
            System.err.println("Error obteniendo usuario por token: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Limpiar tokens expirados (método de mantenimiento)
     */
    public void cleanupExpiredTokens() {
        try {
            passwordResetTokenRepository.deleteExpiredTokens(LocalDateTime.now());
            System.out.println("Tokens expirados limpiados correctamente");
        } catch (Exception e) {
            System.err.println("Error limpiando tokens expirados: " + e.getMessage());
        }
    }

    /**
     * Contar tokens activos de un usuario
     */
    public long getActiveTokensCount(String email) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository
                        .findValidTokenByUser(userOpt.get(), LocalDateTime.now());
                return tokenOpt.isPresent() ? 1 : 0;
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Error contando tokens activos: " + e.getMessage());
            return 0;
        }
    }
}