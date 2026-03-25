package com.security.dto.admin;

import java.util.List;

/**
 * Métricas completas de salud de la base de datos PostgreSQL.
 * Este DTO es el contrato con el frontend Angular.
 *
 * <p>
 * Todos los campos son calculados a partir de vistas del sistema de PostgreSQL
 * ({@code pg_stat_*}, {@code pg_statio_*}, {@code pg_database}, etc.)
 * </p>
 *
 * @param healthScore            Puntuación global de 0–100 calculada en el
 *                               servicio
 * @param totalDatabaseSizeBytes Tamaño total de la BD en bytes
 *                               (pg_database_size)
 * @param uptimeDays             Días desde el último reinicio del servidor
 *                               (pg_postmaster_start_time)
 * @param postgresVersion        Versión del motor (version())
 * @param activeConnections      Conexiones activas (pg_stat_activity)
 * @param cacheHitRatio          Cache hit ratio global (pg_statio_user_tables)
 * @param topTables              Top 5 tablas por tamaño total
 * @param performance            Métricas de rendimiento en tiempo real
 * @param connections            Estado detallado de las conexiones
 * @param tableHealth            Salud individual de cada tabla de usuario
 * @param indexUsage             Estadísticas de uso de índices
 * @param alerts                 Alertas activas generadas automáticamente
 */
public record DatabaseMetricsDto(
                int healthScore,
                long totalDatabaseSizeBytes,
                long uptimeDays,
                String postgresVersion,
                int activeConnections,
                double cacheHitRatio,
                List<TableMetricDto> topTables,
                PerformanceMetricsDto performance,
                ConnectionInfoDto connections,
                List<TableHealthDto> tableHealth,
                List<IndexUsageDto> indexUsage,
                List<DbAlertDto> alerts) {
}
