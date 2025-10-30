package com.security.service;

import com.security.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendVerificationEmail(User user, String verificationToken) {
        try {

            // 🔴 AGREGAR ESTE DEBUG TEMPORAL
            System.out.println("🔧 DEBUG EMAIL CONFIG:");
            System.out.println("📧 Username: " + fromEmail);
            System.out.println("🌐 Base URL: " + baseUrl);
            System.out.println("📤 Para: " + user.getEmail());
            System.out.println("🔑 Token: " + verificationToken);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject("Verificación de cuenta - AuthSystem");

            // 🔴 CAMBIAR ESTA LÍNEA - USAR ENDPOINT DEL BACKEND
            String verificationUrl = "http://localhost:8080/api/auth/verify?token=" + verificationToken;

            String htmlContent = buildEmailTemplate(user.getFirstName(), verificationUrl, verificationToken);
            helper.setText(htmlContent, true);

            System.out.println("📨 Enviando email...");
            // ENVÍO REAL (descomenta cuando tengas las variables configuradas)
            mailSender.send(message);
            System.out.println("✅ Email enviado exitosamente a: " + user.getEmail());
            System.out.println("✅ Email enviado exitosamente a: " + user.getEmail());

            // SIMULACIÓN (comenta cuando quieras envío real)
            /*
             * System.out.println("📧 ========== EMAIL SIMULADO ==========");
             * System.out.println("📤 Para: " + user.getEmail());
             * System.out.println("🔗 URL: " + verificationUrl);
             * System.out.println("🔑 Token: " + verificationToken);
             * System.out.println("📧 =======================================");
             */

        } catch (MessagingException e) {
            System.err.println("❌ Error enviando email a " + user.getEmail() + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al enviar email de verificación", e);
        }
    }

    private String buildEmailTemplate(String userName, String verificationUrl, String token) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        .container { max-width: 600px; margin: 0 auto; font-family: Arial, sans-serif; }
                        .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }
                        .content { padding: 20px; }
                        .button {
                            background-color: #4CAF50;
                            color: white;
                            padding: 12px 25px;
                            text-decoration: none;
                            border-radius: 5px;
                            display: inline-block;
                            margin: 20px 0;
                        }
                        .code {
                            background-color: #f1f1f1;
                            padding: 10px;
                            font-family: monospace;
                            font-size: 18px;
                            text-align: center;
                            margin: 20px 0;
                            border-radius: 5px;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>¡Bienvenido %s!</h1>
                        </div>
                        <div class="content">
                            <h2>Verifica tu cuenta</h2>
                            <p>Gracias por registrarte. Para completar tu registro, por favor verifica tu dirección de email.</p>

                            <h3>Opción 1: Click en el enlace</h3>
                            <a href="%s" class="button">Verificar Email</a>

                            <h3>Opción 2: Usa este código</h3>
                            <div class="code">%s</div>
                            <p>Copia y pega este código en la aplicación para verificar tu cuenta.</p>

                            <p><strong>Este enlace y código expiran en 24 horas.</strong></p>

                            <p>Si no creaste esta cuenta, puedes ignorar este email.</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(userName, verificationUrl, token);
    }

    public void sendPasswordResetEmail(User user, String token) {
        System.out.println("📧 PASSWORD RESET EMAIL para: " + user.getEmail());
        System.out.println("🔑 Token: " + token);
    }

    public void send2FACodeEmail(User user, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject("Código de verificación 2FA - AuthSystem");

            String htmlContent = build2FAEmailTemplate(user.getFirstName(), code);
            helper.setText(htmlContent, true);

            System.out.println("📧 Enviando código 2FA por email...");

            // Usar un timeout más corto para evitar bloqueos largos
            long startTime = System.currentTimeMillis();
            mailSender.send(message);
            long endTime = System.currentTimeMillis();

            System.out.println("✅ Código 2FA enviado exitosamente a: " + user.getEmail() + " (tiempo: "
                    + (endTime - startTime) + "ms)");

        } catch (Exception e) {
            System.err.println("❌ Error enviando código 2FA a " + user.getEmail() + ": " + e.getMessage());
            System.err.println("⚠️  NOTA: Railway puede bloquear conexiones SMTP. Usa SMS como alternativa.");

            // Log más específico del error
            if (e.getMessage().contains("Connection timed out")) {
                System.err.println("💡 SUGERENCIA: El servidor está bloqueando conexiones SMTP. Usa SMS en su lugar.");
            }

            throw new RuntimeException("Error al enviar código 2FA por email: " + e.getMessage(), e);
        }
    }

    private String build2FAEmailTemplate(String userName, String code) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        .container { max-width: 600px; margin: 0 auto; font-family: Arial, sans-serif; }
                        .header { background-color: #667eea; color: white; padding: 20px; text-align: center; }
                        .content { padding: 20px; text-align: center; }
                        .code {
                            background-color: #f1f1f1;
                            padding: 15px;
                            font-family: monospace;
                            font-size: 24px;
                            font-weight: bold;
                            text-align: center;
                            margin: 20px 0;
                            border-radius: 5px;
                            border: 2px solid #667eea;
                            color: #333;
                        }
                        .warning {
                            color: #e74c3c;
                            font-size: 14px;
                            margin-top: 20px;
                        }
                        .footer {
                            margin-top: 30px;
                            padding-top: 20px;
                            border-top: 1px solid #eee;
                            color: #666;
                            font-size: 12px;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>🔐 Código de Verificación</h1>
                        </div>
                        <div class="content">
                            <h2>Hola %s</h2>
                            <p>Tu código de verificación de dos factores es:</p>

                            <div class="code">%s</div>

                            <p>Este código expirará en <strong>5 minutos</strong>.</p>

                            <div class="warning">
                                <strong>⚠️ Importante:</strong><br>
                                Si no solicitaste este código, ignora este email.<br>
                                Nunca compartas este código con nadie.
                            </div>
                        </div>
                        <div class="footer">
                            <p>Este email fue enviado automáticamente por AuthSystem.</p>
                            <p>© 2025 AuthSystem. Todos los derechos reservados.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(userName, code);
    }
}