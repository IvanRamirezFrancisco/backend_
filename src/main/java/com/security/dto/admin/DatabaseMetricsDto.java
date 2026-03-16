package com.security.dto.admin;

import java.util.List;

/**
 * Métricas generales de la base de datos PostgreSQL.
 *
 * @param totalDatabaseSizeBytes Tamaño total de la BD en bytes  (pg_database_size)
 * @param activeConnections      Número de conexiones activas    (pg_stat_activity)
 * @param cacheHitRatio          Porcentaje de cache hit ratio   (pg_statio_user_tables)
 * @param topTables              Top 5 tablas por tamaño total
 */
public record DatabaseMetricsDto(
        Long                  totalDatabaseSizeBytes,
        Integer               activeConnections,
        Double                cacheHitRatio,
        List<TableMetricDto>  topTables
) {}
