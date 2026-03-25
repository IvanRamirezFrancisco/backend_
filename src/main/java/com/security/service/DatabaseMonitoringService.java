package com.security.service;

import com.security.dto.admin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio de monitoreo de base de datos PostgreSQL.
 *
 * <p>
 * Todas las consultas usan {@link JdbcTemplate} con SQL nativo sobre las
 * vistas del sistema de PostgreSQL. No depende de JPA ni de entidades.
 * </p>
 *
 * <p>
 * Vistas utilizadas:
 * <ul>
 * <li>{@code pg_database} — tamaño, nombre de la BD</li>
 * <li>{@code pg_stat_activity} — conexiones activas, queries en curso</li>
 * <li>{@code pg_statio_user_tables} — cache hit ratio de heap blocks</li>
 * <li>{@code pg_stat_user_tables} — dead tuples, last autovacuum, seq
 * scans</li>
 * <li>{@code pg_stat_user_indexes} — index scans por índice</li>
 * <li>{@code pg_total_relation_size} — tamaño total de cada tabla</li>
 * <li>{@code pg_postmaster_start_time}— fecha de inicio del servidor</li>
 * <li>{@code pg_stat_database} — commits/rollbacks para calcular TPS</li>
 * <li>{@code current_setting} — max_connections configurado</li>
 * </ul>
 * </p>
 */
