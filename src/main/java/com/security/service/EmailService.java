package com.security.service;

import com.security.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // Configuraciones para servicios alternativos
    @Value("${app.email.resend.api-key:#{null}}")
    private String resendApiKey;

    @Value("${app.email.mailgun.api-key:#{null}}")
    private String mailgunApiKey;

    @Value("${app.email.mailgun.domain:#{null}}")
    private String mailgunDomain;

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
        System.out.println("📧 Iniciando envío de código 2FA por email...");

        // Estrategia 1: Intentar con Resend (más confiable en Railway)
        if (sendWithResend(user, code)) {
            return;
        }

        // Estrategia 2: Intentar con Mailgun
        if (sendWithMailgun(user, code)) {
            return;
        }

        // Estrategia 3: Fallback a JavaMail (probablemente fallará en Railway)
        sendWithJavaMail(user, code);
    }

    private boolean sendWithResend(User user, String code) {
        // DEBUG: Verificar qué está recibiendo la variable
        System.out.println("🔍 DEBUG RESEND: resendApiKey = '" + resendApiKey + "'");
        System.out.println("🔍 DEBUG RESEND: is null? " + (resendApiKey == null));
        System.out.println("🔍 DEBUG RESEND: is empty? " + (resendApiKey != null && resendApiKey.trim().isEmpty()));
        System.out.println("🔍 DEBUG RESEND: is placeholder? "
                + (resendApiKey != null && resendApiKey.equals("re_demo_key_placeholder")));

        if (resendApiKey == null || resendApiKey.trim().isEmpty() || resendApiKey.equals("re_demo_key_placeholder")) {
            System.out.println("🔄 Resend API Key no configurada o es placeholder, saltando...");
            return false;
        }

        try {
            System.out.println("📨 Intentando envío con Resend API...");

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + resendApiKey);

            Map<String, Object> emailData = new HashMap<>();
            emailData.put("from", "AuthSystem <onboarding@resend.dev>");
            emailData.put("to", new String[] { user.getEmail() });
            emailData.put("subject", "Código de verificación 2FA para " + user.getEmail() + " - AuthSystem");
            emailData.put("html", build2FAEmailTemplate(user.getFirstName() + " (" + user.getEmail() + ")", code));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(emailData, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.resend.com/emails", request, String.class);
            long endTime = System.currentTimeMillis();

            System.out.println(
                    "📊 Resend Response - Status: " + response.getStatusCode() + ", Body: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Código 2FA enviado exitosamente via Resend a: " + user.getEmail() +
                        " (tiempo: " + (endTime - startTime) + "ms)");
                return true;
            } else {
                System.err.println(
                        "❌ Error Resend - Status: " + response.getStatusCode() + ", Body: " + response.getBody());
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Error con Resend: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean sendWithMailgun(User user, String code) {
        if (mailgunApiKey == null || mailgunApiKey.trim().isEmpty() ||
                mailgunDomain == null || mailgunDomain.trim().isEmpty()) {
            System.out.println("🔄 Mailgun no configurado, saltando...");
            return false;
        }

        try {
            System.out.println("📨 Intentando envío con Mailgun...");

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth("api", mailgunApiKey);

            String body = "from=AuthSystem <noreply@" + mailgunDomain + ">" +
                    "&to=" + user.getEmail() +
                    "&subject=Código de verificación 2FA - AuthSystem" +
                    "&html=" + java.net.URLEncoder.encode(build2FAEmailTemplate(user.getFirstName(), code), "UTF-8");

            HttpEntity<String> request = new HttpEntity<>(body, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.mailgun.net/v3/" + mailgunDomain + "/messages", request, String.class);
            long endTime = System.currentTimeMillis();

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Código 2FA enviado exitosamente via Mailgun a: " + user.getEmail() +
                        " (tiempo: " + (endTime - startTime) + "ms)");
                return true;
            } else {
                System.err.println("❌ Error Mailgun - Status: " + response.getStatusCode());
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Error con Mailgun: " + e.getMessage());
            return false;
        }
    }

    private void sendWithJavaMail(User user, String code) {
        try {
            System.out.println("📨 Intentando envío con JavaMail (puede fallar en Railway)...");

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject("Código de verificación 2FA - AuthSystem");

            String htmlContent = build2FAEmailTemplate(user.getFirstName(), code);
            helper.setText(htmlContent, true);

            long startTime = System.currentTimeMillis();
            mailSender.send(message);
            long endTime = System.currentTimeMillis();

            System.out.println("✅ Código 2FA enviado exitosamente via JavaMail a: " + user.getEmail() +
                    " (tiempo: " + (endTime - startTime) + "ms)");

        } catch (Exception e) {
            System.err.println("❌ Error enviando código 2FA a " + user.getEmail() + ": " + e.getMessage());
            System.err.println("⚠️  NOTA: Railway bloquea conexiones SMTP directas.");
            System.err.println("💡 SOLUCIÓN: Configura Resend o Mailgun API Keys para envío de emails.");

            throw new RuntimeException(
                    "Error al enviar código 2FA por email. Configura un proveedor de email alternativo (Resend/Mailgun) o usa SMS.",
                    e);
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