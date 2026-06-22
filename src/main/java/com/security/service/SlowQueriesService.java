package com.security.service;

import com.security.dto.admin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Servicio para auditoría de consultas lentas, bloqueos y estadísticas
 * de tablas en PostgreSQL.
 *
 * <p>
 * Fuentes de datos utilizadas:
 * </p>
 * <ul>
 * <li>{@code pg_stat_activity} — queries activas en tiempo real</li>
 * <li>{@code pg_stat_statements} — historial estadístico (requiere
 * extensión)</li>
 * <li>{@code pg_stat_user_tables} — estadísticas de acceso por tabla</li>
 * <li>{@code pg_locks / pg_blocking_pids} — bloqueos entre sesiones</li>
 * <li>{@code pg_settings} — configuración actual del servidor</li>
 * </ul>
 *
 * <p>
 * <strong>Seguridad:</strong> todas las queries son de solo lectura sobre
 * vistas
 * del sistema. No reciben parámetros del usuario. Las previews de queries se
 * truncan
 * en el backend para evitar filtrar datos sensibles.
 * </p>
 */
@Service
public class SlowQueriesService {

    private static final Logger log = LoggerFactory.getLogger(SlowQueriesService.class);

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final JdbcTemplate jdbc;

    public SlowQueriesService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. QUERIES ACTIVAS (pg_stat_activity)
    // ═══════════════════════════════════════════════════════════════════════

    private static final String SQL_ACTIVE_QUERIES = """
            SELECT
                pid,
                usename                                           AS username,
                COALESCE(application_name, '')                    AS application_name,
                COALESCE(client_addr::TEXT, 'local')              AS client_ip,
                state,
                wait_event_type,
                wait_event,
                COALESCE(
                    EXTRACT(EPOCH FROM (NOW() - query_start))::INTEGER,
                    0
                )                                                 AS duration_seconds,
                LEFT(query, 500)                                  AS query_preview,
                query_start
            FROM pg_stat_activity
            WHERE datname = current_database()
              AND state   != 'idle'
              AND pid     != pg_backend_pid()
              AND query NOT ILIKE '%pg_stat_activity%'
              AND query NOT ILIKE '%pg_stat_statements%'
              AND query NOT ILIKE '%pg_blocking_pids%'
            ORDER BY duration_seconds DESC NULLS LAST
            """;

    /**
     * Obtiene las queries activas en este momento, clasificadas por severidad.
     */
    public List<ActiveQueryDto> getActiveQueries() {
        log.debug("[SlowQueries] Consultando queries activas");
        return jdbc.query(SQL_ACTIVE_QUERIES, (rs, rowNum) -> {
            String state = rs.getString("state");
            String waitEventType = rs.getString("wait_event_type");
            int durationSeconds = rs.getInt("duration_seconds");

            Timestamp queryStart = rs.getTimestamp("query_start");
            String queryStartStr = queryStart != null
                    ? queryStart.toLocalDateTime().format(ISO_FMT)
                    : null;

            String classification = classifyQuery(state, waitEventType, durationSeconds);

            return new ActiveQueryDto(
                    rs.getInt("pid"),
                    rs.getString("username"),
                    rs.getString("application_name"),
                    rs.getString("client_ip"),
                    state,
                    waitEventType,
                    rs.getString("wait_event"),
                    durationSeconds,
                    rs.getString("query_preview"),
                    queryStartStr,
                    classification);
        });
    }

