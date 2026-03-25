package com.security.dto.admin;

/**
 * Información de una sesión de usuario activa en la aplicación.
 * Los datos provienen de la tabla {@code active_sessions}, no de
 * pg_stat_activity,
 * porque HTTP es stateless y los usuarios no tienen conexiones persistentes a
 * PostgreSQL.
 *
 * @param usuario         Nombre completo del usuario (firstName + lastName)
 * @param email           Email del usuario
 * @param ipAddress       IP desde la que inició sesión (puede ser null)
 * @param userAgent       Navegador / dispositivo (User-Agent del token JWT)
 * @param lastActivity    Última actividad en formato legible ("hace X min")
 * @param secondsInactive Segundos desde la última actividad
 * @param sessionCount    Número de sesiones activas de este usuario
 */
public record UserSessionDto(
        String usuario,
        String email,
        String ipAddress,
        String userAgent,
        String lastActivity,
        long secondsInactive,
        int sessionCount) {
}
