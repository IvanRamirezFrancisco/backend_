package com.security.service;

import com.security.dto.admin.TableMaintenanceDto;
import com.security.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

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
     * Actúa como whitelist para prevenir SQL Injection: solo se permiten
     * tablas explícitamente listadas aquí. SonarQube reconoce este patrón
     * como protección efectiva contra CWE-89.
     */
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "users",
            "roles",
            "user_roles",
            "verification_tokens",
            "password_reset_tokens",
            "sessions",
            "products",
            "categories",
            "orders",
            "order_items",
            "cart",
            "cart_items",
            "permissions",
            "role_permissions"
    );

    // ── SQL estático (sin datos de usuario) ──────────────────────────────────

    /**
     * Obtiene estadísticas de tuplas muertas y vivas de todas las tablas de usuario.
     * Query completamente estático — sin datos de usuario.
     */
    private static final String SQL_DEAD_TUPLES =
            "SELECT relname, " +
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
     * El nombre de la tabla se valida contra el whitelist {@link #ALLOWED_TABLES}
     * para prevenir SQL Injection (CWE-89). Solo se aceptan tablas explícitamente
     * enumeradas en esa constante — ningún valor externo puede introducir SQL arbitrario.
     * </p>
     *
     * <p>
     * <strong>No debe llamarse dentro de un contexto {@code @Transactional}.</strong>
     * </p>
     *
     * @param tableName nombre de la tabla a limpiar
     * @throws IllegalArgumentException si el nombre de tabla no está en el whitelist
     */
    public void runVacuum(String tableName) {
        validateTableName(tableName);
        // tableName proviene del whitelist ALLOWED_TABLES — seguro contra SQL injection
        String sql = "VACUUM ANALYZE " + tableName;
        log.info("[Maintenance] Iniciando VACUUM ANALYZE en tabla '{}'...", LogSanitizer.sanitize(tableName));
        jdbc.execute(sql);
        log.info("[Maintenance] VACUUM ANALYZE completado en tabla '{}'", LogSanitizer.sanitize(tableName));
    }

    /**
     * Ejecuta {@code REINDEX TABLE} para reconstruir los índices de una tabla
     * específica.
     *
     * <p>
     * El nombre de la tabla se valida contra el whitelist {@link #ALLOWED_TABLES}
     * para prevenir SQL Injection (CWE-89). Solo se aceptan tablas explícitamente
     * enumeradas en esa constante — ningún valor externo puede introducir SQL arbitrario.
     * </p>
     *
     * <p>
     * <strong>No debe llamarse dentro de un contexto {@code @Transactional}.</strong>
     * </p>
     *
     * @param tableName nombre de la tabla cuyos índices se reconstruirán
     * @throws IllegalArgumentException si el nombre de tabla no está en el whitelist
     */
    public void runReindex(String tableName) {
        validateTableName(tableName);
        // tableName proviene del whitelist ALLOWED_TABLES — seguro contra SQL injection
        String sql = "REINDEX TABLE " + tableName;
        log.info("[Maintenance] Iniciando REINDEX TABLE '{}' manual...", LogSanitizer.sanitize(tableName));
        jdbc.execute(sql);
        log.info("[Maintenance] REINDEX TABLE '{}' completado exitosamente", LogSanitizer.sanitize(tableName));
    }

    // ── Utilidades privadas ───────────────────────────────────────────────────

    /**
     * Valida que el nombre de tabla pertenezca al whitelist de tablas permitidas.
     * Este enfoque es reconocido por SAST (SonarQube) como protección efectiva
     * contra SQL Injection (CWE-89): el valor de entrada se contrasta contra un
     * conjunto finito y hardcoded — no se interpola directamente en la query.
     *
     * @param tableName nombre a validar
     * @throws IllegalArgumentException si el nombre no está en {@link #ALLOWED_TABLES}
     */
    private void validateTableName(String tableName) {
        if (tableName == null || !ALLOWED_TABLES.contains(tableName)) {
            throw new IllegalArgumentException(
                    "Operación de mantenimiento denegada: tabla no permitida.");
        }
    }
}
