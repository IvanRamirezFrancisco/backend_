package com.security.service;

import com.security.dto.admin.MaintenanceConfigDto;
import com.security.dto.admin.TableMaintenanceDto;
import com.security.entity.MaintenanceConfig;
import com.security.entity.MaintenanceLog;
import com.security.repository.MaintenanceConfigRepository;
import com.security.repository.MaintenanceLogRepository;
import com.security.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Servicio de programación de mantenimiento automático.
 *
 * <p>
 * Revisa cada hora si hay tablas que necesiten VACUUM según los umbrales
 * configurados en {@code maintenance_config} y registra cada ejecución
 * automática en {@code maintenance_logs} con
 * {@code executedBy = "SISTEMA_AUTOMATICO"}.
 * </p>
 *
 * <p>
 * <strong>Seguridad:</strong> los nombres de tabla usados en
 * {@code runAutomaticVacuum} provienen de
 * {@link DatabaseMaintenanceService#getDeadTuplesStats()}
 * que a su vez los obtiene de {@code pg_stat_user_tables} — nunca de entrada
 * externa.
 * La validación adicional del whitelist la aplica
 * {@code DatabaseMaintenanceService}
 * en cada llamada a {@code runVacuum()}.
 * </p>
 */
@Service
public class MaintenanceSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceSchedulerService.class);

    private static final String EXECUTOR_SYSTEM = "SISTEMA_AUTOMATICO";

    private final MaintenanceConfigRepository configRepository;
    private final DatabaseMaintenanceService maintenanceService;
    private final MaintenanceLogRepository logRepository;

    public MaintenanceSchedulerService(
            MaintenanceConfigRepository configRepository,
            DatabaseMaintenanceService maintenanceService,
            MaintenanceLogRepository logRepository) {
        this.configRepository = configRepository;
        this.maintenanceService = maintenanceService;
        this.logRepository = logRepository;
    }

    // ── Scheduler ─────────────────────────────────────────────────────────────

    /**
     * Se ejecuta cada hora (fijo después de completar la tarea anterior).
     * Verifica si el scheduler está activo y si ya es hora de ejecutar.
     */
    @Scheduled(fixedDelay = 3_600_000) // 1 hora en ms
    public void checkAndRunMaintenance() {
        MaintenanceConfig config = configRepository.findById(1L).orElse(null);
        if (config == null || !config.isEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // Aún no es hora si la próxima ejecución está en el futuro
        if (config.getNextScheduledExecution() != null
                && now.isBefore(config.getNextScheduledExecution())) {
            log.debug("[Scheduler] Mantenimiento automático omitido: próxima ejecución en {}",
                    config.getNextScheduledExecution());
            return;
        }

        log.info("[Scheduler] Iniciando ciclo de mantenimiento automático…");
        runAutomaticVacuum(config);

        // Calcular la próxima ejecución alineada a la hora preferida
        LocalDateTime next = computeNextExecution(now, config.getFrequencyHours(), config.getPreferredHour());
        config.setLastAutoExecution(now);
        config.setNextScheduledExecution(next);
        config.setUpdatedAt(now);
        configRepository.save(config);
        log.info("[Scheduler] Ciclo completado. Próxima ejecución programada: {}", next);
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Ejecuta el mantenimiento automático de forma inmediata y recalcula
     * la próxima ejecución a partir de ahora.
     * Lanzado por el endpoint {@code POST /automation/run-now}.
     */
    public void runNow() {
        MaintenanceConfig config = configRepository.findById(1L)
                .orElseGet(this::createDefaultConfig);

        log.info("[Scheduler] Ejecución manual inmediata solicitada por admin.");
        runAutomaticVacuum(config);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = computeNextExecution(now, config.getFrequencyHours(), config.getPreferredHour());
        config.setLastAutoExecution(now);
        config.setNextScheduledExecution(next);
        config.setUpdatedAt(now);
        configRepository.save(config);
        log.info("[Scheduler] Ejecución inmediata completada. Próxima programada: {}", next);
    }

    /**
     * Lee la configuración y la mapea a {@link MaintenanceConfigDto}.
     * Si no existe, crea el registro por defecto.
     */
    public MaintenanceConfigDto getConfig() {
        MaintenanceConfig config = configRepository.findById(1L)
                .orElseGet(this::createDefaultConfig);
        return toDto(config);
    }

    /**
     * Actualiza los parámetros de configuración.
     * Cuando se activa ({@code enabled=true}) y aún no hay
     * {@code nextScheduledExecution}, calcula la primera.
     */
    public MaintenanceConfigDto updateConfig(MaintenanceConfigDto dto) {
        MaintenanceConfig config = configRepository.findById(1L)
                .orElseGet(this::createDefaultConfig);

        config.setEnabled(dto.enabled());
        config.setFrequencyHours(dto.frequencyHours());
        config.setPreferredHour(dto.preferredHour());
        config.setVacuumThresholdDeadTuples(dto.vacuumThresholdDeadTuples());
        config.setVacuumThresholdBloatPct(dto.vacuumThresholdBloatPct());
        config.setUpdatedAt(LocalDateTime.now());

        // Si se activó y no hay próxima ejecución programada, calcularla
        if (dto.enabled() && config.getNextScheduledExecution() == null) {
            config.setNextScheduledExecution(
                    computeNextExecution(LocalDateTime.now(), dto.frequencyHours(), dto.preferredHour()));
        }
        // Si se desactivó, limpiar la próxima ejecución
        if (!dto.enabled()) {
            config.setNextScheduledExecution(null);
        }

        configRepository.save(config);
        log.info("[Scheduler] Configuración actualizada: enabled={}, freq={}h, deadTuples>={}, bloat>={}%",
                dto.enabled(), dto.frequencyHours(),
                dto.vacuumThresholdDeadTuples(), dto.vacuumThresholdBloatPct());
        return toDto(config);
    }

    // ── Lógica interna ────────────────────────────────────────────────────────

    /**
     * Obtiene las tablas con dead tuples, filtra las que superan AL MENOS UNO
     * de los dos umbrales y ejecuta VACUUM ANALYZE en cada una.
     *
     * <p>
     * La condición es OR (no AND) de forma intencional:
     * <ul>
     * <li>Una tabla con muchos dead tuples pero bajo bloat (tabla muy grande)
     * necesita VACUUM igual — ignorarla sería un error.</li>
     * <li>Una tabla con alto bloat pero pocos dead tuples en número absoluto
     * (tabla pequeña) también se beneficia de la limpieza.</li>
     * </ul>
     * El AND conservador tiene sentido para el badge visual del UI, pero para
     * la decisión de actuar cualquier señal de alerta es suficiente.
     * </p>
     */
    private void runAutomaticVacuum(MaintenanceConfig config) {
        List<TableMaintenanceDto> tables = maintenanceService.getDeadTuplesStats();
        int vacuumed = 0;

        for (TableMaintenanceDto table : tables) {
            boolean exceedsDeadTuples = table.deadTuples() >= config.getVacuumThresholdDeadTuples().longValue();
            boolean exceedsBloat = BigDecimal.valueOf(table.bloatPercent())
                    .compareTo(config.getVacuumThresholdBloatPct()) >= 0;

            // Actuar si CUALQUIERA de las dos señales supera su umbral (OR)
            if (!exceedsDeadTuples && !exceedsBloat) {
                continue;
            }

            String tableName = table.tableName();
            log.info("[Scheduler] Tabla '{}' supera umbrales (dead={}, bloat={}%). Ejecutando VACUUM ANALYZE…",
                    LogSanitizer.sanitize(tableName), table.deadTuples(), table.bloatPercent());

            MaintenanceLog entry = new MaintenanceLog();
            entry.setOperation("VACUUM_ANALYZE");
            entry.setTargetName(tableName);
            entry.setTargetType("TABLE");
            entry.setExecutedBy(EXECUTOR_SYSTEM);
            entry.setExecutedAt(LocalDateTime.now());
            entry.setRowsBefore(table.deadTuples().intValue());
            entry.setStatus("IN_PROGRESS");
            entry = logRepository.save(entry);

            long start = System.currentTimeMillis();
            try {
                // runVacuum valida contra ALLOWED_TABLES internamente
                maintenanceService.runVacuumSilent(tableName, EXECUTOR_SYSTEM);
                long duration = System.currentTimeMillis() - start;

                // Refrescar dead tuples después del vacuum
                Integer deadAfter = maintenanceService.queryDeadTuplesPublic(tableName);
                entry.setRowsAfter(deadAfter != null ? deadAfter : 0);
                entry.setDurationMs((int) duration);
                entry.setStatus("SUCCESS");
                logRepository.save(entry);
                vacuumed++;
                log.info("[Scheduler] VACUUM ANALYZE en '{}' completado en {} ms",
                        LogSanitizer.sanitize(tableName), duration);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - start;
                entry.setDurationMs((int) duration);
                entry.setStatus("ERROR");
                entry.setErrorMessage(e.getMessage());
                logRepository.save(entry);
                log.error("[Scheduler] Error en VACUUM ANALYZE '{}': {}",
                        LogSanitizer.sanitize(tableName), e.getMessage());
            }
        }

        log.info("[Scheduler] Ciclo de VACUUM automático: {}/{} tablas limpiadas.",
                vacuumed, tables.size());
    }

    /**
     * Calcula la próxima fecha/hora de ejecución alineada a la hora preferida.
     *
     * <p>
     * Algoritmo:
     * </p>
     * <ol>
     * <li>Parte de {@code now} y avanza en saltos de {@code frequencyHours}
     * hasta encontrar un slot cuya hora del día sea exactamente
     * {@code preferredHour}:00.</li>
     * <li>Si la frecuencia es divisor de 24 (1h, 2h, 3h, 4h, 6h, 8h, 12h, 24h)
     * siempre existe un slot alineado; se busca el primero que sea
     * {@code > now}.</li>
     * <li>Si la frecuencia NO es divisor de 24 (p.ej. 5h, 7h) no existe slot
     * perfectamente alineado cada día, por lo que se usa el slot que caiga
     * dentro del margen de ±{@code frequencyHours/2} de la hora preferida,
     * o en su defecto simplemente {@code now + frequencyHours}.</li>
     * </ol>
     *
     * <p>
     * <strong>Ejemplo:</strong> {@code now=15:23, freq=6h, preferredHour=2}
     * → candidatos: 2:00, 8:00, 14:00, 20:00 (slots de 6h alineados a las 2:00).
     * El primero &gt; 15:23 es las 20:00. Resultado: {@code hoy 20:00}.
     * </p>
     *
     * @param now            momento actual
     * @param frequencyHours intervalo entre ejecuciones (1–24)
     * @param preferredHour  hora del día deseada (0–23)
     * @return próxima {@link LocalDateTime} de ejecución
     */
    static LocalDateTime computeNextExecution(LocalDateTime now,
            int frequencyHours,
            int preferredHour) {
        // Construir el primer slot del día actual a la hora preferida
        LocalDateTime base = now.toLocalDate().atTime(preferredHour, 0);

        // Si freq es divisor de 24 → hay slots exactos alineados a preferredHour
        if (24 % frequencyHours == 0) {
            // Avanzar en saltos de frequencyHours desde base hasta superar now
            while (!base.isAfter(now)) {
                base = base.plusHours(frequencyHours);
            }
            return base;
        }

        // freq no es divisor de 24 (p.ej. 5h, 7h): buscar el slot más cercano
        // a la hora preferida que sea > now, dentro de la ventana razonable (7 días)
        LocalDateTime candidate = base;
        int margin = Math.max(1, frequencyHours / 2);
        for (int i = 0; i < 7 * 24; i++) {
            if (candidate.isAfter(now) && Math.abs(candidate.getHour() - preferredHour) <= margin) {
                return candidate;
            }
            candidate = candidate.plusHours(frequencyHours);
        }

        // Fallback seguro: now + frequencyHours
        return now.plusHours(frequencyHours);
    }

    /**
     * Crea (o recupera) el registro de configuración id=1 por defecto.
     *
     * <p>
     * Establece {@code id=1} explícitamente para que Hibernate emita un
     * {@code MERGE} (UPDATE si ya existe, INSERT si no) en lugar de un INSERT
     * ciego. Esto garantiza idempotencia: si Flyway ya sembró la fila,
     * {@code save()} con id conocido llama a {@code EntityManager.merge()},
     * que devuelve la entidad existente sin duplicar filas.
     * </p>
     */
    private MaintenanceConfig createDefaultConfig() {
        MaintenanceConfig cfg = new MaintenanceConfig();
        cfg.setId(1L); // merge: UPDATE si ya existe, INSERT de emergencia si no
        return configRepository.save(cfg);
    }

    // ── Conversión ────────────────────────────────────────────────────────────

    private MaintenanceConfigDto toDto(MaintenanceConfig c) {
        return new MaintenanceConfigDto(
                c.isEnabled(),
                c.getFrequencyHours(),
                c.getPreferredHour(),
                c.getVacuumThresholdDeadTuples(),
                c.getVacuumThresholdBloatPct(),
                c.getLastAutoExecution(),
                c.getNextScheduledExecution(),
                formatNextExecution(c.getNextScheduledExecution()),
                formatLastExecution(c.getLastAutoExecution()));
    }

    /**
     * Formatea el tiempo restante hasta la próxima ejecución, p.ej. "en 3h 42min".
     */
    private String formatNextExecution(LocalDateTime next) {
        if (next == null)
            return "No programada";
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(next))
            return "Pendiente";
        long totalMinutes = ChronoUnit.MINUTES.between(now, next);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0)
            return String.format("en %dh %dmin", hours, minutes);
        if (hours > 0)
            return String.format("en %dh", hours);
        return String.format("en %d min", minutes);
    }

    /**
     * Formatea el tiempo transcurrido desde la última ejecución, p.ej. "hace 2
     * horas".
     */
    private String formatLastExecution(LocalDateTime last) {
        if (last == null)
            return "Nunca ejecutado";
        long seconds = ChronoUnit.SECONDS.between(last, LocalDateTime.now());
        if (seconds < 60)
            return "hace " + seconds + " segundo" + (seconds == 1 ? "" : "s");
        long minutes = seconds / 60;
        if (minutes < 60)
            return "hace " + minutes + " minuto" + (minutes == 1 ? "" : "s");
        long hours = minutes / 60;
        if (hours < 24)
            return "hace " + hours + " hora" + (hours == 1 ? "" : "s");
        long days = hours / 24;
        return "hace " + days + " día" + (days == 1 ? "" : "s");
    }
}