@Service
public class DatabaseMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMonitoringService.class);

    // ── Umbrales para alertas ────────────────────────────────────────────────
    private static final double CACHE_HIT_WARN = 95.0; // por debajo → warning
    private static final double CACHE_HIT_CRIT = 90.0; // por debajo → critical

    /**
     * Umbral absoluto de dead tuples para estado 'critical' (dead &gt; 20 AND bloat
     * &gt; 30%)
     */
    private static final int DEAD_CRIT_ABSOLUTE = 20;
    /** Porcentaje de bloat para estado 'critical' */
    private static final double DEAD_PCT_CRIT = 30.0;
    /**
     * Umbral absoluto de dead tuples para estado 'warning' (dead &gt; 10 AND bloat
     * &gt; 20%)
     */
    private static final int DEAD_WARN_ABSOLUTE = 10;
    /** Porcentaje de bloat para estado 'warning' */
    private static final double DEAD_PCT_WARN = 20.0;
    private static final double IDX_EFF_WARN = 80.0; // por debajo → low-efficiency
    private static final double CONN_WARN_PCT = 70.0;
    private static final double CONN_CRIT_PCT = 90.0;

    /** Mínimo de seq_scans para considerar eficiencia baja como alerta real */
    private static final long IDX_MIN_SEQ_SCANS = 100;
    /** Eficiencia mínima para disparar alerta en tablas con datos reales */
    private static final double IDX_ALERT_EFF_THRESHOLD = 50.0;
    /** Mínimo de registros vivos para que apliquen alertas de índices */
    private static final long IDX_MIN_LIVE_ROWS = 50;
    /** Mínimo de registros para recomendar REINDEX */
    private static final long IDX_REINDEX_MIN_ROWS = 100;

    // ── Variables para cálculo de TPS real (delta entre llamadas) ────────────
    private static final AtomicLong prevXactCommit = new AtomicLong(-1);
    private static final AtomicReference<Instant> prevTpsTime = new AtomicReference<>(null);

    // ── SQL ───────────────────────────────────────────────────────────────────

    private static final String SQL_DB_SIZE = "SELECT pg_database_size(current_database())";

    private static final String SQL_UPTIME_DAYS = "SELECT EXTRACT(DAY FROM (now() - pg_postmaster_start_time()))::bigint";

    private static final String SQL_PG_VERSION = "SELECT split_part(version(), ' ', 2)";

    private static final String SQL_MAX_CONNECTIONS = "SELECT current_setting('max_connections')::int";

    /** Total de conexiones abiertas en la BD actual (sin filtro de state) */
    private static final String SQL_ACTIVE_CONNECTIONS = "SELECT count(*)::int " +
            "FROM pg_stat_activity " +
            "WHERE datname = current_database()";

    /**
     * Clasificación unificada de conexiones en una sola pasada sobre
     * {@code pg_stat_activity}, filtrado por la base de datos actual.
     *
     * <p>
     * Cada fila cae en <em>exactamente una</em> categoría, en orden de
     * precedencia estricto:
     * <ol>
     * <li><b>pg_internal</b> – procesos propios de PostgreSQL
     * ({@code backend_type != 'client backend'}): autovacuum, checkpointer,
     * WAL writer, background writer, etc.</li>
     * <li><b>pool</b> – conexiones del pool HikariCP de Spring Boot.
     * En producción el driver se identifica como
     * {@code "PostgreSQL JDBC Driver"}; en dev pueden aparecer como
     * {@code "HikariCP"} o variantes similares.</li>
     * <li><b>admin_tools</b> – conexiones de herramientas de administración
     * (pgAdmin 4, DBeaver, DataGrip, TablePlus, Postico, Navicat, HeidiSQL).
     * Se detectan por {@code application_name ILIKE 'pgAdmin%'} que captura
     * exactamente el formato real: {@code "pgAdmin 4 - CONN:XXXXXXX"}.
     * Se incluyen conexiones con cualquier {@code state} (incluyendo idle)
     * porque pgAdmin mantiene conexiones persistentes sin query activa.</li>
     * <li><b>user_sessions</b> – sesiones externas con actividad reciente
     * (&lt; 30 min, estado 'active' o 'idle') que no son pool ni admin.
     * Típicamente peticiones REST, Postman, psql.</li>
     * </ol>
     *
     * <p>
     * <b>WHERE datname = current_database()</b>: filtra exactamente la BD
     * de la aplicación — igual que tu query manual que clasificó correctamente
     * los pgAdmin. Sin este filtro, {@code count(*)} incluye conexiones de otras
     * BDs del clúster y el conteo se desincroniza con
     * {@code SQL_ACTIVE_CONNECTIONS}.
     * Los procesos internos PG tienen {@code datname} de su BD de trabajo, por lo
     * que el filtro los captura correctamente.
     *
     * <p>
     * <b>Sin vulnerabilidades</b>: solo lee la vista de sistema
     * {@code pg_stat_activity} (solo lectura). No recibe parámetros externos;
     * {@code current_database()} es una función interna de PostgreSQL.
     */
    private static final String SQL_CONNECTION_BREAKDOWN = "SELECT " +
            "  COALESCE(SUM(CASE WHEN backend_type != 'client backend'" +
            "                    THEN 1 ELSE 0 END), 0)::int               AS pg_internal, " +
            "  COALESCE(SUM(CASE WHEN backend_type = 'client backend'" +
            "            AND (" +
            "              application_name = 'PostgreSQL JDBC Driver'" +
            "              OR application_name ILIKE '%hikari%'" +
            "              OR application_name ILIKE '%HikariCP%'" +
            "            )" +
            "                    THEN 1 ELSE 0 END), 0)::int               AS pool, " +
            "  COALESCE(SUM(CASE WHEN backend_type = 'client backend'" +
            "            AND (" +
            "              application_name ILIKE 'pgAdmin%'" +
            "              OR application_name ILIKE '%DBeaver%'" +
            "              OR application_name ILIKE '%DataGrip%'" +
            "              OR application_name ILIKE '%TablePlus%'" +
            "              OR application_name ILIKE '%Postico%'" +
            "              OR application_name ILIKE '%Navicat%'" +
            "              OR application_name ILIKE '%HeidiSQL%'" +
            "              OR application_name ILIKE '%Beekeeper%'" +
            "            )" +
            "                    THEN 1 ELSE 0 END), 0)::int               AS admin_tools, " +
            "  COALESCE(SUM(CASE WHEN backend_type = 'client backend'" +
            "            AND application_name NOT ILIKE 'pgAdmin%'" +
            "            AND application_name NOT ILIKE '%DBeaver%'" +
            "            AND application_name NOT ILIKE '%DataGrip%'" +
            "            AND application_name NOT ILIKE '%TablePlus%'" +
            "            AND application_name NOT ILIKE '%Postico%'" +
            "            AND application_name NOT ILIKE '%Navicat%'" +
            "            AND application_name NOT ILIKE '%HeidiSQL%'" +
            "            AND application_name NOT ILIKE '%Beekeeper%'" +
            "            AND application_name != 'PostgreSQL JDBC Driver'" +
            "            AND application_name NOT ILIKE '%hikari%'" +
            "            AND application_name NOT ILIKE '%HikariCP%'" +
            "            AND state IN ('active', 'idle')" +
            "            AND now() - state_change < interval '30 minutes'" +
            "                    THEN 1 ELSE 0 END), 0)::int               AS user_sessions, " +
            "  count(*)::int                                               AS total_in_db " +
            "FROM pg_stat_activity " +
            "WHERE datname = current_database()";

    private static final String SQL_CACHE_HIT_RATIO = "SELECT COALESCE(ROUND(" +
            "  (sum(heap_blks_hit) / NULLIF(sum(heap_blks_hit) + sum(heap_blks_read), 0)) * 100, 2" +
            "), 0) FROM pg_statio_user_tables";

    /** xact_commit acumulado — se usa para calcular el delta TPS entre llamadas */
    private static final String SQL_XACT_COMMIT = "SELECT COALESCE(xact_commit + xact_rollback, 0) " +
            "FROM pg_stat_database " +
            "WHERE datname = current_database()";

    /**
     * Tiempo promedio en ms de queries activas (excluyendo la consulta de
     * monitoreo)
     */
    private static final String SQL_AVG_QUERY_MS = "SELECT COALESCE(ROUND(AVG(EXTRACT(EPOCH FROM (now() - query_start)) * 1000)::numeric, 2), 0.0) "
            +
            "FROM pg_stat_activity " +
            "WHERE datname = current_database() " +
            "  AND state = 'active' " +
            "  AND query NOT ILIKE '%pg_stat_activity%'";

    private static final String SQL_TOP_TABLES = "SELECT relname, " +
            "       pg_total_relation_size(relid), " +
            "       pg_indexes_size(relid), " +
            "       n_live_tup " +
            "FROM pg_stat_user_tables " +
            "ORDER BY pg_total_relation_size(relid) DESC " +
            "LIMIT 5";

    /**
     * Salud de TODAS las tablas de usuario: devuelve las tablas incluyendo las
     * vacías. Ordenadas por actividad total DESC para que las más relevantes
     * aparezcan primero.
     *
     * <p>
     * Umbrales unificados (idénticos a {@link #calculateTableStatus}):
     * </p>
     * <ul>
     * <li><b>critical</b>: n_dead_tup &gt; 20 Y porcentaje &gt; 30 %</li>
     * <li><b>warning</b>: n_dead_tup &gt; 10 Y porcentaje &gt; 20 %</li>
     * <li><b>ok</b>: el resto</li>
     * </ul>
     */
    private static final String SQL_TABLE_HEALTH = "SELECT relname, " +
            "       n_live_tup, " +
            "       n_dead_tup, " +
            "       pg_size_pretty(pg_total_relation_size(relid)) AS total_size, " +
            "       TO_CHAR(GREATEST(last_vacuum, last_autovacuum), 'YYYY-MM-DD HH24:MI') AS last_vacuum_any, " +
            "       TO_CHAR(last_autoanalyze, 'YYYY-MM-DD HH24:MI') AS last_autoanalyze " +
            "FROM pg_stat_user_tables " +
            "ORDER BY (n_live_tup + n_dead_tup) DESC, relname ASC";

    /** Uso de índices: scans por índice vs. escaneos secuenciales de la tabla */
    private static final String SQL_INDEX_USAGE = "SELECT i.indexrelname, " +
            "       i.relname, " +
            "       i.idx_scan, " +
            "       t.seq_scan " +
            "FROM pg_stat_user_indexes i " +
            "JOIN pg_stat_user_tables  t ON i.relid = t.relid " +
            "ORDER BY i.idx_scan DESC";

    // ── Dependencias ──────────────────────────────────────────────────────────

    private final JdbcTemplate jdbc;

    public DatabaseMonitoringService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Recopila todas las métricas de salud de la base de datos en una sola llamada.
     *
     * @return {@link DatabaseMetricsDto} con todos los datos actuales de
     *         PostgreSQL.
     */
    public DatabaseMetricsDto getMetrics() {
        log.debug("[Monitoring] Consultando métricas completas de la base de datos...");

        // Datos básicos
        long dbSizeBytes = queryDbSize();
        long uptimeDays = queryUptimeDays();
        String pgVersion = queryPgVersion();
        int maxConnections = queryMaxConnections();
        int totalConns = queryActiveConnections();
        // Clasificación unificada en una sola query sin residuos
        int[] breakdown = queryConnectionBreakdown(); // [pg_internal, pool, admin_tools, user_sessions_pg]
        int pgInternalConns = breakdown[0];
        int poolConns = breakdown[1];
        int adminToolConns = breakdown[2];
        // Sesiones de usuario: leídas desde active_sessions (JWT válidos con actividad
        // reciente)
        List<UserSessionDto> userSessionList = queryActiveAppSessions();
        int userSessions = userSessionList.size();
        int sessionsLastHour = querySessionsLastHour();
        int sessionsToday = querySessionsToday();
        double cacheHitRatio = queryCacheHitRatio();
        long tps = computeTpsDelta();
        double avgQueryMs = queryAvgQueryMs();

        // Sub-objetos
        double connUsagePct = maxConnections > 0
                ? BigDecimal.valueOf(totalConns * 100.0 / maxConnections)
                        .setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        ConnectionInfoDto connections = new ConnectionInfoDto(
                totalConns, maxConnections, connUsagePct,
                poolConns, pgInternalConns, userSessions,
                adminToolConns,
                sessionsLastHour, sessionsToday, userSessionList);
        log.debug(
                "[Monitoring] ConnectionInfoDto creado: total={}, pool={}, pgInternal={}, adminTools={}, userSessions={}",
                connections.total(), connections.poolConnections(), connections.pgInternalConnections(),
                connections.adminTools(), connections.activeUserSessions());
        PerformanceMetricsDto performance = new PerformanceMetricsDto(cacheHitRatio, tps, avgQueryMs);

        // Top tablas, salud, índices
        List<TableMetricDto> topTables = queryTopTables();
        List<TableHealthDto> tableHealth = queryTableHealth();
        List<IndexUsageDto> indexUsage = queryIndexUsage();

        // Alertas automáticas
        List<DbAlertDto> alerts = generateAlerts(tableHealth, indexUsage, connections, performance);

        // Score global (0-100)
        int healthScore = computeHealthScore(cacheHitRatio, connUsagePct, tableHealth, indexUsage, alerts);

        log.debug(
                "[Monitoring] score={}, size={}B, conns={}/{}, pool={}, pgInternal={}, adminTools={}, users={}, cache={}%, tps={}, avgQ={}ms",
                healthScore, dbSizeBytes, totalConns, maxConnections,
                poolConns, pgInternalConns, adminToolConns, userSessions, cacheHitRatio, tps, avgQueryMs);

        return new DatabaseMetricsDto(
                healthScore, dbSizeBytes, uptimeDays, pgVersion,
                totalConns, cacheHitRatio, topTables,
                performance, connections,
                tableHealth, indexUsage, alerts);
    }

    /**
     * Diagnóstico: devuelve todas las filas de {@code pg_stat_activity} ordenadas
     * por estado y application_name. Útil para ajustar los filtros de
     * clasificación.
     *
     * @return lista de mapas columna→valor con las columnas principales
     */
    public List<Map<String, Object>> getConnectionsDebug() {
        return jdbc.queryForList(
                "SELECT pid, usename, application_name, client_addr::text AS client_addr, " +
                        "       state, backend_type " +
                        "FROM pg_stat_activity " +
                        "ORDER BY state, application_name, pid");
    }

    // ── Queries individuales ─────────────────────────────────────────────────

    private long queryDbSize() {
        Long r = jdbc.queryForObject(SQL_DB_SIZE, Long.class);
        return r != null ? r : 0L;
    }

    private long queryUptimeDays() {
        Long r = jdbc.queryForObject(SQL_UPTIME_DAYS, Long.class);
        return r != null ? r : 0L;
    }

    private String queryPgVersion() {
        String r = jdbc.queryForObject(SQL_PG_VERSION, String.class);
        return r != null ? r : "Desconocida";
    }

    private int queryMaxConnections() {
        Integer r = jdbc.queryForObject(SQL_MAX_CONNECTIONS, Integer.class);
        return r != null ? r : 100;
    }

    private int queryActiveConnections() {
        Integer r = jdbc.queryForObject(SQL_ACTIVE_CONNECTIONS, Integer.class);
        return r != null ? r : 0;
    }

    /**
     * Ejecuta {@link #SQL_CONNECTION_BREAKDOWN} y devuelve un array de 5 enteros:
     * <ul>
     * <li>[0] pg_internal – procesos internos de PostgreSQL</li>
     * <li>[1] pool – conexiones del pool HikariCP de la aplicación</li>
     * <li>[2] admin_tools – herramientas de administración (pgAdmin, DBeaver…)</li>
     * <li>[3] user_sessions – sesiones externas con actividad reciente</li>
     * <li>[4] total_in_db – total de backends en la BD actual (consistente con
     * {@code SQL_ACTIVE_CONNECTIONS})</li>
     * </ul>
     * Un solo round-trip a la BD. Registra resultado raw en DEBUG para diagnóstico.
     */
    private int[] queryConnectionBreakdown() {
        int[] result = jdbc.queryForObject(SQL_CONNECTION_BREAKDOWN, (rs, rowNum) -> new int[] {
                rs.getInt("pg_internal"),
                rs.getInt("pool"),
                rs.getInt("admin_tools"),
                rs.getInt("user_sessions"),
                rs.getInt("total_in_db")
        });
        if (result == null) {
            result = new int[] { 0, 0, 0, 0, 0 };
        }
        log.debug(
                "[Monitoring] Connection breakdown raw: pg_internal={}, pool={}, admin_tools={}, user_sessions={}, total_in_db={}",
                result[0], result[1], result[2], result[3], result[4]);
        return result;
    }

    /**
     * Lee la tabla {@code active_sessions} de la aplicación.
     * Agrupa por usuario (u.id) y limita a 200 filas para evitar sobrecarga.
     * Retorna un array de 3 elementos: [lista, countLastHour, countToday].
     */
    private List<UserSessionDto> queryActiveAppSessions() {
        final String sql = "SELECT u.first_name, " +
                "       u.last_name, " +
                "       u.email, " +
                "       latest.ip_address, " +
                "       latest.user_agent, " +
                "       latest.session_count, " +
                "       EXTRACT(EPOCH FROM (NOW() - latest.last_activity))::bigint AS seconds_inactive " +
                "FROM users u " +
                "JOIN ( " +
                "  SELECT a.user_id, " +
                "         MAX(a.last_activity)  AS last_activity, " +
                "         COUNT(a.id)           AS session_count, " +
                "         MAX(a.ip_address)     AS ip_address, " +
                "         MAX(a.user_agent)     AS user_agent " +
                "  FROM active_sessions a " +
                "  WHERE a.revoked = false " +
                "    AND a.expires_at > NOW() " +
                "    AND a.last_activity > NOW() - INTERVAL '30 minutes' " +
                "  GROUP BY a.user_id " +
                "  ORDER BY MAX(a.last_activity) DESC " +
                "  LIMIT 200 " +
                ") latest ON latest.user_id = u.id " +
                "ORDER BY latest.last_activity DESC";

        return jdbc.query(sql, (rs, rowNum) -> {
            long secs = rs.getLong("seconds_inactive");
            String first = rs.getString("first_name");
            String last = rs.getString("last_name");
            String fullName = ((first == null ? "" : first) + " " + (last == null ? "" : last)).strip();
            return new UserSessionDto(
                    fullName.isBlank() ? rs.getString("email") : fullName,
                    rs.getString("email"),
                    rs.getString("ip_address"),
                    abbreviateUserAgent(rs.getString("user_agent")),
                    formatSecondsDisplay(secs),
                    secs,
                    rs.getInt("session_count"));
        });
    }

    /** COUNT de usuarios distintos con actividad en la última hora (sin LIMIT). */
    private int querySessionsLastHour() {
        Integer r = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT user_id)::int " +
                        "FROM active_sessions " +
                        "WHERE revoked = false " +
                        "  AND expires_at > NOW() " +
                        "  AND last_activity > NOW() - INTERVAL '1 hour'",
                Integer.class);
        return r != null ? r : 0;
    }

    /** COUNT de usuarios distintos con actividad desde medianoche de hoy. */
    private int querySessionsToday() {
        Integer r = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT user_id)::int " +
                        "FROM active_sessions " +
                        "WHERE revoked = false " +
                        "  AND expires_at > NOW() " +
                        "  AND last_activity >= CURRENT_DATE",
                Integer.class);
        return r != null ? r : 0;
    }

    /** Formatea segundos en texto legible: "hace 2 min", "hace 45 seg", etc. */
    private static String formatSecondsDisplay(long seconds) {
        if (seconds < 60)
            return "hace " + seconds + "s";
        if (seconds < 3600)
            return "hace " + (seconds / 60) + " min";
        return "hace " + (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "min";
    }

    /** Extrae navegador del User-Agent sin exponer la cadena completa. */
    private static String abbreviateUserAgent(String ua) {
        if (ua == null || ua.isBlank())
            return "Desconocido";
        if (ua.contains("Edg"))
            return "Edge";
        if (ua.contains("Chrome"))
            return "Chrome";
        if (ua.contains("Firefox"))
            return "Firefox";
        if (ua.contains("Safari") && !ua.contains("Chrome"))
            return "Safari";
        if (ua.contains("Postman"))
            return "Postman";
        if (ua.contains("curl"))
            return "cURL";
        return ua.length() > 30 ? ua.substring(0, 30) + "…" : ua;
    }

    private double queryCacheHitRatio() {
        BigDecimal r = jdbc.queryForObject(SQL_CACHE_HIT_RATIO, BigDecimal.class);
        return r != null ? r.doubleValue() : 0.0;
    }

    /**
     * Calcula la tasa de operaciones por segundo como un delta real entre llamadas.
     * <ul>
     * <li>Primera llamada: guarda xact_commit + timestamp, devuelve -1 (señal
     * "Calculando…")</li>
     * <li>Llamadas siguientes: (xact_actual − xact_previo) /
     * segundos_transcurridos</li>
     * </ul>
     *
     * @return TPS como tasa real, o -1 en la primera llamada (el frontend muestra
     *         "Calculando…")
     */
    private long computeTpsDelta() {
        Long current = jdbc.queryForObject(SQL_XACT_COMMIT, Long.class);
        if (current == null)
            return -1L;

        Instant now = Instant.now();
        long prev = prevXactCommit.get();
        Instant prevT = prevTpsTime.get();

        // Siempre actualizar los valores guardados
        prevXactCommit.set(current);
        prevTpsTime.set(now);

        // Primera llamada
        if (prev < 0 || prevT == null) {
            return -1L;
        }

        double seconds = (now.toEpochMilli() - prevT.toEpochMilli()) / 1000.0;
        if (seconds < 0.5)
            return -1L; // evitar división con intervalo muy corto

        long delta = current - prev;
        if (delta < 0)
            return 0L; // reset de estadísticas de pg_stat_database

        return Math.round(delta / seconds);
    }

    private double queryAvgQueryMs() {
        BigDecimal r = jdbc.queryForObject(SQL_AVG_QUERY_MS, BigDecimal.class);
        return r != null ? r.doubleValue() : 0.0;
    }

    private List<TableMetricDto> queryTopTables() {
        return jdbc.query(SQL_TOP_TABLES, (rs, rowNum) -> new TableMetricDto(
                rs.getString(1),
                rs.getLong(2),
                rs.getLong(3),
                rs.getLong(4)));
    }

    private List<TableHealthDto> queryTableHealth() {
        return jdbc.query(SQL_TABLE_HEALTH, (rs, rowNum) -> {
            String tableName = rs.getString(1);
            long live = rs.getLong(2);
            long dead = rs.getLong(3);
            // col 4 = total_size (texto, no usado en DTO actual)
            // col 5 = last_vacuum_any: GREATEST(last_vacuum, last_autovacuum) — captura
            // tanto VACUUMs manuales (last_vacuum) como automáticos (last_autovacuum)
            String lastVacuum = rs.getString(5);
            // col 6 = last_autoanalyze (no usado en DTO actual)

            // Bloat: dead/(live+dead)*100, 0 si total < 10
            double bloat = (live + dead < 10) ? 0.0
                    : BigDecimal.valueOf(dead * 100.0 / (live + dead))
                            .setScale(2, RoundingMode.HALF_UP).doubleValue();

            // Usar el método canónico unificado — mismo cálculo que Mantenimiento
            String statusRaw = calculateTableStatus(live, dead);

            // Normalizar status al dominio del DTO: 'ok' → 'optimal'
            String status = "critical".equals(statusRaw) ? "critical"
                    : "warning".equals(statusRaw) ? "warning"
                            : "optimal";

            return new TableHealthDto(tableName, live, dead, bloat, lastVacuum, status);
        });
    }

    /**
     * Método canónico de cálculo de estado de tabla por dead tuples.
     * Usado tanto en Monitoreo (aquí) como en el módulo de Mantenimiento
     * ({@link DatabaseMaintenanceService#getDeadTuplesStats()}).
     *
     * <p>
     * Umbrales:
     * </p>
     * <ul>
     * <li><b>critical</b>: dead &gt; 20 Y bloat &gt; 30 %</li>
     * <li><b>warning</b>: dead &gt; 10 Y bloat &gt; 20 %</li>
     * <li><b>ok</b>: el resto</li>
     * </ul>
     *
     * @param liveTuples registros vivos de la tabla
     * @param deadTuples registros obsoletos de la tabla
     * @return "critical" | "warning" | "ok"
     */
    private String calculateTableStatus(long liveTuples, long deadTuples) {
        if (liveTuples + deadTuples == 0)
            return "ok";
        double bloatPct = (double) deadTuples / (liveTuples + deadTuples) * 100.0;
        if (deadTuples > DEAD_CRIT_ABSOLUTE && bloatPct > DEAD_PCT_CRIT)
            return "critical";
        if (deadTuples > DEAD_WARN_ABSOLUTE && bloatPct > DEAD_PCT_WARN)
            return "warning";
        return "ok";
    }

    private List<IndexUsageDto> queryIndexUsage() {
        return jdbc.query(SQL_INDEX_USAGE, (rs, rowNum) -> {
            String indexName = rs.getString(1);
            String tableName = rs.getString(2);
            long idxScans = rs.getLong(3);
            long seqScans = rs.getLong(4);

            long total = idxScans + seqScans;
            double effPct = total > 0
                    ? BigDecimal.valueOf(idxScans * 100.0 / total)
                            .setScale(1, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;

            String status;
            if (idxScans == 0 && seqScans == 0) {
                status = "unused";
            } else if (effPct < IDX_EFF_WARN) {
                status = "low-efficiency";
            } else {
                status = "active";
            }

            return new IndexUsageDto(indexName, tableName, idxScans, seqScans, effPct, status);
        });
    }

    // ── Generador de alertas ─────────────────────────────────────────────────

    private List<DbAlertDto> generateAlerts(
            List<TableHealthDto> tableHealth,
            List<IndexUsageDto> indexUsage,
            ConnectionInfoDto connections,
            PerformanceMetricsDto performance) {

        List<DbAlertDto> alerts = new ArrayList<>();
        AtomicInteger seq = new AtomicInteger(1);

        // — Alertas de tablas — (basadas en status ya calculado en queryTableHealth)
        for (TableHealthDto t : tableHealth) {
            if ("critical".equals(t.status())) {
                alerts.add(new DbAlertDto(
                        "tbl-dead-" + seq.getAndIncrement(), "critical", "Tablas",
                        "La tabla \"" + t.tableName() + "\" tiene " + t.deadTuples() + " registros obsoletos",
                        t.deadTuples() + " registros obsoletos",
                        "Ve al módulo de Mantenimiento y ejecuta VACUUM ANALYZE en \"" + t.tableName() + "\"."));
            } else if ("warning".equals(t.status())) {
                alerts.add(new DbAlertDto(
                        "tbl-dead-" + seq.getAndIncrement(), "warning", "Tablas",
                        "La tabla \"" + t.tableName() + "\" tiene " + t.deadTuples() + " registros obsoletos",
                        t.deadTuples() + " registros obsoletos",
                        "Ejecuta VACUUM ANALYZE en \"" + t.tableName() + "\" para limpiar espacio."));
            }
        }

        // — Alertas de índices —
        for (IndexUsageDto idx : indexUsage) {
            // Obtener registros vivos de la tabla correspondiente
            long liveRows = tableHealth.stream()
                    .filter(t -> t.tableName().equals(idx.tableName()))
                    .mapToLong(TableHealthDto::estimatedRows)
                    .findFirst()
                    .orElse(0L);

            if ("unused".equals(idx.status())) {
                alerts.add(new DbAlertDto(
                        "idx-unused-" + seq.getAndIncrement(), "critical", "Índices",
                        "El índice \"" + idx.indexName() + "\" no se ha utilizado nunca",
                        "0 búsquedas",
                        "Considera eliminar este índice para liberar espacio y mejorar el rendimiento de escritura."));
            } else if ("low-efficiency".equals(idx.status())) {
                // Solo generar alerta si hay suficientes datos y uso real
                boolean hasRealData = liveRows >= IDX_MIN_LIVE_ROWS;
                boolean hasRealUsage = idx.seqScans() >= IDX_MIN_SEQ_SCANS;
                boolean lowEfficiency = idx.efficiencyPct() < IDX_ALERT_EFF_THRESHOLD;

                if (hasRealData && hasRealUsage && lowEfficiency) {
                    // Tabla con datos reales: sugerir REINDEX solo si tiene >100 registros
                    String hint = liveRows >= IDX_REINDEX_MIN_ROWS
                            ? "Ejecuta REINDEX TABLE " + idx.tableName() + " para reconstruir los índices."
                            : "La eficiencia baja es normal mientras la tabla tiene pocos registros. "
                                    + "Se optimizará automáticamente cuando crezca el volumen de datos.";
                    alerts.add(new DbAlertDto(
                            "idx-low-" + seq.getAndIncrement(), "warning", "Índices",
                            "El índice \"" + idx.indexName() + "\" tiene eficiencia baja (" + idx.efficiencyPct()
                                    + "%)",
                            idx.efficiencyPct() + "% — " + idx.seqScans() + " escaneos secuenciales",
                            hint));
                }
                // Si la tabla tiene < 50 registros: no generar alerta (normal en desarrollo)
            }
        }

        // — Alertas de conexiones —
        if (connections.usagePct() >= CONN_CRIT_PCT) {
            alerts.add(new DbAlertDto(
                    "conn-crit-" + seq.getAndIncrement(), "critical", "Conexiones",
                    "El uso de conexiones supera el 90% del límite",
                    connections.total() + " / " + connections.maxLimit(),
                    "Revisa si hay conexiones ociosas y considera aumentar max_connections."));
        } else if (connections.usagePct() >= CONN_WARN_PCT) {
            alerts.add(new DbAlertDto(
                    "conn-warn-" + seq.getAndIncrement(), "warning", "Conexiones",
                    "El uso de conexiones supera el 70% del límite",
                    connections.total() + " / " + connections.maxLimit(),
                    "Revisa si hay conexiones ociosas que puedan cerrarse."));
        }

        // — Alertas de rendimiento —
        if (performance.cacheHitRatio() < CACHE_HIT_CRIT) {
            alerts.add(new DbAlertDto(
                    "perf-cache-" + seq.getAndIncrement(), "critical", "Rendimiento",
                    "El cache hit ratio es críticamente bajo (" + performance.cacheHitRatio() + "%)",
                    performance.cacheHitRatio() + "%",
                    "Revisa las consultas más frecuentes y considera aumentar shared_buffers."));
        } else if (performance.cacheHitRatio() < CACHE_HIT_WARN) {
            alerts.add(new DbAlertDto(
                    "perf-cache-" + seq.getAndIncrement(), "warning", "Rendimiento",
                    "El cache hit ratio está por debajo del 95% recomendado (" + performance.cacheHitRatio() + "%)",
                    performance.cacheHitRatio() + "%",
                    "Revisa las consultas más frecuentes para mejorar la eficiencia del cache."));
        }

        return alerts;
    }

    // ── Cálculo del score global ─────────────────────────────────────────────

    /**
     * Calcula un score global de 0–100 basado en los indicadores más importantes.
     *
     * <ul>
     * <li><b>Cache (40 pts)</b>: ≥99% → 40, ≥95% → 35, proporcional por debajo</li>
     * <li><b>Conexiones (20 pts)</b>: ≤50% → 20, ≤80% → 15, proporcional por
     * debajo</li>
     * <li><b>Alertas críticas (25 pts)</b>: 0 → 25, 1 → 18, 2+ → −8 por cada
     * una</li>
     * <li><b>Índices sin uso (15 pts)</b>: −3 por cada índice "unused"
     * confirmado</li>
     * </ul>
     */
    private int computeHealthScore(double cacheHitRatio, double connUsagePct,
            List<TableHealthDto> tables, List<IndexUsageDto> indexes,
            List<DbAlertDto> alerts) {
        // Cache (40 pts)
        double cacheScore;
        if (cacheHitRatio >= 99.0)
            cacheScore = 40.0;
        else if (cacheHitRatio >= 95.0)
            cacheScore = 35.0;
        else
            cacheScore = (cacheHitRatio / 100.0) * 40.0;

        // Conexiones (20 pts)
        double connScore;
        if (connUsagePct <= 50.0)
            connScore = 20.0;
        else if (connUsagePct <= 80.0)
            connScore = 15.0;
        else
            connScore = Math.max(0.0, (1.0 - connUsagePct / 100.0) * 20.0);

        // Alertas críticas (25 pts): factor principal de penalización
        long criticalAlerts = alerts.stream().filter(a -> "critical".equals(a.level())).count();
        double alertScore;
        if (criticalAlerts == 0)
            alertScore = 25.0;
        else if (criticalAlerts == 1)
            alertScore = 18.0;
        else
            alertScore = Math.max(0.0, 25.0 - criticalAlerts * 8.0);

        // Índices sin uso reales (15 pts): solo "unused", −3 por cada uno
        long unusedIdx = indexes.stream().filter(i -> "unused".equals(i.status())).count();
        double idxScore = Math.max(0.0, 15.0 - unusedIdx * 3.0);

        int total = (int) Math.round(cacheScore + connScore + alertScore + idxScore);
        int bounded = Math.max(0, Math.min(100, total));

        log.debug("[Score] cache={} pts (ratio={}%), conns={} pts (uso={}%), "
                + "alertas={} pts ({} críticas), indices={} pts ({} unused) → total={}",
                Math.round(cacheScore), String.format("%.1f", cacheHitRatio),
                Math.round(connScore), String.format("%.1f", connUsagePct),
                Math.round(alertScore), criticalAlerts,
                Math.round(idxScore), unusedIdx,
                bounded);

        return bounded;
    }
}
