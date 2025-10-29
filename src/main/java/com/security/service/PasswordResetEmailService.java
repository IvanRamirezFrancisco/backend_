package com.security.service;

import com.security.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class PasswordResetEmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Enviar email de reset de contraseña
     */
    public boolean sendPasswordResetEmail(User user, String token) {
        try {
            String resetLink = frontendUrl + "/reset-password?token=" + token;
            String subject = "🔐 Restablece tu contraseña - Sistema de Login";
            String htmlContent = buildPasswordResetEmailContent(user, resetLink);
            return sendHtmlEmail(user.getEmail(), subject, htmlContent);
        } catch (Exception e) {
            System.err.println("Error enviando email de reset: " + e.getMessage());
            return false;
        }
    }

    /**
     * Enviar notificación de contraseña cambiada
     */
    public boolean sendPasswordChangedNotification(User user) {
        try {
            String subject = "✅ Contraseña actualizada - Sistema de Login";
            String htmlContent = buildPasswordChangedEmailContent(user);

            return sendHtmlEmail(user.getEmail(), subject, htmlContent);

        } catch (Exception e) {
            System.err.println("Error enviando notificación de cambio: " + e.getMessage());
            return false;
        }
    }

    /**
     * Método privado para enviar emails HTML
     */
    public boolean sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            System.err.println("Error enviando email HTML: " + e.getMessage());
            return false;
        }
    }

    /**
     * Construir contenido HTML para email de reset
     */
    private String buildPasswordResetEmailContent(User user, String resetLink) {
        return """
                    <html>
                    <body>
                        <h2>Solicitud de restablecimiento de contraseña</h2>
                        <p>Hola %s,</p>
                        <p>Haz clic en el siguiente enlace para restablecer tu contraseña:</p>
                        <a href="%s">Restablecer contraseña</a>
                        <p>Si no solicitaste este cambio, ignora este mensaje.</p>
                    </body>
                    </html>
                """.formatted(user.getFirstName(), resetLink);
    }

    /**
     * Construir contenido HTML para notificación de contraseña cambiada
     */
    private String buildPasswordChangedEmailContent(User user) {
        String currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <style>
                                body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; }
                                .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                                .header { background: linear-gradient(135deg, #28a745 0%, #20c997 100%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                                .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                                .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                                .success { background: #d4edda; border: 1px solid #c3e6cb; padding: 15px; border-radius: 5px; margin: 20px 0; color: #155724; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <h1>✅ Contraseña Actualizada</h1>
                                </div>
                                <div class="content">
                                    <h2>Hola %s,</h2>
                                    <div class="success">
                                        <p><strong>¡Tu contraseña ha sido actualizada exitosamente!</strong></p>
                                    </div>
                                    <p>Tu contraseña se cambió el <strong>%s</strong>.</p>
                                    <p>Si no fuiste tú quien realizó este cambio, contacta inmediatamente con soporte.</p>
                                    <p>Por tu seguridad, te recomendamos:</p>
                                    <ul>
                                        <li>Usar una contraseña única y segura</li>
                                        <li>No compartir tus credenciales</li>
                                        <li>Cerrar sesión en dispositivos que no uses</li>
                                    </ul>
                                </div>
                                <div class="footer">
                                    <p>Este email fue enviado automáticamente. No respondas a este mensaje.</p>
                                    <p>© 2024 Sistema de Login. Todos los derechos reservados.</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                user.getFirstName(), currentDate);
    }
}