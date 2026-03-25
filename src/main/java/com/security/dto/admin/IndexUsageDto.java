package com.security.dto.admin;

/**
 * Estadísticas de uso de un índice PostgreSQL.
 *
 * @param indexName     Nombre del índice (indexrelname)
 * @param tableName     Tabla a la que pertenece (relname)
 * @param indexScans    Número de búsquedas por índice (idx_scan)
 * @param seqScans      Número de búsquedas secuenciales sobre la tabla
 *                      (seq_scan)
 * @param efficiencyPct Porcentaje de eficiencia: idx_scan / (idx_scan +
 *                      seq_scan) * 100
 * @param status        "active" | "unused" | "low-efficiency"
 */
public record IndexUsageDto(
        String indexName,
        String tableName,
        long indexScans,
        long seqScans,
        double efficiencyPct,
        String status) {
}
