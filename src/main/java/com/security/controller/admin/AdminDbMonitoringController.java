package com.security.controller.admin;

import com.security.dto.admin.DatabaseMetricsDto;
import com.security.service.DatabaseMonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para métricas de monitoreo de la base de datos.
 *
 * <ul>
 * <li>GET /api/admin/database/metrics — Métricas de salud de PostgreSQL</li>
 * <li>GET /api/admin/database/connections/debug — Filas crudas de
 * pg_stat_activity</li>
 * </ul>
 *
 * <p>
 * Acceso exclusivo para {@code ROLE_SUPER_ADMIN}.
 * </p>
 */
@RestController
@RequestMapping("/api/admin/database")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminDbMonitoringController {

    private static final Logger log = LoggerFactory.getLogger(AdminDbMonitoringController.class);

    private final DatabaseMonitoringService monitoringService;

    public AdminDbMonitoringController(DatabaseMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    /**
     * Retorna métricas actuales de la base de datos PostgreSQL.
     *
     * @return 200 OK con {@link DatabaseMetricsDto}
     */
    @GetMapping("/metrics")
    public ResponseEntity<DatabaseMetricsDto> getMetrics() {
        log.info("[Admin] Solicitud de métricas de monitoreo de BD");
        DatabaseMetricsDto metrics = monitoringService.getMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Endpoint de diagnóstico: devuelve las filas crudas de
     * {@code pg_stat_activity}
     * para depurar la clasificación de conexiones (pool vs. internas vs. usuario).
     *
     * <p>
     * Útil para verificar qué {@code application_name} envía cada cliente y
     * ajustar los filtros SQL del servicio.
     * </p>
     *
     * @return 200 OK con lista de mapas columna→valor
     */
    @GetMapping("/connections/debug")
    public ResponseEntity<List<Map<String, Object>>> getConnectionsDebug() {
        log.info("[Admin] Debug: listando filas de pg_stat_activity");
        List<Map<String, Object>> rows = monitoringService.getConnectionsDebug();
        return ResponseEntity.ok(rows);
    }
}
