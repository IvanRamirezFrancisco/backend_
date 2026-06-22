package com.security.controller.admin;

import com.security.dto.admin.*;
import com.security.service.SlowQueriesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para auditoría de consultas lentas en PostgreSQL.
 *
 * <p>
 * Endpoints:
 * </p>
 * <ol>
 * <li>GET /api/admin/slow-queries/active — Queries activas en tiempo real</li>
 * <li>GET /api/admin/slow-queries/top-expensive — Top 10 queries más costosas
 * (historial)</li>
 * <li>GET /api/admin/slow-queries/locks — Bloqueos activos entre sesiones</li>
 * <li>GET /api/admin/slow-queries/table-stats — Estadísticas de tablas con más
 * carga</li>
 * <li>GET /api/admin/slow-queries/config — Configuración relevante de
 * PostgreSQL</li>
 * </ol>
 *
 * <p>
 * <strong>Seguridad:</strong> Acceso exclusivo para {@code ROLE_SUPER_ADMIN}.
 * Todas las consultas son de solo lectura sobre vistas del sistema PostgreSQL.
 * </p>
 */
@RestController
@RequestMapping("/api/admin/slow-queries")
@PreAuthorize("hasAuthority('DATABASE_VIEW')")
public class SlowQueriesController {

    private static final Logger log = LoggerFactory.getLogger(SlowQueriesController.class);

    private final SlowQueriesService service;

    public SlowQueriesController(SlowQueriesService service) {
        this.service = service;
    }

    /**
     * Obtiene las queries activas en este momento desde {@code pg_stat_activity}.
     * Cada query incluye su clasificación de severidad (NORMAL, WATCH, SLOW,
     * BLOCKED, IDLE_TX).
     *
     * @return 200 OK con lista de {@link ActiveQueryDto}
     */
    @GetMapping("/active")
    public ResponseEntity<List<ActiveQueryDto>> getActiveQueries() {
        log.info("[Admin] Solicitud de queries activas");
        List<ActiveQueryDto> queries = service.getActiveQueries();
        return ResponseEntity.ok(queries);
    }

    /**
     * Obtiene las 10 queries más costosas en promedio desde
     * {@code pg_stat_statements}.
     * Si la extensión no está instalada, devuelve {@code available: false} con
     * mensaje informativo.
     *
     * @return 200 OK con {@link TopExpensiveQueriesResponse}
     */
    @GetMapping("/top-expensive")
    public ResponseEntity<TopExpensiveQueriesResponse> getTopExpensive() {
        log.info("[Admin] Solicitud de queries costosas (pg_stat_statements)");
        TopExpensiveQueriesResponse response = service.getTopExpensiveQueries();
        return ResponseEntity.ok(response);
    }

    /**
     * Detecta bloqueos activos entre sesiones de PostgreSQL.
     *
     * @return 200 OK con lista de {@link ActiveLockDto}
     */
    @GetMapping("/locks")
    public ResponseEntity<List<ActiveLockDto>> getActiveLocks() {
        log.info("[Admin] Solicitud de bloqueos activos");
        List<ActiveLockDto> locks = service.getActiveLocks();
        return ResponseEntity.ok(locks);
    }

    /**
     * Obtiene estadísticas de acceso de las tablas con más escaneos secuenciales.
     * Un número alto de seq scans en tablas grandes indica la necesidad de índices.
     *
     * @return 200 OK con lista de {@link TableStatsDto}
     */
    @GetMapping("/table-stats")
    public ResponseEntity<List<TableStatsDto>> getTableStats() {
        log.info("[Admin] Solicitud de estadísticas de tablas");
        List<TableStatsDto> stats = service.getTableStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Obtiene parámetros de configuración de PostgreSQL relevantes para
     * el análisis de queries lentas (solo lectura).
     *
     * @return 200 OK con lista de {@link PgSettingDto}
     */
    @GetMapping("/config")
    public ResponseEntity<List<PgSettingDto>> getPgConfig() {
        log.info("[Admin] Solicitud de configuración de PostgreSQL");
        List<PgSettingDto> config = service.getPgConfig();
        return ResponseEntity.ok(config);
    }
}
