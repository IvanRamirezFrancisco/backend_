package com.security.service;

/**
 * Constantes compartidas para la evaluación de salud de la base de datos.
 *
 * <p>
 * Centralizar aquí todos los umbrales garantiza que
 * {@link DatabaseMonitoringService}, {@link DatabaseMaintenanceService}
 * y {@link MaintenanceSchedulerService} usen exactamente los mismos valores
 * sin duplicación. Un cambio de umbral en esta clase se propaga
 * automáticamente a todos los módulos.
 * </p>
 *
 * <p>
 * La clase es {@code final} y su constructor es privado para evitar
 * instanciación accidental (patrón "utility class").
 * </p>
 */
public final class DatabaseThresholds {

    private DatabaseThresholds() {
        throw new UnsupportedOperationException("Utility class — no instanciar.");
    }

    // ── Índices ───────────────────────────────────────────────────────────────
    /**
     * Eficiencia = idx_scan / (idx_scan + seq_scan) * 100.
     * Por encima de este valor el índice se considera en uso activo normal.
     */
    public static final int INDEX_EFFICIENCY_OK = 80;

    /**
     * Entre {@code INDEX_EFFICIENCY_WARNING} y {@code INDEX_EFFICIENCY_OK}
     * el índice tiene baja eficiencia pero aún aporta valor.
     */
    public static final int INDEX_EFFICIENCY_WARNING = 75;

    /**
     * Por debajo de este valor con tráfico real el índice es candidato
     * urgente a reconstrucción (REINDEX).
     */
    public static final int INDEX_EFFICIENCY_CRITICAL = 50;

    /**
     * Tráfico mínimo (idx_scan + seq_scan) para que las métricas de
     * eficiencia sean estadísticamente confiables.
     * Un índice con solo 3 escaneos totales no puede calificarse como
     * "ineficiente" — los datos son insuficientes.
     */
    public static final int INDEX_MIN_TRAFFIC = 100;

    /**
     * Número mínimo de filas vivas en la tabla para evaluar sus índices.
     * Tablas casi vacías producen ratios de eficiencia espurios.
     */
    public static final int INDEX_MIN_LIVE_ROWS = 50;

    /**
     * Nivel de confianza alto: más de 1000 operaciones totales.
     * Con este tráfico el veredicto del evaluador es muy confiable.
     */
    public static final int INDEX_CONFIDENCE_HIGH = 1_000;

    /**
     * Nivel de confianza medio: más de 100 operaciones totales.
     * Suficiente para emitir alertas, pero con menor certeza que HIGH.
     */
    public static final int INDEX_CONFIDENCE_MEDIUM = 100;

    // ── Tablas ────────────────────────────────────────────────────────────────
    /**
     * Dead tuples absolutos y porcentaje de bloat para estado "critical".
     * Ambas condiciones deben cumplirse simultáneamente (AND lógico).
     * La doble condición evita falsos positivos en tablas muy grandes
     * (muchos dead tuples pero bajo porcentaje) o muy pequeñas (alto porcentaje
     * pero pocos registros absolutos).
     */
    public static final int TABLE_DEAD_TUPLES_CRITICAL = 20;
    public static final double TABLE_BLOAT_PCT_CRITICAL = 30.0;

    /**
     * Dead tuples y bloat para estado "warning".
     * Igual que critical pero con umbrales más bajos.
     */
    public static final int TABLE_DEAD_TUPLES_WARNING = 10;
    public static final double TABLE_BLOAT_PCT_WARNING = 20.0;

    /**
     * Días sin VACUUM (ni manual ni autovacuum) con dead tuples > 0
     * para emitir una alerta de mantenimiento "overdue".
     */
    public static final int TABLE_NO_VACUUM_DAYS_ALERT = 7;

    // ── Cache ─────────────────────────────────────────────────────────────────
    /**
     * Cache hit ratio por debajo del cual el sistema está en estado crítico.
     * Indica que PostgreSQL está leyendo la mayoría de bloques desde disco.
     */
    public static final double CACHE_HIT_CRITICAL = 90.0;

    /**
     * Cache hit ratio por debajo del cual se genera una alerta de advertencia.
     * El valor recomendado para producción es ≥ 99 %.
     */
    public static final double CACHE_HIT_WARNING = 95.0;

    // ── Conexiones ────────────────────────────────────────────────────────────
    /** Porcentaje de uso del pool de conexiones para nivel crítico. */
    public static final int CONN_USAGE_CRITICAL = 90;

    /** Porcentaje de uso del pool de conexiones para nivel de advertencia. */
    public static final int CONN_USAGE_WARNING = 70;
}
