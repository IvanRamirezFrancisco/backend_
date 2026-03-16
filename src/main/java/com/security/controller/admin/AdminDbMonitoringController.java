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

/**
 * Controlador REST para métricas de monitoreo de la base de datos.
 *
 * <ul>
 *   <li>GET /api/admin/database/metrics — Retorna métricas de salud de PostgreSQL</li>
 * </ul>
 *
 * <p>Acceso exclusivo para {@code ROLE_SUPER_ADMIN}.</p>
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
     * <p>Incluye: tamaño total de la BD, conexiones activas,
     * cache hit ratio y top 5 tablas por tamaño.</p>
     *
     * @return 200 OK con {@link DatabaseMetricsDto}
     */
    @GetMapping("/metrics")
    public ResponseEntity<DatabaseMetricsDto> getMetrics() {
        log.info("[Admin] Solicitud de métricas de monitoreo de BD");
        DatabaseMetricsDto metrics = monitoringService.getMetrics();
        return ResponseEntity.ok(metrics);
    }
}
