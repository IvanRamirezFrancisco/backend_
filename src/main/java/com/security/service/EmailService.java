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

    @Value("${app.email.brevo.api-key:#{null}}")
    private String brevoApiKey;

    public void sendVerificationEmail(User user, String verificationToken) {
        System.out.println("📧 Iniciando envío de email de verificación...");
        System.out.println("🔧 DEBUG EMAIL CONFIG:");
        System.out.println("📧 Username: " + fromEmail);
        System.out.println("🌐 Base URL: " + baseUrl);
        System.out.println("📤 Para: " + user.getEmail());
        System.out.println("🔑 Token: " + verificationToken);

        // Crear URL de verificación usando baseUrl (FRONTEND_URL)
        String verificationUrl = baseUrl + "/verify-account?token=" + verificationToken;
        String htmlContent = buildEmailTemplate(user.getFirstName(), verificationUrl, verificationToken);
        String subject = "Verificación de cuenta - AuthSystem";

        // PRIORIDAD 1: Usar Brevo API (más confiable en Railway)
        if (sendVerificationWithBrevoAPI(user, subject, htmlContent)) {
            return;
        }

        // PRIORIDAD 2: Usar Resend API (backup)
        if (sendVerificationWithResend(user, subject, htmlContent)) {
            return;
        }

        // PRIORIDAD 3: Intentar JavaMail/SMTP (puede fallar en Railway)
        try {
            System.out.println("📨 Intentando envío con JavaMail/SMTP (puede fallar en Railway)...");

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            System.out.println("✅ Email de verificación enviado exitosamente via SMTP a: " + user.getEmail());

        } catch (Exception e) {
            System.err.println("❌ Error enviando email de verificación a " + user.getEmail() + ": " + e.getMessage());
            throw new RuntimeException("Error al enviar email de verificación. Todos los proveedores fallaron.", e);
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
        System.out.println("📧 Iniciando envío de email de reseteo de contraseña...");
        System.out.println("🔧 DEBUG RESET PASSWORD CONFIG:");
        System.out.println("📤 Para: " + user.getEmail());
        System.out.println("🔑 Token: " + token);
        System.out.println("🌐 Base URL: " + baseUrl);

        // Crear URL de reseteo de contraseña usando baseUrl (FRONTEND_URL)
        String frontendUrl = (baseUrl != null) ? baseUrl.trim() : "";
        // Si el FRONTEND_URL fue configurado sin esquema, añadir https:// por seguridad
        if (!frontendUrl.startsWith("http://") && !frontendUrl.startsWith("https://") && !frontendUrl.isEmpty()) {
            frontendUrl = "https://" + frontendUrl;
        }
        if (frontendUrl.endsWith("/")) {
            frontendUrl = frontendUrl.substring(0, frontendUrl.length() - 1);
        }
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        System.out.println("🔗 Reset URL construido: " + resetUrl);
        String htmlContent = buildPasswordResetEmailTemplate(user.getFirstName(), resetUrl, token);
        String subject = "Recuperación de contraseña - AuthSystem";

        // PRIORIDAD 1: Usar Brevo API
        if (sendPasswordResetWithBrevoAPI(user, subject, htmlContent)) {
            return;
        }

        // PRIORIDAD 2: Usar Resend API (backup)
        if (sendPasswordResetWithResend(user, subject, htmlContent)) {
            return;
        }

        // PRIORIDAD 3: Intentar JavaMail/SMTP (puede fallar en Railway)
        try {
            System.out.println("📨 Intentando envío de reseteo con JavaMail/SMTP (puede fallar en Railway)...");

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            System.out.println("✅ Email de reseteo de contraseña enviado exitosamente via SMTP a: " + user.getEmail());

        } catch (Exception e) {
            System.err.println(
                    "❌ Error enviando email de reseteo de contraseña a " + user.getEmail() + ": " + e.getMessage());
            throw new RuntimeException(
                    "Error al enviar email de reseteo de contraseña. Todos los proveedores fallaron.", e);
        }
    }

    public void send2FACodeEmail(User user, String code) {
        System.out.println("📧 Iniciando envío de código 2FA por email...");

        // 🔍 DEBUG: Verificar configuraciones
        System.out.println("🔧 DEBUG CONFIGURACIONES:");
        System.out.println("🔑 Brevo API Key: '" + brevoApiKey + "'");
        System.out.println("🔑 Brevo API Key null? " + (brevoApiKey == null));
        System.out.println("🔑 Brevo API Key empty? " + (brevoApiKey != null && brevoApiKey.trim().isEmpty()));
        System.out.println("🔑 Resend API Key: '" + resendApiKey + "'");

        // PRIORIDAD 1: Usar Brevo API (más confiable en Railway)
        String htmlContent = build2FAEmailTemplate(user.getFirstName(), code);
        String subject = "Código de verificación 2FA - AuthSystem";
        if (sendWith2FAWithBrevoAPI(user, subject, htmlContent)) {
            return;
        }

        // PRIORIDAD 2: Usar Resend API (backup)
        if (sendWithResend(user, code)) {
            return;
        }

        // PRIORIDAD 2: Intentar con SendGrid
        if (sendWithSendGrid(user, code)) {
            return;
        }

        // PRIORIDAD 3: Intentar JavaMail con Brevo SMTP
        if (sendWith2FAWithJavaMail(user, subject, htmlContent)) {
            return;
        }

        // PRIORIDAD 4: Intentar con Mailgun
        if (sendWithMailgun(user, code)) {
            return;
        }

        // Si todo falla, lanzar excepción
        throw new RuntimeException("❌ Error: No se pudo enviar el email 2FA. Todos los proveedores fallaron.");
    }

    private boolean sendWith2FAWithJavaMail(User user, String subject, String htmlContent) {
        try {
            System.out.println("📨 Intentando envío 2FA con JavaMail (Brevo SMTP)...");
            System.out.println("🔧 DEBUG MAIL CONFIG:");
            System.out.println("📧 From Email: " + fromEmail);
            System.out.println("📤 To Email: " + user.getEmail());
            System.out.println("📝 Subject: " + subject);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail); // Usará la configuración de Brevo
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            long startTime = System.currentTimeMillis();
            mailSender.send(message);
            long endTime = System.currentTimeMillis();

            System.out.println("✅ Código 2FA enviado exitosamente via Brevo SMTP a: " + user.getEmail() +
                    " (tiempo: " + (endTime - startTime) + "ms)");
            return true;

        } catch (Exception e) {
            System.err.println("❌ Error con JavaMail/Brevo SMTP: " + e.getMessage());
            System.err.println("⚠️  Intentando con proveedores alternativos...");
            return false;
        }
    }

    private boolean sendWith2FAWithBrevoAPI(User user, String subject, String htmlContent) {
        if (brevoApiKey == null || brevoApiKey.trim().isEmpty()) {
            System.out.println("🔄 Brevo API Key no configurada, saltando...");
            return false;
        }

        try {
            System.out.println("📨 Intentando envío 2FA con Brevo API...");
            System.out.println(
                    "🔑 DEBUG: Brevo API Key presente = " + (brevoApiKey != null && !brevoApiKey.trim().isEmpty()));

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            Map<String, Object> emailData = new HashMap<>();

            // Remitente - usando tu email personal verificado
            Map<String, String> sender = new HashMap<>();
            sender.put("name", "AuthSystem");
            sender.put("email", "pepemontgomez@gmail.com"); // Email verificado en Brevo
            emailData.put("sender", sender);

            // Destinatarios
            Map<String, String> recipient = new HashMap<>();
            recipient.put("email", user.getEmail());
            recipient.put("name", user.getFirstName());
            emailData.put("to", new Map[] { recipient });

            emailData.put("subject", subject);
            emailData.put("htmlContent", htmlContent);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(emailData, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.brevo.com/v3/smtp/email", request, String.class);
            long endTime = System.currentTimeMillis();

            System.out.println("📊 Brevo API Response - Status: " + response.getStatusCode() +
                    ", Body: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Código 2FA enviado exitosamente via Brevo API a: " + user.getEmail() +
                        " (tiempo: " + (endTime - startTime) + "ms)");
                return true;
            } else {
                System.err.println("❌ Error Brevo API - Status: " + response.getStatusCode() +
                        ", Body: " + response.getBody());
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Error con Brevo API: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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
            // Enviar al email real del usuario usando el dominio verificado por defecto
            emailData.put("to", new String[] { user.getEmail() });
            emailData.put("subject", "Código de verificación 2FA para " + user.getEmail() + " - AuthSystem");
            emailData.put("html", build2FAEmailTemplate(user.getFirstName(), code));

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

    private boolean sendWithSendGrid(User user, String code) {
        String sendGridApiKey = System.getenv("SENDGRID_API_KEY");

        System.out.println("🔍 DEBUG SENDGRID: sendGridApiKey = '" + sendGridApiKey + "'");
        System.out.println("🔍 DEBUG SENDGRID: is null? " + (sendGridApiKey == null));
        System.out
                .println("🔍 DEBUG SENDGRID: is empty? " + (sendGridApiKey != null && sendGridApiKey.trim().isEmpty()));

        if (sendGridApiKey == null || sendGridApiKey.trim().isEmpty()) {
            System.out.println("🔄 SendGrid API Key no configurada, saltando...");
            return false;
        }

        try {
            System.out.println("📨 Intentando envío con SendGrid API...");

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + sendGridApiKey);

            // Estructura JSON para SendGrid v3 API
            Map<String, Object> emailData = new HashMap<>();

            // Personalización del remitente
            Map<String, String> fromData = new HashMap<>();
            fromData.put("email", "noreply@authsystem.com");
            fromData.put("name", "AuthSystem Security");
            emailData.put("from", fromData);

            // Configuración de destinatarios
            Map<String, String> toData = new HashMap<>();
            toData.put("email", user.getEmail());
            toData.put("name", user.getFirstName());
            emailData.put("personalizations", new Map[] {
                    Map.of("to", new Map[] { toData })
            });

            emailData.put("subject", "Código de verificación 2FA - AuthSystem");

            // Contenido del email
            emailData.put("content", new Map[] {
                    Map.of("type", "text/html", "value", build2FAEmailTemplate(user.getFirstName(), code))
            });

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(emailData, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.sendgrid.com/v3/mail/send", request, String.class);
            long endTime = System.currentTimeMillis();

            System.out.println(
                    "📊 SendGrid Response - Status: " + response.getStatusCode() + ", Body: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Código 2FA enviado exitosamente via SendGrid a: " + user.getEmail() +
                        " (tiempo: " + (endTime - startTime) + "ms)");
                return true;
            } else {
                System.err.println(
                        "❌ Error SendGrid - Status: " + response.getStatusCode() + ", Body: " + response.getBody());
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Error con SendGrid: " + e.getMessage());
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

    // Método para enviar email de verificación usando Brevo API
    private boolean sendVerificationWithBrevoAPI(User user, String subject, String htmlContent) {
        if (brevoApiKey == null || brevoApiKey.trim().isEmpty()) {
            System.out.println("🔄 Brevo API Key no configurada para verificación, saltando...");
            return false;
        }

        try {
            System.out.println("📨 Intentando envío de verificación con Brevo API...");

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            Map<String, Object> emailData = new HashMap<>();

            // Remitente - usando email verificado en Brevo
            Map<String, String> sender = new HashMap<>();
            sender.put("name", "AuthSystem");
            sender.put("email", "pepemontgomez@gmail.com"); // Email verificado en Brevo
            emailData.put("sender", sender);

            // Destinatarios
            Map<String, String> recipient = new HashMap<>();
            recipient.put("email", user.getEmail());
            recipient.put("name", user.getFirstName());
            emailData.put("to", new Map[] { recipient });

            emailData.put("subject", subject);
            emailData.put("htmlContent", htmlContent);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(emailData, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.brevo.com/v3/smtp/email", request, String.class);
            long endTime = System.currentTimeMillis();

            System.out.println("📊 Brevo API Response (Verificación) - Status: " + response.getStatusCode() +
                    ", Body: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Email de verificación enviado exitosamente via Brevo API a: " + user.getEmail() +
                        " (tiempo: " + (endTime - startTime) + "ms)");
                return true;
            } else {
                System.err.println("❌ Error Brevo API (Verificación) - Status: " + response.getStatusCode() +
                        ", Body: " + response.getBody());
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Error con Brevo API (Verificación): " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Método para enviar email de verificación usando Resend API
    private boolean sendVerificationWithResend(User user, String subject, String htmlContent) {
        if (resendApiKey == null || resendApiKey.trim().isEmpty() || resendApiKey.equals("re_demo_key_placeholder")) {
            System.out.println("🔄 Resend API Key no configurada para verificación, saltando...");
            return false;
        }

        try {
            System.out.println("📨 Intentando envío de verificación con Resend API...");

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + resendApiKey);

            Map<String, Object> emailData = new HashMap<>();
            emailData.put("from", "AuthSystem <onboarding@resend.dev>");
            emailData.put("to", new String[] { user.getEmail() });
            emailData.put("subject", subject);
            emailData.put("html", htmlContent);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(emailData, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.resend.com/emails", request, String.class);
            long endTime = System.currentTimeMillis();

            System.out.println("📊 Resend Response (Verificación) - Status: " + response.getStatusCode() +
                    ", Body: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Email de verificación enviado exitosamente via Resend a: " + user.getEmail() +
                        " (tiempo: " + (endTime - startTime) + "ms)");
                return true;
            } else {
                System.err.println("❌ Error Resend (Verificación) - Status: " + response.getStatusCode() +
                        ", Body: " + response.getBody());
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Error con Resend (Verificación): " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Método para enviar email de reseteo de contraseña usando Brevo API
    private boolean sendPasswordResetWithBrevoAPI(User user, String subject, String htmlContent) {
        if (brevoApiKey == null || brevoApiKey.trim().isEmpty()) {
            System.out.println("🔄 Brevo API Key no configurada para reseteo de contraseña, saltando...");
            return false;
        }

        try {
            System.out.println("📨 Intentando envío de reseteo de contraseña con Brevo API...");

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            Map<String, Object> emailData = new HashMap<>();

            // Remitente - usando email verificado en Brevo
            Map<String, String> sender = new HashMap<>();
            sender.put("name", "AuthSystem Security");
            sender.put("email", "pepemontgomez@gmail.com"); // Email verificado en Brevo
            emailData.put("sender", sender);

            // Destinatarios
            Map<String, String> recipient = new HashMap<>();
            recipient.put("email", user.getEmail());
            recipient.put("name", user.getFirstName());
            emailData.put("to", new Map[] { recipient });

            emailData.put("subject", subject);
            emailData.put("htmlContent", htmlContent);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(emailData, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.brevo.com/v3/smtp/email", request, String.class);
            long endTime = System.currentTimeMillis();

            System.out.println("📊 Brevo API Response (Reset Password) - Status: " + response.getStatusCode() +
                    ", Body: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println(
                        "✅ Email de reseteo de contraseña enviado exitosamente via Brevo API a: " + user.getEmail() +
                                " (tiempo: " + (endTime - startTime) + "ms)");
                return true;
            } else {
                System.err.println("❌ Error Brevo API (Reset Password) - Status: " + response.getStatusCode() +
                        ", Body: " + response.getBody());
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Error con Brevo API (Reset Password): " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Método para enviar email de reseteo de contraseña usando Resend API
    private boolean sendPasswordResetWithResend(User user, String subject, String htmlContent) {
        if (resendApiKey == null || resendApiKey.trim().isEmpty() || resendApiKey.equals("re_demo_key_placeholder")) {
            System.out.println("🔄 Resend API Key no configurada para reseteo de contraseña, saltando...");
            return false;
        }

        try {
            System.out.println("📨 Intentando envío de reseteo de contraseña con Resend API...");

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + resendApiKey);

            Map<String, Object> emailData = new HashMap<>();
            emailData.put("from", "AuthSystem Security <onboarding@resend.dev>");
            emailData.put("to", new String[] { user.getEmail() });
            emailData.put("subject", subject);
            emailData.put("html", htmlContent);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(emailData, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.resend.com/emails", request, String.class);
            long endTime = System.currentTimeMillis();

            System.out.println("📊 Resend Response (Reset Password) - Status: " + response.getStatusCode() +
                    ", Body: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println(
                        "✅ Email de reseteo de contraseña enviado exitosamente via Resend a: " + user.getEmail() +
                                " (tiempo: " + (endTime - startTime) + "ms)");
                return true;
            } else {
                System.err.println("❌ Error Resend (Reset Password) - Status: " + response.getStatusCode() +
                        ", Body: " + response.getBody());
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Error con Resend (Reset Password): " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Template para email de reseteo de contraseña
    private String buildPasswordResetEmailTemplate(String userName, String resetUrl, String token) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        .container { max-width: 600px; margin: 0 auto; font-family: Arial, sans-serif; }
                        .header { background-color: #e74c3c; color: white; padding: 20px; text-align: center; }
                        .content { padding: 20px; text-align: center; }
                        .button {
                            background-color: #e74c3c;
                            color: white;
                            padding: 15px 30px;
                            text-decoration: none;
                            border-radius: 5px;
                            display: inline-block;
                            margin: 20px 0;
                            font-weight: bold;
                        }
                        .token {
                            background-color: #f1f1f1;
                            padding: 15px;
                            font-family: monospace;
                            font-size: 18px;
                            text-align: center;
                            margin: 20px 0;
                            border-radius: 5px;
                            border: 2px solid #e74c3c;
                            color: #333;
                            word-break: break-all;
                        }
                        .warning {
                            color: #e74c3c;
                            font-size: 14px;
                            margin-top: 20px;
                            background-color: #ffe6e6;
                            padding: 15px;
                            border-radius: 5px;
                            border-left: 4px solid #e74c3c;
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
                            <h1>🔒 Recuperación de Contraseña</h1>
                        </div>
                        <div class="content">
                            <h2>Hola %s</h2>
                            <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta en AuthSystem.</p>

                            <h3>Opción 1: Click en el enlace</h3>
                            <a href="%s" class="button">Restablecer Contraseña</a>

                            <h3>Opción 2: Usa este token</h3>
                            <div class="token">%s</div>
                            <p>Copia y pega este token en la aplicación para restablecer tu contraseña.</p>

                            <p><strong>Este enlace y token expiran en 1 hora.</strong></p>

                            <div class="warning">
                                <strong>⚠️ Importante:</strong><br>
                                • Si no solicitaste este restablecimiento, ignora este email.<br>
                                • Tu contraseña actual sigue siendo válida hasta que la cambies.<br>
                                • Nunca compartas este token con nadie.<br>
                                • Si tienes dudas, contacta a nuestro soporte.
                            </div>
                        </div>
                        <div class="footer">
                            <p>Este email fue enviado automáticamente por AuthSystem.</p>
                            <p>© 2025 AuthSystem. Todos los derechos reservados.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(userName, resetUrl, token);
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