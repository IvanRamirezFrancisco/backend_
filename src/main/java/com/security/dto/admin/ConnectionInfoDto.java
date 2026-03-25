package com.security.dto.admin;

import java.util.List;

/**
 * Estado detallado de las conexiones a la base de datos.
 *
 * @param total                 Total de conexiones abiertas en la BD actual
 * @param maxLimit              Límite configurado (max_connections)
 * @param usagePct              Porcentaje uso: total / maxLimit * 100
 * @param poolConnections       Conexiones del pool HikariCP (idle o en uso por
 *                              Spring)
 * @param pgInternalConnections Procesos internos de PostgreSQL
 * @param activeUserSessions    Número de sesiones con actividad en los últimos
 *                              30 min
 * @param adminTools            Conexiones de herramientas de administración
 *                              (pgAdmin, DBeaver, DataGrip, TablePlus, Postico)
 * @param sessionsLastHour      Número de sesiones con actividad en la última
 *                              hora
 * @param sessionsToday         Número de sesiones con actividad hoy (desde
 *                              medianoche)
 * @param userSessions          Lista paginada (máx. 200) de sesiones de usuario
 */
public record ConnectionInfoDto(
                int total,
                int maxLimit,
                double usagePct,
                int poolConnections,
                int pgInternalConnections,
                int activeUserSessions,
                int adminTools,
                int sessionsLastHour,
                int sessionsToday,
                List<UserSessionDto> userSessions) {
}
