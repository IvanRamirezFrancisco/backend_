package com.security.dto.admin;

/**
 * Query activa detectada en {@code pg_stat_activity}.
 *
 * @param pid             ID del proceso PostgreSQL
 * @param username        Usuario que ejecuta la query
 * @param applicationName Nombre de la aplicación cliente (HikariCP, pgAdmin…)
 * @param clientIp        IP de origen de la conexión
 * @param state           Estado de la conexión (active, idle in transaction…)
 * @param waitEventType   Tipo de espera (Lock, IO…) — null si no espera
 * @param waitEvent       Evento de espera específico
 * @param durationSeconds Segundos transcurridos desde query_start
 * @param queryPreview    Primeros 500 caracteres de la query
 * @param queryStart      Timestamp de inicio de la query (ISO)
 * @param classification  Clasificación calculada: NORMAL, WATCH, SLOW, BLOCKED,
 *                        IDLE_TX
 */
public record ActiveQueryDto(
        int pid,
        String username,
        String applicationName,
        String clientIp,
        String state,
        String waitEventType,
        String waitEvent,
        int durationSeconds,
        String queryPreview,
        String queryStart,
        String classification) {
}
