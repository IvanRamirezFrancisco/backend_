package com.security.service;

import com.security.service.IndexEvaluator.EvaluationContext;
import com.security.service.IndexEvaluator.IndexVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitarios de {@link IndexEvaluator}.
 *
 * <p>
 * Cada grupo de tests cubre una regla de evaluación concreta, en el mismo
 * orden de precedencia que la implementación.
 * </p>
 */
@DisplayName("IndexEvaluator")
class IndexEvaluatorTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Crea un contexto con todas las flags en false (caso base normal). */
    private static EvaluationContext ctx(long idxScan, long seqScan, long liveRows) {
        return new EvaluationContext(idxScan, seqScan, liveRows, false, false);
    }

    private static EvaluationContext constraintCtx(long idxScan, long seqScan, long liveRows) {
        return new EvaluationContext(idxScan, seqScan, liveRows, true, false);
    }

    private static EvaluationContext rebuiiltCtx(long idxScan, long seqScan, long liveRows) {
        return new EvaluationContext(idxScan, seqScan, liveRows, false, true);
    }

    // ── Regla 0: null guard ───────────────────────────────────────────────────

    @Test
    @DisplayName("null context → IllegalArgumentException")
    void nullContext_throwsIllegalArgument() {
        assertThatThrownBy(() -> IndexEvaluator.evaluate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    // ── Regla 1: índices de constraint ───────────────────────────────────────

    @Nested
    @DisplayName("Regla 1 — Constraint index (PK / UNIQUE)")
    class ConstraintIndexTests {

        @Test
        @DisplayName("PK con idx_scan=0 y sin tráfico → ACTIVE (nunca reportar como unused)")
        void pkWithZeroScans_isActive() {
            EvaluationContext ctx = constraintCtx(0, 0, 0);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.ACTIVE);
        }

        @Test
        @DisplayName("PK con tráfico alto y efficiency=0% → ACTIVE (constraint tiene prioridad)")
        void pkWithHighTrafficZeroIdx_isActive() {
            // seq_scan=5000, idx_scan=0 → efficiency=0%, pero es constraint
            EvaluationContext ctx = constraintCtx(0, 5_000, 10_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.ACTIVE);
        }

        @Test
        @DisplayName("UNIQUE con baja eficiencia → ACTIVE (constraint tiene prioridad)")
        void uniqueWithLowEfficiency_isActive() {
            // efficiency = 10 / (10+500) = ~2%
            EvaluationContext ctx = constraintCtx(10, 500, 1_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.ACTIVE);
        }
    }

    // ── Regla 2: reconstruido recientemente ───────────────────────────────────

    @Nested
    @DisplayName("Regla 2 — Reconstruido recientemente (REINDEX últimas 24h)")
    class RecentlyRebuiltTests {

        @Test
        @DisplayName("REINDEX reciente con idx_scan=0 → ACTIVE (contadores recién reseteados)")
        void recentlyRebuilt_zeroScans_isActive() {
            EvaluationContext ctx = rebuiiltCtx(0, 500, 1_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.ACTIVE);
        }

        @Test
        @DisplayName("REINDEX reciente con eficiencia=0% → ACTIVE")
        void recentlyRebuilt_zeroEfficiency_isActive() {
            EvaluationContext ctx = rebuiiltCtx(0, 5_000, 50_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.ACTIVE);
        }
    }

    // ── Regla 3: datos insuficientes ──────────────────────────────────────────

    @Nested
    @DisplayName("Regla 3 — Datos insuficientes (tráfico < MIN_TRAFFIC o liveRows < MIN_LIVE_ROWS)")
    class InsufficientDataTests {

        @Test
        @DisplayName("totalTraffic=50 (< 100) → INSUFFICIENT_DATA")
        void lowTraffic_insufficientData() {
            // idx=20, seq=30 → total=50 < INDEX_MIN_TRAFFIC=100
            EvaluationContext ctx = ctx(20, 30, 200);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.INSUFFICIENT_DATA);
        }

        @Test
        @DisplayName("totalTraffic=99 (exactamente bajo el umbral) → INSUFFICIENT_DATA")
        void trafficJustBelowThreshold_insufficientData() {
            EvaluationContext ctx = ctx(50, 49, 200); // total = 99
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.INSUFFICIENT_DATA);
        }

        @Test
        @DisplayName("liveRows=0 con tráfico alto → INSUFFICIENT_DATA (tabla vacía)")
        void emptyTableHighTraffic_insufficientData() {
            // tráfico alto pero tabla sin filas vivas (puede ser tabla recién creada o
            // truncada)
            EvaluationContext ctx = ctx(0, 500, 0);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.INSUFFICIENT_DATA);
        }

        @Test
        @DisplayName("liveRows=49 (< MIN_LIVE_ROWS=50) → INSUFFICIENT_DATA")
        void liveRowsJustBelowThreshold_insufficientData() {
            EvaluationContext ctx = ctx(0, 500, 49);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.INSUFFICIENT_DATA);
        }

        @Test
        @DisplayName("totalTraffic=0 (servidor recién reiniciado) → INSUFFICIENT_DATA")
        void serverRestarted_zeroTraffic_insufficientData() {
            EvaluationContext ctx = ctx(0, 0, 5_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.INSUFFICIENT_DATA);
        }
    }

    // ── Regla 4: unused con evidencia real ────────────────────────────────────

    @Nested
    @DisplayName("Regla 4 — UNUSED_CONFIRMED (tráfico real, idx_scan=0)")
    class UnusedConfirmedTests {

        @Test
        @DisplayName("totalTraffic=500, idx_scan=0 → UNUSED_CONFIRMED")
        void realTrafficZeroIdx_unusedConfirmed() {
            EvaluationContext ctx = ctx(0, 500, 1_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.UNUSED_CONFIRMED);
        }

        @Test
        @DisplayName("totalTraffic exactamente en el umbral mínimo con idx=0 → UNUSED_CONFIRMED")
        void exactMinTrafficZeroIdx_unusedConfirmed() {
            // total = 100 = INDEX_MIN_TRAFFIC, liveRows = 50 = INDEX_MIN_LIVE_ROWS
            EvaluationContext ctx = ctx(0, 100, 50);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.UNUSED_CONFIRMED);
        }

        @Test
        @DisplayName("tráfico muy alto (10 000 seq) con idx=0 → UNUSED_CONFIRMED")
        void veryHighSeqScanZeroIdx_unusedConfirmed() {
            EvaluationContext ctx = ctx(0, 10_000, 100_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.UNUSED_CONFIRMED);
        }
    }

    // ── Regla 5: eficiencia activa ────────────────────────────────────────────

    @Nested
    @DisplayName("Regla 5 — ACTIVE (eficiencia ≥ INDEX_EFFICIENCY_OK=80%)")
    class ActiveEfficiencyTests {

        @Test
        @DisplayName("efficiency=100% (solo idx_scan) → ACTIVE")
        void perfectEfficiency_isActive() {
            EvaluationContext ctx = ctx(1_000, 0, 5_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.ACTIVE);
        }

        @Test
        @DisplayName("efficiency=80% (exactamente en el umbral) → ACTIVE")
        void exactlyAtThreshold_isActive() {
            // idx=800, seq=200 → 800/1000 = 80%
            EvaluationContext ctx = ctx(800, 200, 5_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.ACTIVE);
        }

        @Test
        @DisplayName("efficiency=95% con tráfico alto → ACTIVE")
        void highEfficiencyHighTraffic_isActive() {
            EvaluationContext ctx = ctx(9_500, 500, 50_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.ACTIVE);
        }
    }

    // ── Regla 5: baja eficiencia ──────────────────────────────────────────────

    @Nested
    @DisplayName("Regla 5 — LOW_EFFICIENCY (eficiencia < INDEX_EFFICIENCY_OK=80%)")
    class LowEfficiencyTests {

        @Test
        @DisplayName("efficiency=60% con tráfico real → LOW_EFFICIENCY")
        void efficiency60_lowEfficiency() {
            // idx=600, seq=400 → 600/1000 = 60%
            EvaluationContext ctx = ctx(600, 400, 5_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.LOW_EFFICIENCY);
        }

        @Test
        @DisplayName("efficiency=79.9% (justo debajo del umbral) → LOW_EFFICIENCY")
        void justBelowThreshold_lowEfficiency() {
            // idx=799, seq=201 → 799/1000 = 79.9%
            EvaluationContext ctx = ctx(799, 201, 5_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.LOW_EFFICIENCY);
        }

        @Test
        @DisplayName("efficiency=40% (debajo de CRITICAL=50%) → LOW_EFFICIENCY (caller decide el nivel)")
        void belowCriticalThreshold_stillLowEfficiency() {
            // El evaluador devuelve LOW_EFFICIENCY; el caller distingue
            // warning vs critical usando INDEX_EFFICIENCY_CRITICAL
            EvaluationContext ctx = ctx(400, 600, 5_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.LOW_EFFICIENCY);
        }

        @Test
        @DisplayName("efficiency=1% (prácticamente inutilizado pero idx_scan > 0) → LOW_EFFICIENCY")
        void nearZeroEfficiency_lowEfficiency() {
            // Diferente de UNUSED_CONFIRMED: aquí idx_scan=10 > 0
            EvaluationContext ctx = ctx(10, 990, 5_000);
            assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(IndexVerdict.LOW_EFFICIENCY);
        }
    }

    // ── Tests parametrizados de casos límite ──────────────────────────────────

    @ParameterizedTest(name = "idx={0}, seq={1}, liveRows={2} → {3}")
    @CsvSource({
            // idx, seq, liveRows, expectedVerdict
            "0,     0,   10000, INSUFFICIENT_DATA", // servidor recién iniciado, tabla con datos
            "0,     99,  1000,  INSUFFICIENT_DATA", // justo bajo MIN_TRAFFIC
            "0,     100, 49,    INSUFFICIENT_DATA", // justo bajo MIN_LIVE_ROWS
            "0,     100, 50,    UNUSED_CONFIRMED", // exactamente en ambos umbrales
            "1,     999, 1000,  LOW_EFFICIENCY", // efficiency=0.1%
            "800,   200, 1000,  ACTIVE", // efficiency=80%
            "10000, 0,   1000,  ACTIVE", // efficiency=100%
    })
    @DisplayName("Tabla de casos límite parametrizados")
    void parametrizedBoundaryTests(long idxScan, long seqScan, long liveRows, String expectedName) {
        IndexVerdict expected = IndexVerdict.valueOf(expectedName);
        EvaluationContext ctx = ctx(idxScan, seqScan, liveRows);
        assertThat(IndexEvaluator.evaluate(ctx)).isEqualTo(expected);
    }
}
