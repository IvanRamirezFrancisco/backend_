package com.security.service;

/**
 * Calculador canónico del estado de salud de una tabla PostgreSQL.
 *
 * <p>
 * Centraliza la lógica duplicada que antes existía en
 * {@link DatabaseMonitoringService} y {@link DatabaseMaintenanceService}.
 * Al usar este único método en ambos servicios se garantiza que el badge
 * de estado en el módulo de Monitoreo sea siempre idéntico al de Mantenimiento.
 * </p>
 *
 * <p>
 * La clase es {@code final} y su constructor es privado — función pura sin
 * estado.
 * </p>
 *
 * <h2>Umbrales</h2>
 * 
 * <pre>
 * critical : dead > TABLE_DEAD_TUPLES_CRITICAL(20) AND bloat > TABLE_BLOAT_PCT_CRITICAL(30%)
 * warning  : dead > TABLE_DEAD_TUPLES_WARNING(10)  AND bloat > TABLE_BLOAT_PCT_WARNING(20%)
 * ok       : cualquier otro caso
 * </pre>
 *
 * <p>
 * La doble condición (AND, no OR) evita falsos positivos: una tabla muy grande
 * puede tener muchos dead tuples en número absoluto pero bajo porcentaje de
 * bloat;
 * una tabla pequeña puede mostrar alto porcentaje con muy pocos registros
 * absolutos.
 * </p>
 */
public final class TableStatusCalculator {

    private TableStatusCalculator() {
        throw new UnsupportedOperationException("Utility class — no instanciar.");
    }

    /**
     * Calcula el estado de mantenimiento de una tabla a partir de sus
     * estadísticas de dead tuples y bloat.
     *
     * @param deadTuples número de filas muertas ({@code n_dead_tup})
     * @param bloatPct   porcentaje de bloat {@code dead / (live + dead) * 100}
     * @return {@code "critical"} | {@code "warning"} | {@code "ok"}
     */
    public static String calculate(long deadTuples, double bloatPct) {
        if (deadTuples > DatabaseThresholds.TABLE_DEAD_TUPLES_CRITICAL
                && bloatPct > DatabaseThresholds.TABLE_BLOAT_PCT_CRITICAL) {
            return "critical";
        }
        if (deadTuples > DatabaseThresholds.TABLE_DEAD_TUPLES_WARNING
                && bloatPct > DatabaseThresholds.TABLE_BLOAT_PCT_WARNING) {
            return "warning";
        }
        return "ok";
    }

    /**
     * Variante que devuelve {@code "optimal"} en lugar de {@code "ok"}.
     * Usada por el módulo de Monitoreo, cuyo contrato con el frontend
     * Angular usa {@code "optimal"} para el estado saludable.
     *
     * @param deadTuples número de filas muertas
     * @param bloatPct   porcentaje de bloat
     * @return {@code "critical"} | {@code "warning"} | {@code "optimal"}
     */
    public static String calculateForMonitoring(long deadTuples, double bloatPct) {
        String raw = calculate(deadTuples, bloatPct);
        return "ok".equals(raw) ? "optimal" : raw;
    }
}
