package com.security.service;

import com.security.entity.AuditLog;
import com.security.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * Servicio para registrar logs de auditoría de acciones administrativas
 * críticas
 * Cumple con estándares de seguridad Enterprise para trazabilidad
 */
@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Registra un log de auditoría de forma asíncrona
     * NO bloquea la operación principal
     */
    @Async
    @Transactional
    public void logAction(String action, String entityType, Long entityId, String details) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String performedBy = auth != null ? auth.getName() : "SYSTEM";

            AuditLog log = new AuditLog();
            log.setAction(action);
            log.setEventType(action);
            log.setEventDescription(details != null ? details : action);
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setPerformedBy(performedBy);
            log.setDetails(details);
            log.setIsSuccess(true);
            log.setSeverity("INFO");
            log.setStatus("SUCCESS");

            // Capturar IP y User-Agent si está disponible
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                        .getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    log.setIpAddress(getClientIp(request));
                    log.setUserAgent(request.getHeader("User-Agent"));
                }
            } catch (Exception e) {
                logger.warn("No se pudo capturar información de la request: {}", e.getMessage());
            }

            auditLogRepository.save(log);
            logger.info("✅ Audit log registrado: {} - {} - {}", action, entityType, performedBy);

        } catch (Exception e) {
            logger.error("❌ Error al registrar audit log: {}", e.getMessage(), e);
        }
    }

    /**
     * Registra un log de auditoría con información completa (síncrono)
     */
    @Transactional
    public void logActionSync(String action, String entityType, Long entityId, Long performedByUserId,
            String performedBy, String details, String oldValues, String newValues) {
        try {
            AuditLog log = new AuditLog();
            log.setAction(action);
            log.setEventType(action);
            log.setEventDescription(details != null ? details : action);
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setPerformedBy(performedBy);
            log.setPerformedByUserId(performedByUserId);
            log.setDetails(details);
            log.setOldValues(oldValues);
            log.setNewValues(newValues);
            log.setIsSuccess(true);
            log.setSeverity("INFO");
            log.setStatus("SUCCESS");

            // Capturar IP y User-Agent
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                        .getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    log.setIpAddress(getClientIp(request));
                    log.setUserAgent(request.getHeader("User-Agent"));
                }
            } catch (Exception e) {
                logger.warn("No se pudo capturar información de la request: {}", e.getMessage());
            }

            auditLogRepository.save(log);
            logger.info("✅ Audit log registrado (sync): {} - {} - {}", action, entityType, performedBy);

        } catch (Exception e) {
            logger.error("❌ Error al registrar audit log: {}", e.getMessage(), e);
        }
    }

    /**
     * Registra un log de auditoría de acción fallida
     */
    @Async
    @Transactional
    public void logFailedAction(String action, String entityType, Long entityId, String errorMessage) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String performedBy = auth != null ? auth.getName() : "SYSTEM";

            AuditLog log = new AuditLog();
            log.setAction(action);
            log.setEventType(action);
            log.setEventDescription("Acción fallida: " + (errorMessage != null ? errorMessage : action));
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setPerformedBy(performedBy);
            log.setDetails("Acción fallida");
            log.setErrorMessage(errorMessage);
            log.setIsSuccess(false);
            log.setSeverity("ERROR");
            log.setStatus("FAILED");

            // Capturar IP y User-Agent
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                        .getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    log.setIpAddress(getClientIp(request));
                    log.setUserAgent(request.getHeader("User-Agent"));
                }
            } catch (Exception e) {
                logger.warn("No se pudo capturar información de la request: {}", e.getMessage());
            }

            auditLogRepository.save(log);
            logger.warn("⚠️ Audit log de fallo registrado: {} - {} - {}", action, entityType, performedBy);

        } catch (Exception e) {
            logger.error("❌ Error al registrar audit log de fallo: {}", e.getMessage(), e);
        }
    }

    /**
     * Obtener logs con filtros
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getLogsWithFilters(String action, String entityType, String performedBy,
            Boolean isSuccess, LocalDateTime startDate, LocalDateTime endDate,
            Pageable pageable) {
        if (startDate == null) {
            startDate = LocalDateTime.now().minusMonths(1); // Por defecto: último mes
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        return auditLogRepository.findWithFilters(action, entityType, performedBy, isSuccess, startDate, endDate,
                pageable);
    }

    /**
     * Obtener logs de una entidad específica (para ver historial de cambios)
     */
    @Transactional(readOnly = true)
    public java.util.List<AuditLog> getEntityHistory(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId);
    }

    /**
     * Obtener la IP real del cliente (considerando proxies)
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // Si hay múltiples IPs (proxy chain), tomar la primera
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    // ==================== Métodos Helper para logs específicos
    // ====================

    /**
     * Log para creación de usuario
     */
    public void logUserCreation(Long userId, String userEmail, String createdBy) {
        logAction("CREATE_USER", "USER", userId,
                String.format("Usuario %s creado por %s", userEmail, createdBy));
    }

    /**
     * Log para actualización de usuario
     */
    public void logUserUpdate(Long userId, String userEmail, String updatedBy, String changes) {
        logAction("UPDATE_USER", "USER", userId,
                String.format("Usuario %s actualizado por %s. Cambios: %s", userEmail, updatedBy, changes));
    }

    /**
     * Log para desactivación de usuario
     */
    public void logUserDeactivation(Long userId, String userEmail, String deactivatedBy) {
        logAction("DEACTIVATE_USER", "USER", userId,
                String.format("Usuario %s desactivado por %s", userEmail, deactivatedBy));
    }

    /**
     * Log para activación de usuario
     */
    public void logUserActivation(Long userId, String userEmail, String activatedBy) {
        logAction("ACTIVATE_USER", "USER", userId,
                String.format("Usuario %s activado por %s", userEmail, activatedBy));
    }

    /**
     * Log para bloqueo de cuenta
     */
    public void logAccountLock(Long userId, String userEmail, String lockedBy) {
        logAction("LOCK_ACCOUNT", "USER", userId,
                String.format("Cuenta de usuario %s bloqueada por %s", userEmail, lockedBy));
    }

    /**
     * Log para desbloqueo de cuenta
     */
    public void logAccountUnlock(Long userId, String userEmail, String unlockedBy) {
        logAction("UNLOCK_ACCOUNT", "USER", userId,
                String.format("Cuenta de usuario %s desbloqueada por %s", userEmail, unlockedBy));
    }

    /**
     * Log para asignación de roles
     */
    public void logRoleAssignment(Long userId, String userEmail, String roles, String assignedBy) {
        logAction("ASSIGN_ROLES", "USER", userId,
                String.format("Roles %s asignados al usuario %s por %s", roles, userEmail, assignedBy));
    }

    /**
     * Log para creación de rol
     */
    public void logRoleCreation(Long roleId, String roleName, String createdBy) {
        logAction("CREATE_ROLE", "ROLE", roleId,
                String.format("Rol %s creado por %s", roleName, createdBy));
    }

    /**
     * Log para actualización de permisos de rol
     */
    public void logRolePermissionsUpdate(Long roleId, String roleName, String permissions, String updatedBy) {
        logAction("UPDATE_ROLE_PERMISSIONS", "ROLE", roleId,
                String.format("Permisos del rol %s actualizados por %s. Permisos: %s", roleName, updatedBy,
                        permissions));
    }
}
