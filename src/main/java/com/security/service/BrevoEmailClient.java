package com.security.service;

import com.security.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP dedicado para la API transaccional de Brevo v3.
 * <p>
 * Responsabilidades:
 * <ul>
 *   <li>Construir y enviar peticiones POST a https://api.brevo.com/v3/smtp/email</li>
 *   <li>Manejar errores HTTP sin exponer detalles internos</li>
 *   <li>Registrar logs seguros (sin datos sensibles raw)</li>
 * </ul>
 *
 * NO usa JavaMail/SMTP para nada — es solo HTTP.
 */
@Component
public class BrevoEmailClient {

    private static final Logger logger = LoggerFactory.getLogger(BrevoEmailClient.class);

    private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";

    /**
     * API Key de Brevo. Se inyecta desde la variable de entorno BREVO_API_KEY.
     * Si no está configurada, el cliente queda deshabilitado (retorna false).
     */
    @Value("${app.email.brevo.api-key:#{null}}")
    private String brevoApiKey;

    /**
     * Dirección de correo remitente verificada en la cuenta Brevo.
     * Configurable vía app.email.brevo.sender-email (o fallback al mail.username).
     */
    @Value("${app.email.brevo.sender-email:${spring.mail.username:noreply@example.com}}")
    private String senderEmail;

    /**
     * Nombre visible del remitente en el cliente de correo del destinatario.
     */
    @Value("${app.email.brevo.sender-name:AuthSystem}")
    private String senderName;

    private final RestTemplate restTemplate;

    public BrevoEmailClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Indica si el cliente está operativo (API key configurada y no vacía).
     */
    public boolean isConfigured() {
        return brevoApiKey != null && !brevoApiKey.isBlank();
    }

    /**
     * Envía un correo HTML transaccional a través de la API de Brevo.
     *
     * @param toEmail      dirección del destinatario
     * @param toName       nombre del destinatario (para personalización)
     * @param subject      asunto del correo
     * @param htmlContent  cuerpo HTML del correo
     * @return {@code true} si Brevo respondió con 2xx; {@code false} en caso contrario
     */
    public boolean send(String toEmail, String toName, String subject, String htmlContent) {
        if (!isConfigured()) {
            logger.warn("Brevo API Key no configurada — omitiendo envío via Brevo API");
            return false;
        }

        HttpHeaders headers = buildHeaders();
        Map<String, Object> payload = buildPayload(toEmail, toName, subject, htmlContent);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_SEND_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Correo enviado exitosamente via Brevo API a: {}",
                        LogSanitizer.maskEmail(toEmail));
                return true;
            }

            logger.warn("Brevo API respondió con estado inesperado: {}", response.getStatusCode());
            return false;

        } catch (HttpClientErrorException e) {
            // 4xx — error de configuración (API key inválida, remitente no verificado, etc.)
            logger.error("Error de cliente al llamar Brevo API [{}] — verifica la API key y el sender: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return false;

        } catch (HttpServerErrorException e) {
            // 5xx — error temporal de Brevo
            logger.error("Error de servidor en Brevo API [{}] — reintentando con fallback",
                    e.getStatusCode());
            return false;

        } catch (Exception e) {
            logger.error("Error inesperado al llamar Brevo API: {}", e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Métodos privados de apoyo
    // ─────────────────────────────────────────────────────────────────────────

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey);
        return headers;
    }

    private Map<String, Object> buildPayload(String toEmail, String toName,
                                              String subject, String htmlContent) {
        Map<String, Object> payload = new HashMap<>();

        // Remitente — debe ser un dominio/email verificado en la cuenta Brevo
        Map<String, String> sender = new HashMap<>();
        sender.put("email", senderEmail);
        sender.put("name", senderName);
        payload.put("sender", sender);

        // Destinatario
        Map<String, String> recipient = new HashMap<>();
        recipient.put("email", toEmail);
        recipient.put("name", toName != null ? toName : "");
        payload.put("to", List.of(recipient));

        payload.put("subject", subject);
        payload.put("htmlContent", htmlContent);

        return payload;
    }
}
