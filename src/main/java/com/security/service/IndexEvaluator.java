package com.security.service;

/**
 * Evaluador de índices PostgreSQL con responsabilidad única (SRP).
 *
 * <h2>Problema que resuelve</h2>
 * <p>
 * {@code pg_stat_user_indexes} acumula estadísticas en memoria y se resetea
 * en cada reinicio del servidor. Un índice recién creado o cuya tabla no ha
 * tenido tráfico todavía mostrará {@code idx_scan = 0}, lo que produciría
 * falsos positivos masivos si se evalúa directamente como "unused".
 * </p>
 *
 * <h2>Solución</h2>
 * <p>
 * El evaluador combina tres fuentes antes de emitir un veredicto:
 * <ol>
 * <li><b>pg_stat_user_indexes</b> — uso reciente (volátil, se resetea).</li>
 * <li><b>pg_constraint</b> — si el índice respalda una PK o UNIQUE constraint,
 * siempre es "ACTIVE" independientemente de sus estadísticas.</li>
 * <li><b>maintenance_logs</b> — si la tabla tuvo un REINDEX exitoso en las
 * últimas 24 h, los contadores son irrelevantes (acaban de resetearse).</li>
 * </ol>
 * </p>
 *
 * <h2>Uso</h2>
 * 
 * <pre>{@code
 * IndexEvaluator.EvaluationContext ctx = new IndexEvaluator.EvaluationContext(
 *         idxScan, seqScan, liveRows, isConstraint, wasRecentlyRebuilt);
 * IndexEvaluator.IndexVerdict verdict = IndexEvaluator.evaluate(ctx);
 * }</pre>
 *
 * <p>
 * La clase es {@code final} y su constructor es privado — función pura sin
 * estado.
 * </p>
 */
public final class IndexEvaluator {

    private IndexEvaluator() {
        throw new UnsupportedOperationException("Utility class — no instanciar.");
    }

    // ── Tipos públicos ────────────────────────────────────────────────────────

    /**
     * Veredicto de la evaluación. Cada valor mapea a una acción específica:
     * <ul>
     * <li>{@code ACTIVE} — sin alerta, el índice funciona correctamente.</li>
     * <li>{@code LOW_EFFICIENCY} — sugerir REINDEX en el módulo de
     * Mantenimiento.</li>
     * <li>{@code UNUSED_CONFIRMED} — candidato a DROP; hay evidencia real de
     * inutilidad.</li>
     * <li>{@code INSUFFICIENT_DATA}— sin tráfico suficiente para juzgar; solo
     * informar,
     * NO generar alerta accionable.</li>
     * </ul>
     */
    public enum IndexVerdict {
        ACTIVE,
        LOW_EFFICIENCY,
        UNUSED_CONFIRMED,
        INSUFFICIENT_DATA
    }

    /**
     * Contexto de evaluación de un índice.
     *
     * @param idxScan            Número de búsquedas por índice ({@code idx_scan}).
     * @param seqScan            Búsquedas secuenciales en la tabla
     *                           ({@code seq_scan}).
     * @param liveRows           Filas vivas en la tabla ({@code n_live_tup}).
     * @param isConstraintIndex  {@code true} si el índice respalda una PK o UNIQUE
     *                           constraint — nunca reportar como unused.
     * @param wasRecentlyRebuilt {@code true} si hubo un REINDEX exitoso en las
     *                           últimas 24 h — los contadores son irrelevantes.
     */
    public record EvaluationContext(
            long idxScan,
            long seqScan,
            long liveRows,
            boolean isConstraintIndex,
            boolean wasRecentlyRebuilt) {
    }

    // ── Lógica de evaluación ──────────────────────────────────────────────────

    /**
     * Evalúa el estado de un índice y devuelve un {@link IndexVerdict}.
     *
     * <p>
     * Las reglas se aplican en orden estricto de precedencia:
     * </p>
     * <ol>
     * <li>Índices de constraint (PK/UNIQUE) → siempre {@code ACTIVE}.</li>
     * <li>Tabla reconstruida recientemente → {@code ACTIVE} (contadores
     * irrelevantes).</li>
     * <li>Sin tráfico mínimo ({@code INDEX_MIN_TRAFFIC}) o sin filas suficientes
     * ({@code INDEX_MIN_LIVE_ROWS}) → {@code INSUFFICIENT_DATA}.</li>
     * <li>Tráfico real pero {@code idxScan == 0} → {@code UNUSED_CONFIRMED}.</li>
     * <li>Eficiencia ≥ {@code INDEX_EFFICIENCY_OK} (80%) → {@code ACTIVE}.</li>
     * <li>Eficiencia menor → {@code LOW_EFFICIENCY}.</li>
     * </ol>
     *
     * @param ctx contexto con los datos del índice
     * @return veredicto de evaluación
     * @throws IllegalArgumentException si {@code ctx} es {@code null}
     */
    public static IndexVerdict evaluate(EvaluationContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("EvaluationContext no puede ser null.");
        }

        // Regla 1: Índices de constraint son siempre válidos.
        // PostgreSQL los usa para garantizar integridad referencial.
        // Pueden tener idx_scan=0 legítimamente (solo se usan internamente
        // para validar INSERT/UPDATE, no para búsquedas de usuario).
        if (ctx.isConstraintIndex()) {
            return IndexVerdict.ACTIVE;
        }

        // Regla 2: Reconstruido recientemente → contadores reseteados,
        // no es posible juzgar eficiencia hasta que acumule tráfico real.
        if (ctx.wasRecentlyRebuilt()) {
            return IndexVerdict.ACTIVE;
        }

        long totalTraffic = ctx.idxScan() + ctx.seqScan();

        // Regla 3: Sin tráfico mínimo estadísticamente confiable.
        // Emitir una alerta con 3 scans totales sería ruido, no señal.
        if (totalTraffic < DatabaseThresholds.INDEX_MIN_TRAFFIC
                || ctx.liveRows() < DatabaseThresholds.INDEX_MIN_LIVE_ROWS) {
            return IndexVerdict.INSUFFICIENT_DATA;
        }

        // Regla 4: La tabla tiene tráfico real, pero el índice NUNCA fue usado.
        // Esto sí es evidencia de que no aporta valor para las consultas actuales.
        if (ctx.idxScan() == 0) {
            return IndexVerdict.UNUSED_CONFIRMED;
        }

        // Regla 5: Calcular eficiencia real y clasificar.
        double efficiency = (ctx.idxScan() * 100.0) / totalTraffic;

        if (efficiency >= DatabaseThresholds.INDEX_EFFICIENCY_OK) {
            return IndexVerdict.ACTIVE;
        }

        // efficiency < INDEX_EFFICIENCY_OK → baja eficiencia (independientemente
        // de si está por encima o debajo de INDEX_EFFICIENCY_CRITICAL,
        // el veredicto es LOW_EFFICIENCY; el nivel de alerta lo decide el caller).
        return IndexVerdict.LOW_EFFICIENCY;
    }
}
