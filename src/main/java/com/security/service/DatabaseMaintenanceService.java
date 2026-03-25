package com.security.service;

import com.security.dto.admin.MaintenanceLogDto;
import com.security.dto.admin.TableMaintenanceDto;
import com.security.entity.MaintenanceLog;
import com.security.repository.MaintenanceLogRepository;
import com.security.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio para operaciones de mantenimiento manual de la base de datos
 * PostgreSQL.
 *
 * <p>
 * <strong>Importante:</strong> Ningún método de esta clase usa
 * {@code @Transactional}.
 * PostgreSQL prohíbe explícitamente las sentencias {@code VACUUM} y
 * {@code REINDEX DATABASE}
 * dentro de bloques de transacción. Ejecutarlas en un contexto transaccional
 * provoca el error:
 * {@code ERROR: VACUUM cannot run inside a transaction block}.
 * </p>
 *
 * <p>
 * El {@link JdbcTemplate} ejecuta los comandos DDL fuera de transacción por
 * defecto
 * cuando no existe un contexto transaccional activo, lo que es correcto en este
 * caso.
 * </p>
 */
@Service
public class DatabaseMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMaintenanceService.class);

    // ── SQL ───────────────────────────────────────────────────────────────────

    /**
     * Tablas de usuario permitidas para operaciones de mantenimiento.
     * Actúa como whitelist exhaustiva para prevenir SQL Injection (CWE-89):
     * solo se permiten tablas explícitamente listadas. SonarQube reconoce
     * este patrón como protección efectiva — ningún valor externo puede
     * inyectar SQL arbitrario porque la comparación es contra un Set hardcoded.
     *
     * <p>
     * Cubre TODAS las tablas @Entity de la aplicación.
     * </p>
     */
    private static final Set<String> ALLOWED_TABLES = Set.of(
            // ── Usuarios y autorización ──────────────────────────────────────
            "users",
            "roles",
            "permissions",
            "user_roles",
            "role_permissions",
            // ── Sesiones y tokens ────────────────────────────────────────────
            "active_sessions",
            "refresh_tokens",
            "verification_tokens",
            "password_reset_tokens",
            "two_factor_tokens",
            "backup_codes",
            // ── Seguridad / auditoría ────────────────────────────────────────
            "login_attempts",
            "password_recovery_attempts",
            "audit_logs",
            "backup_logs",
            "security_settings",
            // ── Catálogo de productos ────────────────────────────────────────
            "products",
            "categories",
            "brands",
            "product_images",
            "product_attributes",
            "product_price_history",
            "product_reviews",
            "review_helpfulness",
            // ── Pedidos y carrito ────────────────────────────────────────────
            "orders",
            "order_items",
            "cart_items",
            "shopping_carts",
            // ── Otros ────────────────────────────────────────────────────────
            "coupons",
            "coupon_usage",
            "addresses",
            "countries",
            "wishlists");

    // ── SQL estático (sin datos de usuario) ──────────────────────────────────

    /**
     * Obtiene estadísticas de tuplas muertas y vivas SOLO de tablas que tienen
     * registros obsoletos pendientes de limpieza (n_dead_tup > 0).
     * Query completamente estático — sin datos de usuario.
     *
     * <p>
     * Filtra las tablas con 0 dead tuples para que el frontend de Mantenimiento
     * solo muestre lo que realmente necesita acción. El módulo de Monitoreo usa
     * su propio query sin filtro en {@code DatabaseMonitoringService}.
     * </p>
     */
    private static final String SQL_DEAD_TUPLES = "SELECT relname, " +
            "       n_dead_tup, " +
            "       n_live_tup, " +
            "       COALESCE(cast(last_autovacuum AS TEXT), 'Nunca') AS last_autovacuum, " +
            "       COALESCE(cast(last_vacuum     AS TEXT), 'Nunca') AS last_vacuum, " +
            "       ROUND(n_dead_tup::numeric * 100.0 / (n_dead_tup + n_live_tup), 1) " +
            "           AS bloat_percent " +
            "FROM pg_stat_user_tables " +
            "WHERE n_dead_tup > 0 " +
            "ORDER BY n_dead_tup DESC";

    /**
     * Obtiene SOLO los índices que realmente necesitan reconstrucción, aplicando
     * cuatro condiciones estrictas para eliminar falsos positivos:
     * <ol>
     * <li>idx_scan &gt; 0 — el índice debe haber sido usado al menos una vez;
     * los nunca usados producen efficiency_pct=0 mediante la fórmula pero
     * no están fragmentados, solo son candidatos a DROP.</li>
     * <li>Eficiencia &lt; 75 % (muchas búsquedas secuenciales vs. por índice)</li>
     * <li>Tráfico real ≥ 100 accesos totales (excluye tablas sin actividad)</li>
     * <li>Tabla con datos reales (n_live_tup &gt; 10)</li>
     * </ol>
     * El CTE con ROW_NUMBER garantiza que por cada tabla solo se expone
     * el índice de MENOR eficiencia, evitando que una tabla con mucho tráfico
     * llene la lista con todas sus columnas indexadas.
     *
     * Query completamente estático — sin datos de usuario.
     */
    private static final String SQL_PROBLEMATIC_INDEXES = "WITH ranked AS ( " +
            "  SELECT i.indexrelname AS index_name, " +
            "         i.relname      AS table_name, " +
            "         i.idx_scan, " +
            "         t.seq_scan, " +
            "         ROUND(" +
            "             i.idx_scan::numeric * 100.0 / NULLIF(i.idx_scan + t.seq_scan, 0), 1" +
            "         ) AS efficiency_pct, " +
            "         t.n_live_tup, " +
            "         ROW_NUMBER() OVER (" +
            "             PARTITION BY i.relname " +
            "             ORDER BY i.idx_scan::numeric * 100.0 / NULLIF(i.idx_scan + t.seq_scan, 0) ASC" +
            "         ) AS rn " +
            "  FROM pg_stat_user_indexes i " +
            "  JOIN pg_stat_user_tables  t ON i.relid = t.relid " +
            "  WHERE i.idx_scan > 0 " +
            "    AND i.idx_scan::numeric * 100.0 / NULLIF(i.idx_scan + t.seq_scan, 0) < 75 " +
            "    AND (i.idx_scan + t.seq_scan) > 100 " +
            "    AND t.n_live_tup > 10 " +
            "    AND t.relname NOT IN (" +
            "          'active_sessions'," +
            "          'refresh_tokens'," +
            "          'verification_tokens'," +
            "          'password_reset_tokens'," +
            "          'two_factor_tokens'," +
            "          'login_attempts'," +
            "          'password_recovery_attempts'" +
            "    ) " +
            ") " +
            "SELECT index_name, table_name, idx_scan, seq_scan, efficiency_pct, n_live_tup " +
            "FROM ranked " +
            "WHERE rn = 1 " +
            "ORDER BY efficiency_pct ASC";

    // ── Dependencias ──────────────────────────────────────────────────────────

    private final JdbcTemplate jdbc;
    private final MaintenanceLogRepository logRepository;

    public DatabaseMaintenanceService(JdbcTemplate jdbc,
            MaintenanceLogRepository logRepository) {
        this.jdbc = jdbc;
        this.logRepository = logRepository;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Devuelve las estadísticas de mantenimiento SOLO de tablas que tienen
     * registros obsoletos ({@code n_dead_tup > 0}).
     *
     * <p>
     * Los datos provienen de {@code pg_stat_user_tables}. El filtro
     * {@code WHERE n_dead_tup > 0} garantiza que solo se devuelven tablas que
     * realmente necesitan VACUUM, reduciendo el ruido frente al módulo de
     * Monitoreo que muestra todas las tablas.
     * </p>
     *
     * @return lista de {@link TableMaintenanceDto} ordenada por tuplas muertas
     *         descendente (solo tablas con dead tuples &gt; 0)
     */
    public List<TableMaintenanceDto> getDeadTuplesStats() {
        log.debug("[Maintenance] Consultando estadísticas de dead tuples (solo tablas con obsoletos)...");

        List<TableMaintenanceDto> stats = jdbc.query(SQL_DEAD_TUPLES, (rs, rowNum) -> {
            long dead = rs.getLong("n_dead_tup");
            long live = rs.getLong("n_live_tup");
            double bloat = rs.getDouble("bloat_percent");
            // Umbrales unificados con DatabaseMonitoringService.calculateTableStatus
            String status;
            if (dead > 20 && bloat > 30.0) {
                status = "critical";
            } else if (dead > 10 && bloat > 20.0) {
                status = "warning";
            } else {
                status = "ok";
            }
            return new TableMaintenanceDto(
                    rs.getString("relname"),
                    dead,
                    live,
                    rs.getString("last_autovacuum"),
                    rs.getString("last_vacuum"),
                    bloat,
                    status);
        });

        log.debug("[Maintenance] {} tablas analizadas", stats.size());
        return stats;
    }

    /**
     * Devuelve SOLO los índices que necesitan reconstrucción, aplicando tres
     * condiciones estrictas para eliminar falsos positivos de tablas vacías o
     * sin actividad real.
     *
     * <p>
     * Condiciones:
     * </p>
     * <ol>
     * <li>Eficiencia del índice &lt; 75 %</li>
     * <li>Tráfico total (idx_scan + seq_scan) &gt; 100</li>
     * <li>Registros vivos en la tabla &gt; 10</li>
     * </ol>
     *
     * <p>
     * Además, filtra los índices que fueron reconstruidos exitosamente
     * en las últimas 24 horas para no mostrar falsos positivos.
     * </p>
     *
     * @return lista de mapas con index_name, table_name, idx_scan, seq_scan,
     *         efficiency_pct, n_live_tup — ordenada por eficiencia ascendente
     */
    public List<Map<String, Object>> getProblematicIndexes() {
        log.debug("[Maintenance] Consultando índices con baja eficiencia (filtro estricto)...");
        List<Map<String, Object>> result = jdbc.queryForList(SQL_PROBLEMATIC_INDEXES);

        // Excluir índices cuya tabla fue reconstruida exitosamente en las últimas 24 h
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        Set<String> reindexedTableNames = logRepository
                .findByOperationOrderByExecutedAtDesc("REINDEX",
                        org.springframework.data.domain.PageRequest.of(0, 200))
                .stream()
                .filter(l -> "SUCCESS".equals(l.getStatus()))
                .filter(l -> l.getExecutedAt() != null && l.getExecutedAt().isAfter(cutoff))
                .map(MaintenanceLog::getTargetName)
                .collect(Collectors.toSet());

        log.debug("[Maintenance] Índices excluidos por REINDEX reciente (últimas 24h): {}",
                reindexedTableNames);

        if (!reindexedTableNames.isEmpty()) {
            result = result.stream()
                    .filter(idx -> !reindexedTableNames
                            .contains(String.valueOf(idx.get("table_name"))))
                    .collect(Collectors.toList());
        }

        log.debug("[Maintenance] {} índices problemáticos encontrados (tras filtro 24h)", result.size());
        return result;
    }

    /**
     * Ejecuta {@code VACUUM ANALYZE} sobre una tabla específica de forma manual
     * y registra el resultado en {@code maintenance_logs}.
     *
     * @param tableName nombre de la tabla a limpiar
     * @throws IllegalArgumentException si el nombre de tabla no está en el
     *                                  whitelist
     */
    public void runVacuum(String tableName) {
        validateTableName(tableName);

        // Capturar dead tuples antes
        Integer deadBefore = queryDeadTuples(tableName);

        // Registrar inicio
        MaintenanceLog entry = new MaintenanceLog();
        entry.setOperation("VACUUM_ANALYZE");
        entry.setTargetName(tableName);
        entry.setTargetType("TABLE");
        entry.setExecutedBy(currentUsername());
        entry.setExecutedAt(LocalDateTime.now());
        entry.setRowsBefore(deadBefore);
        entry.setStatus("IN_PROGRESS");
        entry = logRepository.save(entry);

        long start = System.currentTimeMillis();
        try {
            // tableName proviene del whitelist ALLOWED_TABLES — seguro contra SQL injection
            String sql = "VACUUM ANALYZE " + tableName;
            log.info("[Maintenance] Iniciando VACUUM ANALYZE en tabla '{}'...",
                    LogSanitizer.sanitize(tableName));
            jdbc.execute(sql);
            long duration = System.currentTimeMillis() - start;

            Integer deadAfter = queryDeadTuples(tableName);
            entry.setRowsAfter(deadAfter);
            entry.setDurationMs((int) duration);
            entry.setStatus("SUCCESS");
            logRepository.save(entry);
            log.info("[Maintenance] VACUUM ANALYZE completado en tabla '{}' en {} ms",
                    LogSanitizer.sanitize(tableName), duration);
        } catch (Exception e) {
            entry.setDurationMs((int) (System.currentTimeMillis() - start));
            entry.setStatus("ERROR");
            entry.setErrorMessage(e.getMessage());
            logRepository.save(entry);
            log.error("[Maintenance] Error en VACUUM ANALYZE '{}': {}",
                    LogSanitizer.sanitize(tableName), e.getMessage());
            throw e;
        }
    }

    /**
     * Ejecuta {@code REINDEX TABLE} para reconstruir los índices de una tabla
     * específica y registra el resultado en {@code maintenance_logs}.
     *
     * @param tableName nombre de la tabla cuyos índices se reconstruirán
     * @throws IllegalArgumentException si el nombre de tabla no está en el
     *                                  whitelist
     */
    public void runReindex(String tableName) {
        validateTableName(tableName);

        MaintenanceLog entry = new MaintenanceLog();
        entry.setOperation("REINDEX");
        entry.setTargetName(tableName);
        entry.setTargetType("INDEX");
        entry.setExecutedBy(currentUsername());
        entry.setExecutedAt(LocalDateTime.now());
        entry.setStatus("IN_PROGRESS");
        entry = logRepository.save(entry);

        long start = System.currentTimeMillis();
        try {
            // tableName proviene del whitelist ALLOWED_TABLES — seguro contra SQL injection
            String sql = "REINDEX TABLE " + tableName;
            log.info("[Maintenance] Iniciando REINDEX TABLE '{}' manual...",
                    LogSanitizer.sanitize(tableName));
            jdbc.execute(sql);
            long duration = System.currentTimeMillis() - start;

            entry.setDurationMs((int) duration);
            entry.setStatus("SUCCESS");
            logRepository.save(entry);
            log.info("[Maintenance] REINDEX TABLE '{}' completado en {} ms",
                    LogSanitizer.sanitize(tableName), duration);
        } catch (Exception e) {
            entry.setDurationMs((int) (System.currentTimeMillis() - start));
            entry.setStatus("ERROR");
            entry.setErrorMessage(e.getMessage());
            logRepository.save(entry);
            log.error("[Maintenance] Error en REINDEX '{}': {}",
                    LogSanitizer.sanitize(tableName), e.getMessage());
            throw e;
        }
    }

    /**
     * Ejecuta {@code ANALYZE} sobre una tabla específica (solo actualiza
     * estadísticas del planificador, sin limpiar dead tuples).
     *
     * @param tableName nombre de la tabla a analizar
     * @throws IllegalArgumentException si el nombre de tabla no está en el
     *                                  whitelist
     */
    public void runAnalyze(String tableName) {
        validateTableName(tableName);
        String sql = "ANALYZE " + tableName;
        log.info("[Maintenance] Iniciando ANALYZE en tabla '{}'...", LogSanitizer.sanitize(tableName));
        jdbc.execute(sql);
        log.info("[Maintenance] ANALYZE completado en tabla '{}'", LogSanitizer.sanitize(tableName));
    }

    /**
     * Devuelve los últimos 20 registros del historial de mantenimiento,
     * ordenados por fecha descendente.
     *
     * @return lista de {@link MaintenanceLogDto} con tiempo relativo calculado
     */
    public List<MaintenanceLogDto> getRecentHistory() {
        return logRepository.findTop20ByOrderByExecutedAtDesc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ── Utilidades privadas ───────────────────────────────────────────────────

    /** Mapea entidad → DTO calculando el campo relativo de tiempo. */
    private MaintenanceLogDto toDto(MaintenanceLog l) {
        return new MaintenanceLogDto(
                l.getId(),
                l.getOperation(),
                l.getTargetName(),
                l.getTargetType(),
                l.getExecutedBy(),
                l.getExecutedAt(),
                relativeTime(l.getExecutedAt()),
                l.getRowsBefore(),
                l.getRowsAfter(),
                l.getRowsAffected(),
                l.getDurationMs(),
                l.getStatus(),
                l.getErrorMessage());
    }

    /** Convierte un {@link LocalDateTime} en texto relativo en español. */
    private String relativeTime(LocalDateTime dt) {
        if (dt == null)
            return "";
        long seconds = ChronoUnit.SECONDS.between(dt, LocalDateTime.now());
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

    /** Obtiene el nombre del usuario autenticado desde el SecurityContext. */
    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "system";
    }

    /** Consulta el número actual de dead tuples de una tabla. */
    private Integer queryDeadTuples(String tableName) {
        try {
            Long v = jdbc.queryForObject(
                    "SELECT n_dead_tup FROM pg_stat_user_tables WHERE relname = ?",
                    Long.class, tableName);
            return v != null ? v.intValue() : 0;
        } catch (Exception e) {
            return null;
        }
    }

    public List<Map<String, Object>> getAutovacuumSettings() {
        log.debug("[Maintenance] Consultando parámetros de autovacuum...");
        String sql = "SELECT name, setting, unit " +
                "FROM pg_settings " +
                "WHERE name IN (" +
                "  'autovacuum'," +
                "  'autovacuum_vacuum_threshold'," +
                "  'autovacuum_vacuum_scale_factor'," +
                "  'autovacuum_naptime'," +
                "  'autovacuum_analyze_threshold'," +
                "  'autovacuum_max_workers'" +
                ") ORDER BY name";
        return jdbc.queryForList(sql);
    }

    /**
     * Valida que el nombre de tabla pertenezca al whitelist de tablas permitidas.
     *
     * <p>
     * Dos capas de defensa:
     * </p>
     * <ol>
     * <li>Formato: solo letras minúsculas, dígitos y guiones bajos
     * — rechaza cualquier carácter de SQL injection antes de hacer el lookup.</li>
     * <li>Whitelist: el nombre debe estar en {@link #ALLOWED_TABLES}.
     * SonarQube reconoce este patrón como protección efectiva contra
     * CWE-89 (SQL Injection).</li>
     * </ol>
     *
     * @param tableName nombre a validar
     * @throws IllegalArgumentException si el nombre es nulo, tiene formato
     *                                  inválido o no está en
     *                                  {@link #ALLOWED_TABLES}
     */
    private void validateTableName(String tableName) {
        if (tableName == null
                || !tableName.matches("^[a-z_][a-z0-9_]{0,62}$")
                || !ALLOWED_TABLES.contains(tableName)) {
            throw new IllegalArgumentException(
                    "Operación de mantenimiento denegada: tabla no permitida.");
        }
    }
}
