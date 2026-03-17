package com.security.service;

import com.security.dto.admin.TableMaintenanceDto;
import com.security.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

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
     * Obtiene estadísticas de tuplas muertas y vivas de todas las tablas de
     * usuario.
     * Incluye tanto el último autovacuum del daemon como el último VACUUM manual
     * del DBA.
     * Ordenadas de mayor a menor número de tuplas muertas (las más urgentes
     * primero).
     *
     * <p>
     * Columnas relevantes de {@code pg_stat_user_tables}:
     * </p>
     * <ul>
     * <li>{@code last_vacuum} — fecha del último VACUUM manual</li>
     * <li>{@code last_autovacuum} — fecha del último autovacuum del daemon</li>
     * </ul>
     */
    private static final String SQL_DEAD_TUPLES = "SELECT relname, " +
            "       n_dead_tup, " +
            "       n_live_tup, " +
            "       COALESCE(cast(last_autovacuum AS TEXT), 'Nunca') AS last_autovacuum, " +
            "       COALESCE(cast(last_vacuum     AS TEXT), 'Nunca') AS last_vacuum " +
            "FROM pg_stat_user_tables " +
            "ORDER BY n_dead_tup DESC";

    // ── Dependencias ──────────────────────────────────────────────────────────

    private final JdbcTemplate jdbc;

    public DatabaseMaintenanceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Devuelve las estadísticas de mantenimiento de todas las tablas de usuario.
     *
     * <p>
     * Los datos provienen de {@code pg_stat_user_tables}, una vista del catálogo de
     * PostgreSQL que acumula contadores desde el último reset de estadísticas.
     * </p>
     *
     * @return lista de {@link TableMaintenanceDto} ordenada por tuplas muertas
     *         descendente
     */
    public List<TableMaintenanceDto> getDeadTuplesStats() {
        log.debug("[Maintenance] Consultando estadísticas de dead tuples...");

        List<TableMaintenanceDto> stats = jdbc.query(SQL_DEAD_TUPLES, (rs, rowNum) -> new TableMaintenanceDto(
                rs.getString("relname"),
                rs.getLong("n_dead_tup"),
                rs.getLong("n_live_tup"),
                rs.getString("last_autovacuum"),
                rs.getString("last_vacuum")));

        log.debug("[Maintenance] {} tablas analizadas", stats.size());
        return stats;
    }

    /**
     * Ejecuta {@code VACUUM ANALYZE} sobre una tabla específica de forma manual.
     *
     * <p>
     * El nombre de la tabla es sanitizado con la expresión regular
     * {@code ^[a-z_]+$}
     * para prevenir inyección SQL. Solo se permiten letras minúsculas y guiones
     * bajos.
     * </p>
     *
     * <p>
     * <strong>No debe llamarse dentro de un contexto
     * {@code @Transactional}.</strong>
     * </p>
     *
     * @param tableName nombre de la tabla a limpiar (solo {@code [a-z_]})
     * @throws IllegalArgumentException si el nombre de tabla contiene caracteres
     *                                  inválidos
     */
    public void runVacuum(String tableName) {
        sanitize(tableName);
        log.info("[Maintenance] Iniciando VACUUM ANALYZE en tabla '{}'...", LogSanitizer.sanitize(tableName));
        jdbc.execute("VACUUM ANALYZE " + tableName + ";");
        log.info("[Maintenance] VACUUM ANALYZE completado en tabla '{}'", LogSanitizer.sanitize(tableName));
    }

    /**
     * Ejecuta {@code REINDEX TABLE} para reconstruir los índices de una tabla
     * específica.
     *
     * <p>
     * El nombre de la tabla es sanitizado con la expresión regular
     * {@code ^[a-z_]+$}
     * para prevenir inyección SQL. Solo se permiten letras minúsculas y guiones
     * bajos.
     * </p>
     *
     * <p>
     * <strong>No debe llamarse dentro de un contexto
     * {@code @Transactional}.</strong>
     * </p>
     *
     * @param tableName nombre de la tabla cuyos índices se reconstruirán (solo
     *                  {@code [a-z_]})
     * @throws IllegalArgumentException si el nombre de tabla contiene caracteres
     *                                  inválidos
     */
    public void runReindex(String tableName) {
        sanitize(tableName);
        log.info("[Maintenance] Iniciando REINDEX TABLE '{}' manual...", LogSanitizer.sanitize(tableName));
        jdbc.execute("REINDEX TABLE " + tableName + ";");
        log.info("[Maintenance] REINDEX TABLE '{}' completado exitosamente", LogSanitizer.sanitize(tableName));
    }

    // ── Utilidades privadas ───────────────────────────────────────────────────

    /**
     * Sanitiza el nombre de tabla para prevenir SQL Injection.
     * Solo permite letras minúsculas (a-z) y guiones bajos (_).
     */
    private void sanitize(String tableName) {
        if (tableName == null || !tableName.matches("^[a-z_]+$")) {
            throw new IllegalArgumentException(
                    "Nombre de tabla inválido: '" + tableName + "'. " +
                            "Solo se permiten letras minúsculas y guiones bajos.");
        }
    }
}
