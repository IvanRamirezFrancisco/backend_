package com.security.service;

import com.security.model.User;
import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class SendGridEmailService {

    @Value("${app.sendgrid.api-key:#{null}}")
    private String sendGridApiKey;

    @Value("${app.sendgrid.from-email:noreply@authsystem.com}")
    private String fromEmail;

    @Value("${app.sendgrid.from-name:AuthSystem}")
    private String fromName;

    public void send2FACodeEmail(User user, String code) {
        if (sendGridApiKey == null || sendGridApiKey.trim().isEmpty()) {
            System.err.println("⚠️ SendGrid API Key no configurada. Usando JavaMail como fallback.");
            throw new RuntimeException("SendGrid no configurado");
        }

        try {
            Email from = new Email(fromEmail, fromName);
            String subject = "Código de verificación 2FA - AuthSystem";
            Email to = new Email(user.getEmail());

            String htmlContent = build2FAEmailTemplate(user.getFirstName(), code);
            Content content = new Content("text/html", htmlContent);

            Mail mail = new Mail(from, subject, to, content);

            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();

            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            System.out.println("📧 Enviando código 2FA por email usando SendGrid...");
            long startTime = System.currentTimeMillis();

            Response response = sg.api(request);
            long endTime = System.currentTimeMillis();

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                System.out.println("✅ Código 2FA enviado exitosamente via SendGrid a: " + user.getEmail() +
                        " (tiempo: " + (endTime - startTime) + "ms, status: " + response.getStatusCode() + ")");
            } else {
                System.err.println(
                        "❌ Error SendGrid - Status: " + response.getStatusCode() + ", Body: " + response.getBody());
                throw new RuntimeException("Error al enviar email via SendGrid: " + response.getBody());
            }

        } catch (IOException e) {
            System.err.println("❌ Error enviando código 2FA via SendGrid a " + user.getEmail() + ": " + e.getMessage());
            throw new RuntimeException("Error al enviar código 2FA por email via SendGrid", e);
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
                        }
                        .footer { padding: 20px; text-align: center; font-size: 12px; color: #666; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>🔐 AuthSystem</h1>
                            <h2>Código de Verificación 2FA</h2>
                        </div>
                        <div class="content">
                            <p>Hola <strong>%s</strong>,</p>
                            <p>Tu código de verificación de dos factores es:</p>
                            <div class="code">%s</div>
                            <p>Este código expirará en 5 minutos.</p>
                            <p>Si no solicitaste este código, ignora este mensaje.</p>
                        </div>
                        <div class="footer">
                            <p>© 2024 AuthSystem - Sistema de Autenticación Segura</p>
                            <p>Enviado via SendGrid - Compatible con Railway</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(userName, code);
    }
}