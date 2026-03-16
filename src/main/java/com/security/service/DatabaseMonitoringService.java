package com.security.service;

import com.security.dto.admin.DatabaseMetricsDto;
import com.security.dto.admin.TableMetricDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio de monitoreo de base de datos.
 *
 * <p>Todas las consultas usan {@link JdbcTemplate} con SQL nativo sobre las
 * vistas del sistema de PostgreSQL. No depende de JPA ni de entidades.</p>
 *
 * <p>Consultas utilizadas:
 * <ul>
 *   <li>{@code pg_database_size()} — tamaño total de la BD en bytes</li>
 *   <li>{@code pg_stat_activity}   — conexiones activas en la BD actual</li>
 *   <li>{@code pg_statio_user_tables} — cache hit ratio de bloques de heap</li>
 *   <li>{@code pg_stat_user_tables} + {@code pg_total_relation_size()} — top 5 tablas</li>
 * </ul>
 * </p>
 */
@Service
public class DatabaseMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMonitoringService.class);

    // ── SQL ───────────────────────────────────────────────────────────────────

    private static final String SQL_DB_SIZE =
            "SELECT pg_database_size(current_database())";

    private static final String SQL_ACTIVE_CONNECTIONS =
            "SELECT count(*) " +
            "FROM pg_stat_activity " +
            "WHERE datname = current_database() AND state = 'active'";

    /** Retorna el cache hit ratio como porcentaje (0–100). Devuelve 0 si no hay lecturas. */
    private static final String SQL_CACHE_HIT_RATIO =
            "SELECT COALESCE( " +
            "  ROUND( " +
            "    (sum(heap_blks_hit) / " +
            "     NULLIF(sum(heap_blks_hit) + sum(heap_blks_read), 0) " +
            "    ) * 100, 2 " +
            "  ), 0) " +
            "FROM pg_statio_user_tables";

    private static final String SQL_TOP_TABLES =
            "SELECT relname, " +
            "       pg_total_relation_size(relid), " +
            "       pg_indexes_size(relid), " +
            "       n_live_tup " +
            "FROM pg_stat_user_tables " +
            "ORDER BY pg_total_relation_size(relid) DESC " +
            "LIMIT 5";

    // ── Dependencias ──────────────────────────────────────────────────────────

    private final JdbcTemplate jdbc;

    public DatabaseMonitoringService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Recopila todas las métricas de salud de la base de datos en una sola llamada.
     *
     * @return {@link DatabaseMetricsDto} con los datos actuales de PostgreSQL.
     */
    public DatabaseMetricsDto getMetrics() {
        log.debug("[Monitoring] Consultando métricas de la base de datos...");

        long   dbSizeBytes        = queryDbSize();
        int    activeConnections  = queryActiveConnections();
        double cacheHitRatio      = queryCacheHitRatio();
        List<TableMetricDto> top  = queryTopTables();

        log.debug("[Monitoring] Métricas obtenidas: size={}B, conexiones={}, cache={}%",
                dbSizeBytes, activeConnections, cacheHitRatio);

        return new DatabaseMetricsDto(dbSizeBytes, activeConnections, cacheHitRatio, top);
    }

    // ── Métodos privados ──────────────────────────────────────────────────────

    private long queryDbSize() {
        Long result = jdbc.queryForObject(SQL_DB_SIZE, Long.class);
        return result != null ? result : 0L;
    }

    private int queryActiveConnections() {
        Long result = jdbc.queryForObject(SQL_ACTIVE_CONNECTIONS, Long.class);
        return result != null ? result.intValue() : 0;
    }

    private double queryCacheHitRatio() {
        // COALESCE en la query garantiza que nunca sea null, pero lo manejamos igual
        java.math.BigDecimal result =
                jdbc.queryForObject(SQL_CACHE_HIT_RATIO, java.math.BigDecimal.class);
        return result != null ? result.doubleValue() : 0.0;
    }

    private List<TableMetricDto> queryTopTables() {
        return jdbc.query(SQL_TOP_TABLES, (rs, rowNum) -> new TableMetricDto(
                rs.getString(1),   // relname
                rs.getLong(2),     // pg_total_relation_size
                rs.getLong(3),     // pg_indexes_size
                rs.getLong(4)      // n_live_tup
        ));
    }
}
