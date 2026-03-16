package com.security.dto.admin;

/**
 * Métricas de una tabla individual de PostgreSQL.
 *
 * @param tableName     Nombre de la tabla (relname)
 * @param totalBytes    Tamaño total de la tabla + índices en bytes
 * @param indexBytes    Tamaño solo de los índices en bytes
 * @param estimatedRows Estimado de filas vivas (n_live_tup de pg_stat_user_tables)
 */
public record TableMetricDto(
        String tableName,
        Long   totalBytes,
        Long   indexBytes,
        Long   estimatedRows
) {}
