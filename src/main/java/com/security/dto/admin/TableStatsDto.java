package com.security.dto.admin;

/**
 * Estadísticas de carga de una tabla de usuario en PostgreSQL.
 *
 * @param tableName   Nombre de la tabla
 * @param seqScan     Escaneos secuenciales acumulados
 * @param seqTupRead  Filas leídas por escaneos secuenciales
 * @param idxScan     Escaneos por índice acumulados
 * @param idxTupFetch Filas obtenidas por índice
 * @param totalWrites Total de escrituras (INSERT + UPDATE + DELETE)
 * @param idxUsagePct Porcentaje de accesos que usan índice vs seq scan
 * @param liveRows    Filas vivas estimadas (n_live_tup)
 * @param deadRows    Filas muertas (n_dead_tup)
 */
public record TableStatsDto(
        String tableName,
        long seqScan,
        long seqTupRead,
        long idxScan,
        long idxTupFetch,
        long totalWrites,
        double idxUsagePct,
        long liveRows,
        long deadRows) {
}
