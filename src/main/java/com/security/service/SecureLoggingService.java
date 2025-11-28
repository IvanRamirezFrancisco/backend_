package com.security.service;

import com.security.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Servicio de logging seguro que enmascara datos sensibles
 * e implementa auditoría detallada
 */
@Service
public class SecureLoggingService {

    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final Logger generalLogger = LoggerFactory.getLogger(SecureLoggingService.class);

    @Value("${app.security.logging.mask-sensitive-data:true}")
    private boolean maskSensitiveData;

    @Value("${app.security.logging.include-ip:true}")
    private boolean includeIpInLogs;

    @Value("${app.security.logging.detailed-audit:true}")
    private boolean detailedAudit;

    // Patrones para identificar datos sensibles
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|pwd|pass|secret|key|token)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern
            .compile("\\+?\\d{1,3}[\\s-]?\\(?\\d{1,4}\\)?[\\s-]?\\d{1,4}[\\s-]?\\d{1,9}");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern
            .compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");

    /**
     * Log de eventos de autenticación
     */
    public void logAuthenticationEvent(String event, String userId, String email, String ipAddress,
            String userAgent, boolean success) {
        try {
            Map<String, Object> auditData = createBaseAuditData();
            auditData.put("event_type", "AUTHENTICATION");
            auditData.put("event", event);
            auditData.put("user_id", userId);
            auditData.put("email", maskEmail(email));
            auditData.put("ip_address", includeIpInLogs ? ipAddress : maskIp(ipAddress));
            auditData.put("user_agent", sanitizeUserAgent(userAgent));
            auditData.put("success", success);
            auditData.put("risk_level", calculateRiskLevel(event, success, ipAddress));

            if (detailedAudit) {
                securityLogger.info("AUTH_EVENT: {}", formatAuditData(auditData));
            }

            if (!success) {
                securityLogger.warn("FAILED_AUTH: {} for user {} from IP {}",
                        event, maskEmail(email), maskIp(ipAddress));
            }

        } catch (Exception e) {
            generalLogger.error("Error logging authentication event: {}", e.getMessage());
        }
    }

    /**
     * Log de eventos de autorización
     */
    public void logAuthorizationEvent(String userId, String resource, String action,
            String ipAddress, boolean granted) {
        try {
            Map<String, Object> auditData = createBaseAuditData();
            auditData.put("event_type", "AUTHORIZATION");
            auditData.put("user_id", userId);
            auditData.put("resource", sanitizeInput(resource));
            auditData.put("action", sanitizeInput(action));
            auditData.put("ip_address", includeIpInLogs ? ipAddress : maskIp(ipAddress));
            auditData.put("granted", granted);

            if (detailedAudit) {
                securityLogger.info("AUTHZ_EVENT: {}", formatAuditData(auditData));
            }

            if (!granted) {
                securityLogger.warn("ACCESS_DENIED: User {} denied access to {} for action {} from IP {}",
                        userId, sanitizeInput(resource), sanitizeInput(action), maskIp(ipAddress));
            }

        } catch (Exception e) {
            generalLogger.error("Error logging authorization event: {}", e.getMessage());
        }
    }

    /**
     * Log de cambios de datos sensibles
     */
    public void logSensitiveDataChange(String userId, String dataType, String action,
            String oldValue, String newValue, String ipAddress) {
        try {
            Map<String, Object> auditData = createBaseAuditData();
            auditData.put("event_type", "DATA_CHANGE");
            auditData.put("user_id", userId);
            auditData.put("data_type", sanitizeInput(dataType));
            auditData.put("action", sanitizeInput(action));
            auditData.put("old_value", maskSensitiveData(oldValue, dataType));
            auditData.put("new_value", maskSensitiveData(newValue, dataType));
            auditData.put("ip_address", includeIpInLogs ? ipAddress : maskIp(ipAddress));

            securityLogger.info("DATA_CHANGE: {}", formatAuditData(auditData));

        } catch (Exception e) {
            generalLogger.error("Error logging sensitive data change: {}", e.getMessage());
        }
    }

    /**
     * Log de eventos de seguridad críticos
     */
    public void logSecurityEvent(String eventType, String description, String userId,
            String ipAddress, String severity) {
        try {
            Map<String, Object> auditData = createBaseAuditData();
            auditData.put("event_type", "SECURITY_INCIDENT");
            auditData.put("incident_type", sanitizeInput(eventType));
            auditData.put("description", sanitizeInput(description));
            auditData.put("user_id", userId);
            auditData.put("ip_address", includeIpInLogs ? ipAddress : maskIp(ipAddress));
            auditData.put("severity", sanitizeInput(severity));

            switch (severity.toUpperCase()) {
                case "HIGH", "CRITICAL" -> securityLogger.error("SECURITY_INCIDENT: {}", formatAuditData(auditData));
                case "MEDIUM" -> securityLogger.warn("SECURITY_INCIDENT: {}", formatAuditData(auditData));
                default -> securityLogger.info("SECURITY_INCIDENT: {}", formatAuditData(auditData));
            }

        } catch (Exception e) {
            generalLogger.error("Error logging security event: {}", e.getMessage());
        }
    }

    /**
     * Log de intentos de acceso sospechosos
     */
    public void logSuspiciousActivity(String activityType, String details, String ipAddress,
            String userAgent, String riskScore) {
        try {
            Map<String, Object> auditData = createBaseAuditData();
            auditData.put("event_type", "SUSPICIOUS_ACTIVITY");
            auditData.put("activity_type", sanitizeInput(activityType));
            auditData.put("details", sanitizeInput(details));
            auditData.put("ip_address", includeIpInLogs ? ipAddress : maskIp(ipAddress));
            auditData.put("user_agent", sanitizeUserAgent(userAgent));
            auditData.put("risk_score", riskScore);

            securityLogger.warn("SUSPICIOUS_ACTIVITY: {}", formatAuditData(auditData));

        } catch (Exception e) {
            generalLogger.error("Error logging suspicious activity: {}", e.getMessage());
        }
    }

    /**
     * Sanitiza cualquier input para logging seguro
     */
    public String sanitizeForLogging(String input) {
        if (input == null)
            return "null";

        String sanitized = sanitizeInput(input);

        if (maskSensitiveData) {
            // Enmascarar patrones sensibles
            sanitized = maskSensitivePatterns(sanitized);
        }

        return sanitized;
    }

    /**
     * Crea datos base para auditoría
     */
    private Map<String, Object> createBaseAuditData() {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        auditData.put("service", "casa-musica-castillo");
        auditData.put("version", "1.0.0");
        return auditData;
    }

    /**
     * Calcula el nivel de riesgo de un evento
     */
    private String calculateRiskLevel(String event, boolean success, String ipAddress) {
        // Lógica básica de cálculo de riesgo
        if (!success) {
            if (event.contains("LOGIN") || event.contains("PASSWORD")) {
                return "MEDIUM";
            }
            return "LOW";
        }

        // Verificar si es una IP sospechosa (implementar lógica de IP reputation)
        if (isSuspiciousIp(ipAddress)) {
            return "HIGH";
        }

        return "LOW";
    }

    /**
     * Verifica si una IP es sospechosa
     */
    private boolean isSuspiciousIp(String ipAddress) {
        // Implementar lógica de verificación de IP
        // Por ahora, una implementación básica
        return false;
    }

    /**
     * Enmascara email para logging
     */
    private String maskEmail(String email) {
        if (email == null || !maskSensitiveData)
            return email;

        if (EMAIL_PATTERN.matcher(email).matches()) {
            String[] parts = email.split("@");
            if (parts.length == 2) {
                String localPart = parts[0];
                String domain = parts[1];
                String maskedLocal = localPart.length() > 2 ? localPart.substring(0, 2) + "***" : "***";
                return maskedLocal + "@" + domain;
            }
        }

        return email;
    }

    /**
     * Enmascara IP para logging
     */
    private String maskIp(String ipAddress) {
        if (ipAddress == null || !maskSensitiveData)
            return ipAddress;

        if (ipAddress.contains(".")) {
            // IPv4
            String[] parts = ipAddress.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".***." + parts[3];
            }
        } else if (ipAddress.contains(":")) {
            // IPv6 - simplificado
            return ipAddress.substring(0, Math.min(8, ipAddress.length())) + "***";
        }

        return ipAddress;
    }

    /**
     * Sanitiza user agent
     */
    private String sanitizeUserAgent(String userAgent) {
        if (userAgent == null)
            return "unknown";

        // Limpiar y truncar user agent
        String sanitized = userAgent.replaceAll("[<>\"'&]", "");
        return sanitized.length() > 200 ? sanitized.substring(0, 200) + "..." : sanitized;
    }

    /**
     * Enmascara datos sensibles según el tipo
     */
    private String maskSensitiveData(String data, String dataType) {
        if (data == null || !maskSensitiveData)
            return data;

        switch (dataType.toLowerCase()) {
            case "password", "secret", "token" -> {
                return "***MASKED***";
            }
            case "email" -> {
                return maskEmail(data);
            }
            case "phone" -> {
                return maskPhone(data);
            }
            case "credit_card" -> {
                return maskCreditCard(data);
            }
            default -> {
                return maskSensitivePatterns(data);
            }
        }
    }

    /**
     * Enmascara teléfonos
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4)
            return "***";
        return "***" + phone.substring(phone.length() - 4);
    }

    /**
     * Enmascara tarjetas de crédito
     */
    private String maskCreditCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4)
            return "***";
        return "****-****-****-" + cardNumber.substring(cardNumber.length() - 4);
    }

    /**
     * Enmascara patrones sensibles en texto
     */
    private String maskSensitivePatterns(String text) {
        if (text == null)
            return null;

        String result = text;

        // Enmascarar emails
        result = EMAIL_PATTERN.matcher(result).replaceAll(matchResult -> maskEmail(matchResult.group()));

        // Enmascarar teléfonos
        result = PHONE_PATTERN.matcher(result).replaceAll("***-***-****");

        // Enmascarar tarjetas de crédito
        result = CREDIT_CARD_PATTERN.matcher(result).replaceAll("****-****-****-****");

        return result;
    }

    /**
     * Sanitiza input general
     */
    private String sanitizeInput(String input) {
        if (input == null)
            return null;

        // Remover caracteres peligrosos para logging
        return input.replaceAll("[\r\n\t]", " ")
                .replaceAll("[<>\"'&]", "")
                .trim();
    }

    /**
     * Formatea datos de auditoría para logging estructurado
     */
    private String formatAuditData(Map<String, Object> auditData) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : auditData.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(" ");
        }
        return sb.toString().trim();
    }
}