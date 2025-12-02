package com.security.service;

import com.security.entity.ActiveSession;
import com.security.entity.User;
import com.security.repository.ActiveSessionRepository;
import com.security.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio para manejar sesiones activas y cumplir requisitos de rúbrica:
 * 1. Sesiones expiradas por inactividad (>15 min)
 * 2. Revocación de sesiones en múltiples dispositivos
 */
@Service
@Transactional
public class SessionManagementService {

    @Autowired
    private ActiveSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    // Configuración desde application.yml
    @Value("${app.security.session.max-concurrent-sessions:2}")
    private int maxConcurrentSessions;

    @Value("${app.security.session.inactivity-timeout-minutes:15}")
    private int inactivityTimeoutMinutes;

    /**
     * REQUISITO 1: Crea nueva sesión y maneja límite de sesiones concurrentes
     * REQUISITO 2: Invalida sesiones antiguas cuando excede límite
     */
    public String createSession(String userEmail, LocalDateTime tokenExpiry, HttpServletRequest request) {
        // Buscar usuario
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userEmail));

        String jti = UUID.randomUUID().toString();
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        // Verificar límite de sesiones concurrentes
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime inactivityThreshold = now.minusMinutes(inactivityTimeoutMinutes);
        
        List<ActiveSession> existingSessions = sessionRepository.findValidAndActiveSessionsByUser(
            user, now, inactivityThreshold);
        
        System.out.println("🔍 Usuario " + userEmail + " tiene " + existingSessions.size() + 
                          " sesiones válidas (límite: " + maxConcurrentSessions + ")");

        // Si excede el límite, invalidar las sesiones más antiguas
        if (existingSessions.size() >= maxConcurrentSessions) {
            List<ActiveSession> oldestSessions = sessionRepository.findOldestSessionsByUser(user);
            
            int sessionsToInvalidate = (existingSessions.size() - maxConcurrentSessions) + 1;
            System.out.println("🔒 Invalidando " + sessionsToInvalidate + " sesiones más antiguas");
            
            for (int i = 0; i < sessionsToInvalidate && i < oldestSessions.size(); i++) {
                ActiveSession oldSession = oldestSessions.get(i);
                
                if (!oldSession.getRevoked()) {
                    // Revocar la sesión en la base de datos
                    sessionRepository.revokeSession(oldSession.getId());
                    
                    System.out.println("❌ Sesión " + oldSession.getJwtTokenId() + 
                                     " invalidada por límite de sesiones para " + userEmail);
                }
            }
        }

        // Crear nueva sesión
        ActiveSession newSession = new ActiveSession(user, jti, ipAddress, userAgent, tokenExpiry);
        sessionRepository.save(newSession);

        System.out.println("✅ Nueva sesión creada: " + jti + " para " + userEmail + 
                          " (Dispositivo: " + extractDeviceInfo(userAgent) + ")");

        return jti;
    }

    /**
     * REQUISITO 1: Valida si una sesión está activa y no ha expirado por inactividad
     */
    public boolean isSessionValid(String jti) {
        Optional<ActiveSession> sessionOpt = sessionRepository.findByJwtTokenId(jti);
        
        if (sessionOpt.isEmpty()) {
            System.out.println("❌ Sesión no encontrada: " + jti);
            return false;
        }

        ActiveSession session = sessionOpt.get();
        
        if (session.getRevoked()) {
            System.out.println("❌ Sesión revocada: " + jti);
            return false;
        }

        // Verificar expiración por inactividad (>15 min)
        if (session.isInactive(inactivityTimeoutMinutes)) {
            // Sesión expirada por inactividad
            sessionRepository.revokeSession(session.getId());
            
            System.out.println("⏰ Sesión " + jti + " expirada por inactividad (>" + inactivityTimeoutMinutes + " min)");
            return false;
        }

        // Verificar expiración normal del token
        if (session.isExpired()) {
            sessionRepository.revokeSession(session.getId());
            
            System.out.println("⏰ Sesión " + jti + " expirada normalmente");
            return false;
        }

        return true;
    }

    /**
     * REQUISITO 1: Actualiza actividad de sesión (resetea contador de inactividad)
     */
    public void updateSessionActivity(String jti) {
        LocalDateTime now = LocalDateTime.now();
        sessionRepository.updateLastActivity(jti, now);
        // Solo para debug en desarrollo
        // System.out.println("🔄 Actividad actualizada para sesión: " + jti + " a " + now);
    }

    /**
     * REQUISITO 2: Cierra sesión específica (logout en un dispositivo)
     */
    public void invalidateSession(String jti) {
        sessionRepository.revokeByTokenId(jti);
        
        System.out.println("🚪 Sesión " + jti + " cerrada manualmente");
    }

    /**
     * REQUISITO 2: Cierra todas las sesiones de un usuario (logout from all devices)
     */
    public void invalidateAllUserSessions(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userEmail));
                
        List<ActiveSession> userSessions = sessionRepository.findValidSessionsByUser(user, LocalDateTime.now());
        
        // Revocar todas en la base de datos
        sessionRepository.revokeAllUserSessions(user);
        
        System.out.println("🔒 Todas las sesiones cerradas para " + userEmail + " (" + userSessions.size() + " sesiones)");
    }

    /**
     * Obtiene información de sesiones activas para un usuario
     */
    public List<ActiveSession> getUserActiveSessions(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userEmail));
                
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime inactivityThreshold = now.minusMinutes(inactivityTimeoutMinutes);
        
        return sessionRepository.findValidAndActiveSessionsByUser(user, now, inactivityThreshold);
    }

    /**
     * Cuenta sesiones activas para un usuario
     */
    public long getActiveSessionCount(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userEmail));
                
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime inactivityThreshold = now.minusMinutes(inactivityTimeoutMinutes);
        
        return sessionRepository.countActiveAndNotInactiveSessionsByUserId(
            user.getId(), now, inactivityThreshold);
    }

    /**
     * SCHEDULED TASK: Limpia sesiones expiradas automáticamente cada 5 minutos
     * REQUISITO 1: Implementa la expiración automática por inactividad
     */
    @Scheduled(fixedRate = 300000) // 5 minutos
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime inactivityThreshold = now.minusMinutes(inactivityTimeoutMinutes);
        
        List<ActiveSession> expiredSessions = sessionRepository.findExpiredOrInactiveSessions(now, inactivityThreshold);
        
        for (ActiveSession session : expiredSessions) {
            sessionRepository.revokeSession(session.getId());
        }
        
        if (!expiredSessions.isEmpty()) {
            System.out.println("🧹 Limpieza automática: " + expiredSessions.size() + " sesiones expiradas eliminadas");
        }

        // Limpieza de registros muy antiguos (más de 7 días)
        LocalDateTime cutoffDate = now.minusDays(7);
        sessionRepository.deleteOldRevokedSessions(cutoffDate);
    }

    /**
     * Obtiene información detallada de una sesión para debugging/auditoría
     */
    public Optional<ActiveSession> getSessionInfo(String jti) {
        return sessionRepository.findByJwtTokenId(jti);
    }

    // Utilidades privadas
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader == null) {
            return request.getRemoteAddr();
        } else {
            return xForwardedForHeader.split(",")[0].trim();
        }
    }

    private String extractDeviceInfo(String userAgent) {
        if (userAgent == null) return "Desconocido";
        
        if (userAgent.contains("Mobile") || userAgent.contains("Android") || userAgent.contains("iPhone")) {
            return "Móvil";
        } else if (userAgent.contains("Tablet") || userAgent.contains("iPad")) {
            return "Tablet";
        } else {
            return "Escritorio";
        }
    }
}