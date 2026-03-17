package com.security.service;

import com.security.entity.User;
import com.security.util.LogSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Servicio de email especializado en flujos de recuperacion de contrasena.
 * Delega el envio real a {@link EmailService} para reutilizar la cadena
 * Brevo API -> Resend API -> SMTP y no duplicar logica de transporte.
 */
@Service
public class PasswordResetEmailService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetEmailService.class);

    @Autowired
    private EmailService emailService;

    @Value("${app.base-url:http://localhost:4200}")
    private String frontendUrl;

    // ============================================================================
    // API PUBLICA
    // ============================================================================

    /**
     * Envia el enlace de restablecimiento de contrasena al usuario.
     *
     * @return true si el envio fue exitoso, false en caso contrario.
     */
    public boolean sendPasswordResetEmail(User user, String token) {
        try {
            emailService.sendPasswordResetEmail(user, token);
            logger.info("Email de reset enviado a: {}", LogSanitizer.maskEmail(user.getEmail()));
            return true;
        } catch (Exception e) {
            logger.error("Error enviando email de reset a {}: {}",
                    LogSanitizer.maskEmail(user.getEmail()), e.getMessage());
            return false;
        }
    }

    /**
     * Envia una notificacion de confirmacion cuando la contrasena ha sido cambiada.
     *
     * @return true si el envio fue exitoso, false en caso contrario.
     */
    public boolean sendPasswordChangedNotification(User user) {
        try {
            String subject = "Contrasena actualizada - AuthSystem";
            String html = buildPasswordChangedTemplate(user);
            emailService.sendHtmlEmail(user.getEmail(), subject, html);
            logger.info("Notificacion de cambio de contrasena enviada a: {}",
                    LogSanitizer.maskEmail(user.getEmail()));
            return true;
        } catch (Exception e) {
            logger.error("Error enviando notificacion de cambio de contrasena a {}: {}",
                    LogSanitizer.maskEmail(user.getEmail()), e.getMessage());
            return false;
        }
    }

    // ============================================================================
    // TEMPLATE HTML
    // ============================================================================

    private String buildPasswordChangedTemplate(User user) {
        String currentDate = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        return "<!DOCTYPE html>" +
                "<html lang=\"es\">" +
                "<head><meta charset=\"UTF-8\">" +
                "<style>" +
                "body{margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;}" +
                ".container{max-width:600px;margin:40px auto;background:#fff;border-radius:8px;" +
                "overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.1);}" +
                ".header{background:#28a745;color:#fff;padding:28px 20px;text-align:center;}" +
                ".header h1{margin:0;font-size:22px;}" +
                ".content{padding:32px 28px;color:#333;}" +
                ".success{background:#d4edda;border-left:4px solid #28a745;padding:14px 16px;" +
                "border-radius:4px;color:#155724;margin:16px 0;}" +
                ".footer{padding:16px 28px;font-size:12px;color:#888;border-top:1px solid #eee;text-align:center;}" +
                "</style></head><body>" +
                "<div class=\"container\">" +
                "<div class=\"header\"><h1>Contrasena actualizada</h1></div>" +
                "<div class=\"content\">" +
                "<h2>Hola, " + user.getFirstName() + "</h2>" +
                "<div class=\"success\"><strong>Tu contrasena fue actualizada exitosamente el " + currentDate
                + ".</strong></div>" +
                "<p>Si no fuiste tu quien realizo este cambio, contacta a soporte de inmediato.</p>" +
                "<ul>" +
                "<li>Usa una contrasena unica para cada servicio.</li>" +
                "<li>No compartas tus credenciales con nadie.</li>" +
                "<li>Cierra sesion en dispositivos que no utilices.</li>" +
                "</ul>" +
                "</div>" +
                "<div class=\"footer\">&copy; 2025 AuthSystem &middot; Este mensaje fue generado automaticamente.</div>"
                +
                "</div></body></html>";
    }
}