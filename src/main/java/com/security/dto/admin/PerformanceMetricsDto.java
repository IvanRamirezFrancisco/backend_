package com.security.dto.admin;

/**
 * Métricas de rendimiento en tiempo real del motor PostgreSQL.
 *
 * @param cacheHitRatio  Porcentaje de lecturas servidas desde cache
 *                       (pg_statio_user_tables)
 * @param tps            Operaciones por segundo estimadas (commits + rollbacks
 *                       desde pg_stat_database)
 * @param avgQueryTimeMs Tiempo promedio de query en ms (pg_stat_activity →
 *                       query_start)
 */
public record PerformanceMetricsDto(
        double cacheHitRatio,
        long tps,
        double avgQueryTimeMs) {
}
