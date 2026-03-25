package com.security.dto.admin;

/**
 * Estado de salud de una tabla individual.
 *
 * @param tableName      Nombre de la tabla (relname)
 * @param estimatedRows  Filas vivas estimadas (n_live_tup)
 * @param deadTuples     Filas muertas pendientes de limpieza (n_dead_tup)
 * @param bloatPercent   Porcentaje de espacio desperdiciado (dead / (live+dead)
 *                       * 100)
 * @param lastAutoVacuum Fecha/hora del VACUUM más reciente — ya sea manual
 *                       ({@code last_vacuum}) o automático
 *                       ({@code last_autovacuum}), el que ocurrió más tarde.
 *                       Equivale a
 *                       {@code GREATEST(last_vacuum, last_autovacuum)}.
 *                       Puede ser {@code null} si nunca se ejecutó ninguno.
 * @param status         "optimal" | "warning" | "critical"
 */
public record TableHealthDto(
                String tableName,
                long estimatedRows,
                long deadTuples,
                double bloatPercent,
                String lastAutoVacuum,
                String status) {
}
