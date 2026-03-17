package com.security.service;

import com.security.entity.User;
import com.security.util.LogSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    // ── Dependencias ────────────────────────────────────────────────────────────
    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private RestTemplate restTemplate;

    // ── Configuración base ──────────────────────────────────────────────────────
    @Value("${app.base-url}")
    private String baseUrl;

    /** Nombre visible del remitente, configurable por entorno */
    @Value("${app.email.sender-name:AuthSystem}")
    private String senderName;

    /**
     * Dirección "From" verificada en Brevo.
     * Debe ser un remitente verificado en la cuenta Brevo (no el usuario SMTP técnico).
     * Se usa tanto en la API REST como en el fallback SMTP.
     */
    @Value("${app.email.sender-address:${spring.mail.username}}")
    private String senderAddress;

    // ── API Keys de proveedores ─────────────────────────────────────────────────
    @Value("${app.email.brevo.api-key:#{null}}")
    private String brevoApiKey;

    @Value("${app.email.resend.api-key:#{null}}")
    private String resendApiKey;

    @Value("${app.email.mailgun.api-key:#{null}}")
    private String mailgunApiKey;

    @Value("${app.email.mailgun.domain:#{null}}")
    private String mailgunDomain;

    // ── Constantes ──────────────────────────────────────────────────────────────
    private static final String BREVO_API_URL  = "https://api.brevo.com/v3/smtp/email";
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    // ============================================================================
    //  API PÚBLICA
    // ============================================================================

    /**
     * Envía el correo de verificación de cuenta al usuario recién registrado.
     * Orden de prioridad: Brevo API → Resend API → SMTP JavaMail (fallback).
     */
    public void sendVerificationEmail(User user, String verificationToken) {
        logger.info("Iniciando envío de email de verificación a: {}", LogSanitizer.maskEmail(user.getEmail()));

        String verificationUrl = baseUrl + "/verify-account?token=" + verificationToken;
        String subject         = "Verificación de cuenta - AuthSystem";
        String html            = buildVerificationTemplate(user.getFirstName(), verificationUrl);

        if (sendViaBrevoApi(user, subject, html))  return;
        if (sendViaResendApi(user, subject, html)) return;
        sendViaSmtp(user, subject, html);
    }

    /**
     * Envía el correo de recuperación de contraseña.
     * Orden de prioridad: Brevo API → Resend API → SMTP JavaMail (fallback).
     */
    public void sendPasswordResetEmail(User user, String token) {
        logger.info("Iniciando envío de email de reseteo a: {}", LogSanitizer.maskEmail(user.getEmail()));

        String frontendUrl = normalizeBaseUrl(baseUrl);
        String resetUrl    = frontendUrl + "/reset-password?token=" + token;
        String subject     = "Recuperación de contraseña - AuthSystem";
        String html        = buildPasswordResetTemplate(user.getFirstName(), resetUrl);

        if (sendViaBrevoApi(user, subject, html))  return;
        if (sendViaResendApi(user, subject, html)) return;
        sendViaSmtp(user, subject, html);
    }

    /**
     * Envía el código 2FA por email.
     * Orden de prioridad: Brevo API → Resend API → SMTP JavaMail (fallback).
     */
    public void send2FACodeEmail(User user, String code) {
        logger.info("Iniciando envío de código 2FA a: {}", LogSanitizer.maskEmail(user.getEmail()));

        String subject = "Código de verificación 2FA - AuthSystem";
        String html    = build2FATemplate(user.getFirstName(), code);

        if (sendViaBrevoApi(user, subject, html))  return;
        if (sendViaResendApi(user, subject, html)) return;
        sendViaSmtp(user, subject, html);
    }

    /**
     * Envía un email HTML genérico a una dirección, usando la cadena de proveedores.
     * Útil para notificaciones que no requieren objeto User completo.
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        User stub = new User();
        stub.setEmail(to);
        stub.setFirstName("");
        if (sendViaBrevoApi(stub, subject, htmlContent))  return;
        if (sendViaResendApi(stub, subject, htmlContent)) return;
        sendViaSmtp(stub, subject, htmlContent);
    }

    // ============================================================================
    //  PROVEEDORES DE ENVÍO (privados)
    // ============================================================================

    /**
     * Envía un email usando la API HTTP de Brevo (v3).
     * No usa SMTP, por lo que no está sujeto a bloqueos de puerto 587 en Railway.
     */
    private boolean sendViaBrevoApi(User user, String subject, String htmlContent) {
        if (!hasValue(brevoApiKey)) {
            logger.debug("Brevo API Key no configurada, omitiendo proveedor Brevo.");
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("sender",      Map.of("name", senderName, "email", senderAddress));
            body.put("to",          new Map[]{ Map.of("email", user.getEmail(), "name", user.getFirstName()) });
            body.put("subject",     subject);
            body.put("htmlContent", htmlContent);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    BREVO_API_URL, new HttpEntity<>(body, headers), String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Email enviado via Brevo API a: {} | Respuesta: {}",
                        LogSanitizer.maskEmail(user.getEmail()), response.getBody());
                return true;
            }
            logger.warn("Brevo API respondió con status no exitoso: {} | Body: {}",
                    response.getStatusCode(), response.getBody());
            return false;

        } catch (HttpClientErrorException e) {
            logger.warn("Brevo API error de cliente ({}) | Body: {}. Intentando siguiente proveedor.",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            logger.warn("Error con Brevo API: {}. Intentando siguiente proveedor.", e.getMessage());
            return false;
        }
    }

    /**
     * Envía un email usando la API HTTP de Resend.
     */
    private boolean sendViaResendApi(User user, String subject, String htmlContent) {
        if (!hasValue(resendApiKey)) {
            logger.debug("Resend API Key no configurada, omitiendo proveedor Resend.");
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("from",    senderName + " <" + senderAddress + ">");
            body.put("to",      new String[]{ user.getEmail() });
            body.put("subject", subject);
            body.put("html",    htmlContent);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    RESEND_API_URL, new HttpEntity<>(body, headers), String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Email enviado via Resend API a: {}", LogSanitizer.maskEmail(user.getEmail()));
                return true;
            }
            logger.warn("Resend API respondió con status no exitoso: {}", response.getStatusCode());
            return false;

        } catch (HttpClientErrorException e) {
            logger.warn("Resend API error de cliente ({}). Intentando siguiente proveedor.", e.getStatusCode());
            return false;
        } catch (Exception e) {
            logger.warn("Error con Resend API: {}. Intentando siguiente proveedor.", e.getMessage());
            return false;
        }
    }

    /**
     * Fallback final: envío via JavaMail/SMTP (Brevo SMTP relay).
     * En Railway puede estar bloqueado el puerto 587; si falla, lanza excepción.
     */
    private void sendViaSmtp(User user, String subject, String htmlContent) {
        logger.info("Intentando envío via SMTP para: {}", LogSanitizer.maskEmail(user.getEmail()));
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderAddress, senderName);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("Email enviado via SMTP a: {}", LogSanitizer.maskEmail(user.getEmail()));
        } catch (Exception e) {
            logger.error("Todos los proveedores de email fallaron para: {}",
                    LogSanitizer.maskEmail(user.getEmail()), e);
            throw new RuntimeException(
                    "No se pudo enviar el email. Por favor inténtalo de nuevo más tarde.", e);
        }
    }

    // ============================================================================
    //  UTILIDADES PRIVADAS
    // ============================================================================

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) return "";
        String result = url.trim();
        if (!result.startsWith("http://") && !result.startsWith("https://")) {
            result = "https://" + result;
        }
        if (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    // ============================================================================
    //  TEMPLATES HTML
    // ============================================================================

    private String buildVerificationTemplate(String userName, String verificationUrl) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8">
                    <style>
                        body { margin:0; padding:0; background:#f4f4f4; font-family:Arial,sans-serif; }
                        .container { max-width:600px; margin:40px auto; background:#fff; border-radius:8px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,.1); }
                        .header { background:#4CAF50; color:#fff; padding:28px 20px; text-align:center; }
                        .header h1 { margin:0; font-size:24px; }
                        .content { padding:32px 28px; color:#333; }
                        .btn { display:inline-block; background:#4CAF50; color:#fff; padding:14px 32px; border-radius:6px; text-decoration:none; font-weight:bold; margin:24px 0; }
                        .notice { background:#f9f9f9; border-left:4px solid #4CAF50; padding:12px 16px; font-size:13px; color:#555; border-radius:4px; }
                        .footer { padding:16px 28px; font-size:12px; color:#888; border-top:1px solid #eee; text-align:center; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header"><h1>¡Bienvenido/a, %s!</h1></div>
                        <div class="content">
                            <h2>Verifica tu correo electrónico</h2>
                            <p>Gracias por registrarte en AuthSystem. Para activar tu cuenta, haz clic en el botón de abajo:</p>
                            <a href="%s" class="btn">Verificar mi cuenta</a>
                            <div class="notice">
                                <strong>⏰ Este enlace expira en 24 horas.</strong><br>
                                Si no creaste esta cuenta, puedes ignorar este correo con seguridad.
                            </div>
                        </div>
                        <div class="footer">© 2025 AuthSystem · Este mensaje fue generado automáticamente.</div>
                    </div>
                </body>
                </html>
                """.formatted(userName, verificationUrl);
    }

    private String buildPasswordResetTemplate(String userName, String resetUrl) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8">
                    <style>
                        body { margin:0; padding:0; background:#f4f4f4; font-family:Arial,sans-serif; }
                        .container { max-width:600px; margin:40px auto; background:#fff; border-radius:8px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,.1); }
                        .header { background:#e74c3c; color:#fff; padding:28px 20px; text-align:center; }
                        .header h1 { margin:0; font-size:24px; }
                        .content { padding:32px 28px; color:#333; text-align:center; }
                        .btn { display:inline-block; background:#e74c3c; color:#fff; padding:14px 32px; border-radius:6px; text-decoration:none; font-weight:bold; margin:24px 0; }
                        .warning { background:#fff5f5; border-left:4px solid #e74c3c; padding:12px 16px; font-size:13px; color:#555; border-radius:4px; text-align:left; }
                        .footer { padding:16px 28px; font-size:12px; color:#888; border-top:1px solid #eee; text-align:center; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header"><h1>🔒 Recuperación de contraseña</h1></div>
                        <div class="content">
                            <h2>Hola, %s</h2>
                            <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta.</p>
                            <a href="%s" class="btn">Restablecer contraseña</a>
                            <p><strong>Este enlace expira en 1 hora.</strong></p>
                            <div class="warning">
                                ⚠️ Si no solicitaste este restablecimiento, ignora este correo.<br>
                                Tu contraseña actual permanece sin cambios.<br>
                                Nunca compartas este enlace con nadie.
                            </div>
                        </div>
                        <div class="footer">© 2025 AuthSystem · Este mensaje fue generado automáticamente.</div>
                    </div>
                </body>
                </html>
                """.formatted(userName, resetUrl);
    }

    private String build2FATemplate(String userName, String code) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8">
                    <style>
                        body { margin:0; padding:0; background:#f4f4f4; font-family:Arial,sans-serif; }
                        .container { max-width:600px; margin:40px auto; background:#fff; border-radius:8px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,.1); }
                        .header { background:#667eea; color:#fff; padding:28px 20px; text-align:center; }
                        .header h1 { margin:0; font-size:24px; }
                        .content { padding:32px 28px; color:#333; text-align:center; }
                        .code { background:#f1f1f1; border:2px solid #667eea; border-radius:8px; padding:18px; font-family:monospace; font-size:32px; font-weight:bold; letter-spacing:8px; color:#333; margin:24px 0; }
                        .warning { background:#f9f9f9; border-left:4px solid #667eea; padding:12px 16px; font-size:13px; color:#555; border-radius:4px; text-align:left; }
                        .footer { padding:16px 28px; font-size:12px; color:#888; border-top:1px solid #eee; text-align:center; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header"><h1>🔐 Código de verificación</h1></div>
                        <div class="content">
                            <h2>Hola, %s</h2>
                            <p>Tu código de verificación de dos factores es:</p>
                            <div class="code">%s</div>
                            <p>Este código expira en <strong>5 minutos</strong>.</p>
                            <div class="warning">
                                ⚠️ Si no solicitaste este código, ignora este correo.<br>
                                Nunca compartas este código con nadie.
                            </div>
                        </div>
                        <div class="footer">© 2025 AuthSystem · Este mensaje fue generado automáticamente.</div>
                    </div>
                </body>
                </html>
                """.formatted(userName, code);
    }
}