    /**
     * Clasifica una query activa según su estado y duración.
     *
     * <ul>
     * <li>{@code IDLE_TX} — transacción abandonada (peligroso)</li>
     * <li>{@code BLOCKED} — esperando lock de otra sesión</li>
     * <li>{@code SLOW} — activa por más de 30 segundos</li>
     * <li>{@code WATCH} — activa entre 5 y 30 segundos</li>
     * <li>{@code NORMAL} — menos de 5 segundos</li>
     * </ul>
     */
    private String classifyQuery(String state, String waitEventType, int durationSeconds) {
        if ("idle in transaction".equals(state))
            return "IDLE_TX";
        if ("Lock".equals(waitEventType))
            return "BLOCKED";
        if (durationSeconds > 30)
            return "SLOW";
        if (durationSeconds > 5)
            return "WATCH";
        return "NORMAL";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. QUERIES MÁS COSTOSAS (pg_stat_statements)
    // ═══════════════════════════════════════════════════════════════════════

    private static final String SQL_CHECK_EXTENSION = "SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements'";

    private static final String SQL_TOP_EXPENSIVE = """
            SELECT
                ROUND(mean_exec_time::NUMERIC, 2)                         AS avg_ms,
                ROUND(max_exec_time::NUMERIC, 2)                          AS max_ms,
                ROUND(total_exec_time::NUMERIC, 2)                        AS total_ms,
                calls,
                rows,
                ROUND(
                    (shared_blks_hit * 100.0 /
                     NULLIF(shared_blks_hit + shared_blks_read, 0))::NUMERIC,
                    1
                )                                                         AS cache_hit_pct,
                LEFT(query, 500)                                          AS query_preview
            FROM pg_stat_statements
            WHERE dbid = (SELECT oid FROM pg_database
                          WHERE datname = current_database())
              AND calls > 5
              -- Excluir queries de monitoreo del sistema
              AND query NOT ILIKE '%pg_stat%'
              AND query NOT ILIKE '%pg_settings%'
              AND query NOT ILIKE '%VACUUM%'
              -- Excluir introspección del driver JDBC (PostgreSQL)
              AND query NOT ILIKE '%pg_catalog.pg_namespace%'
              AND query NOT ILIKE '%pg_catalog.pg_class%'
              AND query NOT ILIKE '%pg_catalog.pg_attribute%'
              AND query NOT ILIKE '%pg_catalog.pg_type%'
              AND query NOT ILIKE '%pg_catalog.pg_get_keywords%'
              AND query NOT ILIKE '%pg_catalog.pg_get_expr%'
              AND query NOT ILIKE '%information_schema.sequences%'
              AND query NOT ILIKE '%information_schema.tables%'
              AND query NOT ILIKE '%information_schema.columns%'
              -- Excluir herramientas de administración (pgAdmin, DBeaver)
              AND query NOT ILIKE '%string_agg(word%'
              AND query NOT ILIKE '%pg_catalog.pg_get_userbyid%'
              AND query NOT ILIKE '%pg_catalog.pg_tablespace%'
              AND query NOT ILIKE '%tmp.TABLE_CAT%'
              AND query NOT ILIKE '%tmp.TABLE_SCHEM%'
              -- Excluir tablas internas del sistema de la aplicación
              AND query NOT ILIKE '%active_sessions%'
              AND query NOT ILIKE '%maintenance_logs%'
              AND query NOT ILIKE '%backup_logs%'
              AND query NOT ILIKE '%automation_execution%'
              AND query NOT ILIKE '%system_automations%'
              AND query NOT ILIKE '%flyway%'
              AND query NOT ILIKE '%pg_database_size%'
              AND query NOT ILIKE '%pg_show_all_setti%'
              AND query NOT ILIKE '%set_config%'
              AND query NOT ILIKE '%pg_extension%'
              AND query NOT ILIKE '%pg_blocking_pids%'
              AND query NOT ILIKE '%pg_indexes%'
              -- Excluir queries de refresh de tokens y sesiones
              AND query NOT ILIKE '%refresh_tokens%'
              AND query NOT ILIKE '%verification_tokens%'
              AND query NOT ILIKE '%password_reset%'
              AND query NOT ILIKE '%login_attempts%'
            ORDER BY mean_exec_time DESC
            LIMIT 10
            """;

    /**
     * Obtiene las 10 queries más costosas en promedio.
     * Si pg_stat_statements no está instalada, devuelve respuesta con
     * available=false.
     * Si la extensión está pero no hay queries de negocio (todas filtradas),
     * devuelve available=true con data vacía y message informativo.
     */
    public TopExpensiveQueriesResponse getTopExpensiveQueries() {
        log.debug("[SlowQueries] Consultando queries costosas (pg_stat_statements)");

        // Verificar si la extensión está disponible
        if (!isExtensionAvailable()) {
            log.info("[SlowQueries] pg_stat_statements no disponible");
            return TopExpensiveQueriesResponse.unavailable();
        }

        List<ExpensiveQueryDto> queries = jdbc.query(SQL_TOP_EXPENSIVE, (rs, rowNum) -> {
            double cacheHit = rs.getDouble("cache_hit_pct");
            if (rs.wasNull())
                cacheHit = 0.0;

            return new ExpensiveQueryDto(
                    rs.getDouble("avg_ms"),
                    rs.getDouble("max_ms"),
                    rs.getDouble("total_ms"),
                    rs.getLong("calls"),
                    rs.getLong("rows"),
                    cacheHit,
                    rs.getString("query_preview"));
        });

        if (queries.isEmpty()) {
            return TopExpensiveQueriesResponse.noBusinessQueries();
        }

        return TopExpensiveQueriesResponse.of(queries);
    }

    /**
     * Verifica si la extensión pg_stat_statements está instalada.
     */
    private boolean isExtensionAvailable() {
        try {
            List<Integer> result = jdbc.query(SQL_CHECK_EXTENSION,
                    (rs, rowNum) -> rs.getInt(1));
            return !result.isEmpty();
        } catch (Exception e) {
            log.warn("[SlowQueries] Error verificando pg_stat_statements: {}", e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. BLOQUEOS ACTIVOS (pg_stat_activity + pg_blocking_pids)
    // ═══════════════════════════════════════════════════════════════════════

    private static final String SQL_ACTIVE_LOCKS = """
            SELECT
                blocked.pid                                               AS blocked_pid,
                blocked.usename                                           AS blocked_user,
                COALESCE(blocked.application_name, '')                    AS blocked_app,
                blocking.pid                                              AS blocking_pid,
                blocking.usename                                          AS blocking_user,
                COALESCE(
                    EXTRACT(EPOCH FROM (NOW() - blocked.query_start))::INTEGER,
                    0
                )                                                         AS wait_seconds,
                LEFT(blocked.query, 300)                                  AS blocked_query,
                LEFT(blocking.query, 300)                                 AS blocking_query
            FROM pg_stat_activity AS blocked
            JOIN pg_stat_activity AS blocking
              ON blocking.pid = ANY(pg_blocking_pids(blocked.pid))
            WHERE cardinality(pg_blocking_pids(blocked.pid)) > 0
              AND blocked.datname = current_database()
            ORDER BY wait_seconds DESC
            """;

    /**
     * Detecta bloqueos activos entre sesiones.
     */
    public List<ActiveLockDto> getActiveLocks() {
        log.debug("[SlowQueries] Consultando bloqueos activos");
        try {
            return jdbc.query(SQL_ACTIVE_LOCKS, (rs, rowNum) -> new ActiveLockDto(
                    rs.getInt("blocked_pid"),
                    rs.getString("blocked_user"),
                    rs.getString("blocked_app"),
                    rs.getInt("blocking_pid"),
                    rs.getString("blocking_user"),
                    rs.getInt("wait_seconds"),
                    rs.getString("blocked_query"),
                    rs.getString("blocking_query")));
        } catch (Exception e) {
            // pg_blocking_pids no existe en PG < 9.6 — devolver vacío
            log.warn("[SlowQueries] Error consultando bloqueos (¿PG < 9.6?): {}", e.getMessage());
            return List.of();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. ESTADÍSTICAS DE TABLAS CON MÁS CARGA
    // ═══════════════════════════════════════════════════════════════════════

    private static final String SQL_TABLE_STATS = """
            SELECT
                relname                                    AS table_name,
                COALESCE(seq_scan, 0)                      AS seq_scan,
                COALESCE(seq_tup_read, 0)                  AS seq_tup_read,
                COALESCE(idx_scan, 0)                      AS idx_scan,
                COALESCE(idx_tup_fetch, 0)                 AS idx_tup_fetch,
                COALESCE(n_tup_ins, 0) +
                COALESCE(n_tup_upd, 0) +
                COALESCE(n_tup_del, 0)                     AS total_writes,
                CASE
                    WHEN (COALESCE(seq_scan, 0) + COALESCE(idx_scan, 0)) = 0 THEN 0
                    ELSE ROUND(
                        COALESCE(idx_scan, 0) * 100.0 /
                        (COALESCE(seq_scan, 0) + COALESCE(idx_scan, 0)),
                        1
                    )
                END                                        AS idx_usage_pct,
                COALESCE(n_live_tup, 0)                    AS live_rows,
                COALESCE(n_dead_tup, 0)                    AS dead_rows
            FROM pg_stat_user_tables
            WHERE (COALESCE(seq_scan, 0) + COALESCE(idx_scan, 0)) > 0
              AND COALESCE(n_live_tup, 0) > 100
            ORDER BY seq_scan DESC
            LIMIT 15
            """;

    /**
     * Obtiene estadísticas de acceso de las 15 tablas con más escaneos
     * secuenciales.
     */
    public List<TableStatsDto> getTableStats() {
        log.debug("[SlowQueries] Consultando estadísticas de tablas");
        return jdbc.query(SQL_TABLE_STATS, (rs, rowNum) -> new TableStatsDto(
                rs.getString("table_name"),
                rs.getLong("seq_scan"),
                rs.getLong("seq_tup_read"),
                rs.getLong("idx_scan"),
                rs.getLong("idx_tup_fetch"),
                rs.getLong("total_writes"),
                rs.getDouble("idx_usage_pct"),
                rs.getLong("live_rows"),
                rs.getLong("dead_rows")));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. CONFIGURACIÓN DE POSTGRESQL (solo lectura)
    // ═══════════════════════════════════════════════════════════════════════

    private static final String SQL_PG_CONFIG = """
            SELECT
                name,
                setting,
                unit,
                short_desc
            FROM pg_settings
            WHERE name IN (
                'log_min_duration_statement',
                'log_statement',
                'track_activity_query_size',
                'pg_stat_statements.max',
                'pg_stat_statements.track'
            )
            ORDER BY name
            """;

    /**
     * Obtiene los parámetros de configuración relevantes para el análisis de
     * queries.
     * Estos son de solo lectura — modificarlos requiere acceso directo al servidor.
     */
    public List<PgSettingDto> getPgConfig() {
        log.debug("[SlowQueries] Consultando configuración de PostgreSQL");
        return jdbc.query(SQL_PG_CONFIG, (rs, rowNum) -> new PgSettingDto(
                rs.getString("name"),
                rs.getString("setting"),
                rs.getString("unit"),
                rs.getString("short_desc")));
    }
}
