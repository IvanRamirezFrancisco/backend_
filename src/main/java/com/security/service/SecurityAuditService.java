package com.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio de Auditoría y Monitoreo en Tiempo Real
 * Implementa detección de patrones sospechosos y alertas de seguridad
 */
@Service
public class SecurityAuditService {

    private static final Logger auditLogger = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final Logger alertLogger = LoggerFactory.getLogger("SECURITY_ALERTS");
    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditService.class);

    // Almacenamiento en memoria para eventos de auditoría
    private final Map<String, SecurityEvent> eventStorage = new ConcurrentHashMap<>();
    private final Map<String, SecurityAlert> alertStorage = new ConcurrentHashMap<>();

    @Autowired
    private SecureLoggingService loggingService;

    @Value("${app.security.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    @Value("${app.security.monitoring.suspicious-threshold:5}")
    private int suspiciousActivityThreshold;

    @Value("${app.security.monitoring.alert-cooldown-minutes:15}")
    private int alertCooldownMinutes;

    // Contadores en memoria para análisis en tiempo real
    private final Map<String, AtomicInteger> ipFailureCount = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> userFailureCount = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastAlertTime = new ConcurrentHashMap<>();

    private static final String AUDIT_EVENT_KEY = "audit:event:";

    /**
     * Registra un evento crítico de seguridad
     */
    public void logCriticalSecurityEvent(String eventType, String userId, String details,
            String ipAddress, String userAgent) {
        try {
            if (!monitoringEnabled)
                return;

            SecurityEvent event = new SecurityEvent(
                    eventType,
                    userId,
                    sanitizeDetails(details),
                    ipAddress,
                    sanitizeUserAgent(userAgent),
                    LocalDateTime.now(),
                    calculateSeverity(eventType));

            // Log estructurado
            auditLogger.warn("CRITICAL_EVENT: type={} user={} ip={} severity={} details={}",
                    eventType, userId, maskIp(ipAddress), event.getSeverity(),
                    sanitizeDetails(details));

            // Almacenar en Redis para análisis
            storeSecurityEvent(event);

            // Análisis en tiempo real
            analyzeEventPattern(event);

            // Verificar si requiere alerta inmediata
            if (requiresImmediateAlert(event)) {
                triggerSecurityAlert(event);
            }

        } catch (Exception e) {
            logger.error("Error logging critical security event: {}", e.getMessage());
        }
    }

    /**
     * Registra intentos de acceso fallidos
     */
    public void logFailedAccess(String eventType, String identifier, String ipAddress,
            String userAgent, String reason) {
        try {
            if (!monitoringEnabled)
                return;

            // Incrementar contadores
            ipFailureCount.computeIfAbsent(ipAddress, k -> new AtomicInteger(0)).incrementAndGet();
            userFailureCount.computeIfAbsent(identifier, k -> new AtomicInteger(0)).incrementAndGet();

            SecurityEvent event = new SecurityEvent(
                    eventType,
                    identifier,
                    reason,
                    ipAddress,
                    sanitizeUserAgent(userAgent),
                    LocalDateTime.now(),
                    "MEDIUM");

            auditLogger.info("FAILED_ACCESS: type={} identifier={} ip={} reason={}",
                    eventType, maskIdentifier(identifier), maskIp(ipAddress), reason);

            // Análisis de patrones sospechosos
            checkForSuspiciousActivity(ipAddress, identifier);

            storeSecurityEvent(event);

        } catch (Exception e) {
            logger.error("Error logging failed access: {}", e.getMessage());
        }
    }

    /**
     * Registra eventos de autenticación exitosos
     */
    public void logSuccessfulAuthentication(String userId, String ipAddress, String userAgent,
            String method) {
        try {
            if (!monitoringEnabled)
                return;

            SecurityEvent event = new SecurityEvent(
                    "SUCCESSFUL_AUTHENTICATION",
                    userId,
                    "Authentication method: " + method,
                    ipAddress,
                    sanitizeUserAgent(userAgent),
                    LocalDateTime.now(),
                    "LOW");

            auditLogger.info("SUCCESSFUL_AUTH: user={} ip={} method={}",
                    userId, maskIp(ipAddress), method);

            // Limpiar contadores de fallos para este usuario/IP tras éxito
            userFailureCount.remove(userId);
            ipFailureCount.computeIfPresent(ipAddress, (k, v) -> {
                v.set(Math.max(0, v.get() - 1)); // Decrementar gradualmente
                return v;
            });

            storeSecurityEvent(event);

        } catch (Exception e) {
            logger.error("Error logging successful authentication: {}", e.getMessage());
        }
    }

    /**
     * Registra cambios de datos sensibles
     */
    public void logSensitiveDataChange(String userId, String dataType, String action,
            String ipAddress, String userAgent) {
        try {
            if (!monitoringEnabled)
                return;

            SecurityEvent event = new SecurityEvent(
                    "SENSITIVE_DATA_CHANGE",
                    userId,
                    String.format("Data type: %s, Action: %s", dataType, action),
                    ipAddress,
                    sanitizeUserAgent(userAgent),
                    LocalDateTime.now(),
                    determineSensitivityLevel(dataType, action));

            auditLogger.warn("DATA_CHANGE: user={} type={} action={} ip={}",
                    userId, dataType, action, maskIp(ipAddress));

            storeSecurityEvent(event);

            // Alertar sobre cambios críticos
            if (isCriticalDataChange(dataType, action)) {
                triggerDataChangeAlert(event);
            }

        } catch (Exception e) {
            logger.error("Error logging sensitive data change: {}", e.getMessage());
        }
    }

    /**
     * Monitorea patrones de actividad sospechosa
     */
    @Scheduled(fixedRate = 300000) // Cada 5 minutos
    public void monitorSuspiciousActivity() {
        try {
            if (!monitoringEnabled)
                return;

            logger.debug("Running suspicious activity monitoring");

            // Analizar IPs con muchos fallos
            analyzeFailedAttempts();

            // Analizar patrones temporales
            analyzeTemporalPatterns();

            // Generar reporte de actividad
            generateActivityReport();

        } catch (Exception e) {
            logger.error("Error during suspicious activity monitoring: {}", e.getMessage());
        }
    }

    /**
     * Limpieza periódica de datos de auditoría
     */
    @Scheduled(fixedRate = 3600000) // Cada hora
    public void cleanupAuditData() {
        try {
            if (!monitoringEnabled)
                return;

            // Limpiar contadores antiguos
            cleanupCounters();

            // Limpiar eventos antiguos de Redis
            cleanupOldEvents();

            logger.debug("Audit data cleanup completed");

        } catch (Exception e) {
            logger.error("Error during audit data cleanup: {}", e.getMessage());
        }
    }

    /**
     * Genera alertas por actividad sospechosa
     */
    @Async
    public CompletableFuture<Void> triggerSecurityAlert(SecurityEvent event) {
        try {
            String alertKey = event.getEventType() + ":" + event.getIpAddress();

            // Verificar cooldown para evitar spam de alertas
            if (isInCooldown(alertKey)) {
                return CompletableFuture.completedFuture(null);
            }

            // Marcar tiempo de última alerta
            lastAlertTime.put(alertKey, LocalDateTime.now());

            SecurityAlert alert = new SecurityAlert(
                    generateAlertId(),
                    event.getEventType(),
                    event.getSeverity(),
                    String.format("Suspicious activity detected: %s from IP %s",
                            event.getEventType(), maskIp(event.getIpAddress())),
                    event,
                    LocalDateTime.now());

            // Log de alerta
            alertLogger.error("SECURITY_ALERT: id={} type={} severity={} ip={} details={}",
                    alert.getAlertId(), alert.getEventType(), alert.getSeverity(),
                    maskIp(event.getIpAddress()), alert.getDescription());

            // Enviar notificación (implementar según necesidades)
            sendSecurityNotification(alert);

            // Almacenar alerta para seguimiento
            storeSecurityAlert(alert);

        } catch (Exception e) {
            logger.error("Error triggering security alert: {}", e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Obtiene estadísticas de seguridad en tiempo real
     */
    public Map<String, Object> getSecurityStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // Contadores actuales
            stats.put("failedAttemptsFromIPs", ipFailureCount.size());
            stats.put("failedAttemptsFromUsers", userFailureCount.size());

            // Top IPs problemáticas
            List<Map.Entry<String, AtomicInteger>> topIPs = ipFailureCount.entrySet().stream()
                    .sorted(Map.Entry.<String, AtomicInteger>comparingByValue((a, b) -> b.get() - a.get()))
                    .limit(10)
                    .toList();

            stats.put("topSuspiciousIPs", topIPs.stream()
                    .collect(HashMap::new,
                            (m, e) -> m.put(maskIp(e.getKey()), e.getValue().get()),
                            HashMap::putAll));

            // Eventos por severidad en la última hora
            stats.put("eventsLastHour", getEventCountByTimeRange(LocalDateTime.now().minusHours(1)));

            // Estado de alertas activas
            stats.put("activeAlertsCount", getActiveAlertsCount());

            stats.put("timestamp", LocalDateTime.now().toString());

        } catch (Exception e) {
            logger.error("Error getting security statistics: {}", e.getMessage());
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    // Métodos auxiliares

    private void analyzeEventPattern(SecurityEvent event) {
        // Análisis de patrones en tiempo real usando memoria
        // Incrementar contador de frecuencia en memoria
        ipFailureCount.computeIfAbsent(event.getIpAddress(), k -> new AtomicInteger(0));

        // Analizar patrones específicos por tipo de evento
        if ("FAILED_LOGIN".equals(event.getEventType()) ||
                "BRUTE_FORCE_ATTACK".equals(event.getEventType())) {
            int failures = ipFailureCount.get(event.getIpAddress()).incrementAndGet();
            if (failures > suspiciousActivityThreshold * 2) {
                logger.warn("Critical pattern detected for IP: {} with {} failures",
                        maskIp(event.getIpAddress()), failures);
            }
        }
    }

    private boolean requiresImmediateAlert(SecurityEvent event) {
        return "CRITICAL".equals(event.getSeverity()) ||
                "BRUTE_FORCE_ATTACK".equals(event.getEventType()) ||
                "PRIVILEGE_ESCALATION".equals(event.getEventType());
    }

    private void checkForSuspiciousActivity(String ipAddress, String identifier) {
        int ipFailures = ipFailureCount.getOrDefault(ipAddress, new AtomicInteger(0)).get();
        int userFailures = userFailureCount.getOrDefault(identifier, new AtomicInteger(0)).get();

        if (ipFailures >= suspiciousActivityThreshold) {
            markSuspiciousIP(ipAddress);
            logSuspiciousActivity("EXCESSIVE_FAILED_ATTEMPTS", ipAddress, userFailures);
        }

        if (userFailures >= suspiciousActivityThreshold) {
            logSuspiciousActivity("USER_BRUTE_FORCE", identifier, userFailures);
        }
    }

    private void markSuspiciousIP(String ipAddress) {
        // Marcar IP como sospechosa en memoria por 24 horas
        SecurityEvent suspiciousEvent = new SecurityEvent(
                "SUSPICIOUS_IP_DETECTED",
                "system",
                "IP marked as suspicious due to excessive failures",
                ipAddress,
                "automated-detection",
                LocalDateTime.now(),
                "HIGH");

        String eventKey = "suspicious_ip:" + ipAddress + ":" + System.currentTimeMillis();
        eventStorage.put(eventKey, suspiciousEvent);

        loggingService.logSuspiciousActivity("SUSPICIOUS_IP_DETECTED",
                "IP marked as suspicious due to excessive failures",
                ipAddress, null, "HIGH");
    }

    private void logSuspiciousActivity(String activityType, String identifier, int count) {
        loggingService.logSuspiciousActivity(activityType,
                String.format("Failed attempts: %d", count),
                identifier, null, count > 10 ? "HIGH" : "MEDIUM");
    }

    private String calculateSeverity(String eventType) {
        return switch (eventType) {
            case "BRUTE_FORCE_ATTACK", "PRIVILEGE_ESCALATION", "DATA_BREACH" -> "CRITICAL";
            case "SUSPICIOUS_LOGIN", "UNUSUAL_LOCATION", "FAILED_2FA" -> "HIGH";
            case "EXCESSIVE_FAILED_ATTEMPTS", "SUSPICIOUS_USER_AGENT" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private String determineSensitivityLevel(String dataType, String action) {
        if ("PASSWORD".equals(dataType) || "SECURITY_SETTINGS".equals(dataType)) {
            return "HIGH";
        }
        if ("DELETE".equals(action) || "ADMIN_ACTION".equals(action)) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private boolean isCriticalDataChange(String dataType, String action) {
        return "PASSWORD".equals(dataType) ||
                "ADMIN_PRIVILEGES".equals(dataType) ||
                "SECURITY_SETTINGS".equals(dataType);
    }

    private void analyzeFailedAttempts() {
        ipFailureCount.entrySet().forEach(entry -> {
            String ip = entry.getKey();
            int failures = entry.getValue().get();

            if (failures > suspiciousActivityThreshold) {
                SecurityEvent event = new SecurityEvent(
                        "BRUTE_FORCE_DETECTION",
                        "system",
                        String.format("IP %s has %d failed attempts", maskIp(ip), failures),
                        ip,
                        null,
                        LocalDateTime.now(),
                        "HIGH");

                triggerSecurityAlert(event);
            }
        });
    }

    private void analyzeTemporalPatterns() {
        // Análisis de patrones temporales (implementar lógica específica)
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();

        // Detectar actividad fuera de horario normal (ejemplo: 2 AM - 6 AM)
        if (hour >= 2 && hour <= 6) {
            int currentActivity = getTotalActivityCount();
            int normalActivity = getAverageActivityForHour(hour);

            if (currentActivity > normalActivity * 2) {
                logSuspiciousActivity("UNUSUAL_HOUR_ACTIVITY",
                        "system", currentActivity);
            }
        }
    }

    private void generateActivityReport() {
        try {
            Map<String, Object> report = new HashMap<>();
            report.put("timestamp", LocalDateTime.now().toString());
            report.put("totalFailedAttempts", ipFailureCount.values().stream()
                    .mapToInt(AtomicInteger::get).sum());
            report.put("suspiciousIPs", ipFailureCount.size());
            report.put("activeUsers", userFailureCount.size());

            auditLogger.info("ACTIVITY_REPORT: {}", formatReport(report));

        } catch (Exception e) {
            logger.error("Error generating activity report: {}", e.getMessage());
        }
    }

    private void cleanupCounters() {
        // Limpiar contadores con valor 0 (inactivos)
        ipFailureCount.entrySet().removeIf(entry -> entry.getValue().get() == 0);
        userFailureCount.entrySet().removeIf(entry -> entry.getValue().get() == 0);
    }

    private void cleanupOldEvents() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
            eventStorage.entrySet().removeIf(entry -> entry.getValue().getTimestamp().isBefore(cutoff));

            logger.debug("Old events cleanup completed. Remaining events: {}", eventStorage.size());
        } catch (Exception e) {
            logger.error("Error during old events cleanup: {}", e.getMessage());
        }
    }

    private void cleanupOldAlerts() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            alertStorage.entrySet().removeIf(entry -> entry.getValue().getTimestamp().isBefore(cutoff));

            logger.debug("Old alerts cleanup completed. Remaining alerts: {}", alertStorage.size());
        } catch (Exception e) {
            logger.error("Error during old alerts cleanup: {}", e.getMessage());
        }
    }

    private boolean isInCooldown(String alertKey) {
        LocalDateTime lastAlert = lastAlertTime.get(alertKey);
        if (lastAlert == null)
            return false;

        return LocalDateTime.now().isBefore(lastAlert.plusMinutes(alertCooldownMinutes));
    }

    private void triggerDataChangeAlert(SecurityEvent event) {
        CompletableFuture.runAsync(() -> {
            try {
                alertLogger.warn("CRITICAL_DATA_CHANGE: user={} type={} ip={}",
                        event.getUserId(), event.getDetails(), maskIp(event.getIpAddress()));

                // Implementar notificación inmediata para cambios críticos

            } catch (Exception e) {
                logger.error("Error triggering data change alert: {}", e.getMessage());
            }
        });
    }

    private String generateAlertId() {
        return "ALERT-" + System.currentTimeMillis() + "-" +
                Integer.toHexString(new Random().nextInt());
    }

    private void sendSecurityNotification(SecurityAlert alert) {
        // Implementar envío de notificación (email, Slack, etc.)
        logger.info("Security notification would be sent for alert: {}", alert.getAlertId());
    }

    private void storeSecurityEvent(SecurityEvent event) {
        try {
            String key = AUDIT_EVENT_KEY + System.currentTimeMillis() + "_" +
                    event.getEventType() + "_" + event.getIpAddress().hashCode();
            eventStorage.put(key, event);

            // Limpiar eventos antiguos (más de 7 días) para evitar uso excesivo de memoria
            cleanupOldEvents();
        } catch (Exception e) {
            logger.error("Error storing security event: {}", e.getMessage());
        }
    }

    private void storeSecurityAlert(SecurityAlert alert) {
        try {
            String key = "alert:" + alert.getAlertId();
            alertStorage.put(key, alert);

            // Limpiar alertas antiguas (más de 30 días)
            cleanupOldAlerts();
        } catch (Exception e) {
            logger.error("Error storing security alert: {}", e.getMessage());
        }
    }

    private int getTotalActivityCount() {
        return ipFailureCount.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    private int getAverageActivityForHour(int hour) {
        // Implementar cálculo de promedio histórico
        return 10; // Placeholder
    }

    private Map<String, Integer> getEventCountByTimeRange(LocalDateTime since) {
        // Implementar conteo de eventos por tiempo
        return new HashMap<>();
    }

    private int getActiveAlertsCount() {
        // Implementar conteo de alertas activas
        return 0;
    }

    private String sanitizeDetails(String details) {
        if (details == null)
            return "null";
        return details.replaceAll("[\r\n\t]", " ").replaceAll("[<>\"'&]", "").trim();
    }

    private String sanitizeUserAgent(String userAgent) {
        if (userAgent == null)
            return "unknown";
        return userAgent.replaceAll("[<>\"'&]", "").substring(0, Math.min(userAgent.length(), 200));
    }

    private String maskIp(String ip) {
        if (ip == null)
            return "unknown";
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".***." + parts[3];
            }
        }
        return ip;
    }

    private String maskIdentifier(String identifier) {
        if (identifier == null || identifier.length() < 3)
            return "***";
        return identifier.substring(0, 2) + "***";
    }

    private String formatReport(Map<String, Object> report) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : report.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(" ");
        }
        return sb.toString().trim();
    }

    // Clases de datos

    public static class SecurityEvent {
        private final String eventType;
        private final String userId;
        private final String details;
        private final String ipAddress;
        private final String userAgent;
        private final LocalDateTime timestamp;
        private final String severity;

        public SecurityEvent(String eventType, String userId, String details, String ipAddress,
                String userAgent, LocalDateTime timestamp, String severity) {
            this.eventType = eventType;
            this.userId = userId;
            this.details = details;
            this.ipAddress = ipAddress;
            this.userAgent = userAgent;
            this.timestamp = timestamp;
            this.severity = severity;
        }

        // Getters
        public String getEventType() {
            return eventType;
        }

        public String getUserId() {
            return userId;
        }

        public String getDetails() {
            return details;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getSeverity() {
            return severity;
        }
    }

    public static class SecurityAlert {
        private final String alertId;
        private final String eventType;
        private final String severity;
        private final String description;
        private final SecurityEvent relatedEvent;
        private final LocalDateTime timestamp;

        public SecurityAlert(String alertId, String eventType, String severity, String description,
                SecurityEvent relatedEvent, LocalDateTime timestamp) {
            this.alertId = alertId;
            this.eventType = eventType;
            this.severity = severity;
            this.description = description;
            this.relatedEvent = relatedEvent;
            this.timestamp = timestamp;
        }

        // Getters
        public String getAlertId() {
            return alertId;
        }

        public String getEventType() {
            return eventType;
        }

        public String getSeverity() {
            return severity;
        }

        public String getDescription() {
            return description;
        }

        public SecurityEvent getRelatedEvent() {
            return relatedEvent;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}