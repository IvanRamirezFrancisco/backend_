package com.security.dto.admin;

/**
 * Query costosa según {@code pg_stat_statements} (historial estadístico).
 *
 * @param avgMs        Tiempo promedio de ejecución en ms
 * @param maxMs        Tiempo máximo registrado en ms
 * @param totalMs      Tiempo total acumulado en ms
 * @param calls        Número de ejecuciones
 * @param rows         Total de filas devueltas
 * @param cacheHitPct  Porcentaje de bloques servidos desde cache
 *                     (shared_blks_hit)
 * @param queryPreview Primeros 500 caracteres de la query
 */
public record ExpensiveQueryDto(
        double avgMs,
        double maxMs,
        double totalMs,
        long calls,
        long rows,
        double cacheHitPct,
        String queryPreview) {
}
