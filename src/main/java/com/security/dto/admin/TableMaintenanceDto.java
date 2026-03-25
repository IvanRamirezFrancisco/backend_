package com.security.dto.admin;

/**
 * Estadísticas de mantenimiento de una tabla de usuario en PostgreSQL.
 *
 * @param tableName      Nombre de la tabla ({@code relname} de
 *                       pg_stat_user_tables)
 * @param deadTuples     Tuplas muertas pendientes de limpieza
 *                       ({@code n_dead_tup})
 * @param liveTuples     Tuplas vivas (filas activas) ({@code n_live_tup})
 * @param lastAutovacuum Fecha del último autovacuum ejecutado por el
 *                       <em>daemon</em>
 *                       de PostgreSQL, o "Nunca" si aún no se ha ejecutado
 * @param lastVacuum     Fecha del último VACUUM manual ejecutado por el DBA,
 *                       o "Nunca" si aún no se ha ejecutado
 * @param bloatPercent   Porcentaje estimado de espacio desperdiciado
 *                       (dead / (live + dead) × 100), 0 si live+dead = 0
 * @param status         Estado de la tabla: "ok", "warning" o "critical"
 */
public record TableMaintenanceDto(
                String tableName,
                Long deadTuples,
                Long liveTuples,
                String lastAutovacuum,
                String lastVacuum,
                Double bloatPercent,
                String status) {
}
