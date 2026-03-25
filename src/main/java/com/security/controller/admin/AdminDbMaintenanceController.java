package com.security.controller.admin;

import com.security.dto.admin.MaintenanceLogDto;
import com.security.dto.admin.TableMaintenanceDto;
import com.security.service.DatabaseMaintenanceService;
import com.security.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST para operaciones de mantenimiento manual de la base de
 * datos.
 *
 * <ul>
 * <li>GET /api/admin/database/maintenance/stats — Estadísticas de dead tuples
 * por tabla</li>
 * <li>POST /api/admin/database/maintenance/vacuum/{tableName} — Ejecuta VACUUM
 * ANALYZE en la tabla</li>
 * <li>POST /api/admin/database/maintenance/reindex/{tableName}— Ejecuta REINDEX
 * TABLE en la tabla</li>
 * </ul>
 *
 * <p>
 * Acceso exclusivo para {@code ROLE_SUPER_ADMIN}.
 * </p>
 */
@RestController
@RequestMapping("/api/admin/database/maintenance")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminDbMaintenanceController {

    private static final Logger log = LoggerFactory.getLogger(AdminDbMaintenanceController.class);

    private final DatabaseMaintenanceService maintenanceService;

    public AdminDbMaintenanceController(DatabaseMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    /**
     * Devuelve estadísticas de dead tuples y último autovacuum por tabla de
     * usuario.
     *
     * @return 200 OK con lista de {@link TableMaintenanceDto}
     */
    @GetMapping("/stats")
    public ResponseEntity<List<TableMaintenanceDto>> getStats() {
        log.info("[Admin] Solicitud de estadísticas de mantenimiento de tablas");
        return ResponseEntity.ok(maintenanceService.getDeadTuplesStats());
    }

    /**
     * Ejecuta {@code VACUUM ANALYZE} sobre la tabla especificada.
     *
     * <p>
     * El nombre de la tabla es sanitizado en el servicio ({@code ^[a-z_]+$}) para
     * prevenir inyección SQL.
     * </p>
     *
     * @param tableName nombre de la tabla (solo letras minúsculas y guiones bajos)
     * @return 200 OK con mensaje de éxito y timestamp de ejecución
     */
    @PostMapping("/vacuum/{tableName}")
    public ResponseEntity<Map<String, Object>> runVacuum(@PathVariable String tableName) {
        log.info("[Admin] Ejecutando VACUUM ANALYZE en tabla '{}'...", LogSanitizer.sanitize(tableName));
        maintenanceService.runVacuum(tableName);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "operation", "VACUUM ANALYZE " + tableName,
                "message", "VACUUM ANALYZE ejecutado correctamente en la tabla '" + tableName + "'.",
                "executedAt", LocalDateTime.now().toString()));
    }

    /**
     * Ejecuta {@code REINDEX TABLE} para reconstruir los índices de la tabla
     * especificada.
     *
     * @param tableName nombre de la tabla cuyos índices se reconstruirán
     * @return 200 OK con mensaje de éxito y timestamp de ejecución
     */
    @PostMapping("/reindex/{tableName}")
    public ResponseEntity<Map<String, Object>> runReindex(@PathVariable String tableName) {
        log.info("[Admin] Ejecutando REINDEX TABLE '{}' manual...", LogSanitizer.sanitize(tableName));
        maintenanceService.runReindex(tableName);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "operation", "REINDEX TABLE " + tableName,
                "message", "REINDEX TABLE ejecutado correctamente en la tabla '" + tableName + "'.",
                "executedAt", LocalDateTime.now().toString()));
    }

    /**
     * Ejecuta {@code ANALYZE} sobre la tabla especificada (solo actualiza
     * estadísticas del planificador, sin limpiar dead tuples).
     *
     * @param tableName nombre de la tabla
     * @return 200 OK con mensaje de éxito y timestamp de ejecución
     */
    @PostMapping("/analyze/{tableName}")
    public ResponseEntity<Map<String, Object>> runAnalyze(@PathVariable String tableName) {
        log.info("[Admin] Ejecutando ANALYZE en tabla '{}'...", LogSanitizer.sanitize(tableName));
        maintenanceService.runAnalyze(tableName);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "operation", "ANALYZE " + tableName,
                "message", "ANALYZE ejecutado correctamente en la tabla '" + tableName + "'.",
                "executedAt", LocalDateTime.now().toString()));
    }

    /**
     * Devuelve los parámetros de autovacuum configurados en PostgreSQL.
     *
     * @return 200 OK con lista de parámetros (name, setting, unit)
     */
    @GetMapping("/autovacuum-settings")
    public ResponseEntity<List<Map<String, Object>>> getAutovacuumSettings() {
        log.info("[Admin] Solicitud de parámetros de autovacuum");
        return ResponseEntity.ok(maintenanceService.getAutovacuumSettings());
    }

    /**
     * Devuelve los índices que necesitan reconstrucción, filtrados con tres
     * condiciones estrictas (eficiencia &lt; 75 %, tráfico &gt; 100, registros
     * vivos &gt; 10). Solo incluye índices que realmente requieren REINDEX,
     * sin falsos positivos de tablas vacías.
     *
     * @return 200 OK con lista de índices problemáticos ordenada por eficiencia
     *         ascendente
     */
    @GetMapping("/problematic-indexes")
    public ResponseEntity<List<Map<String, Object>>> getProblematicIndexes() {
        log.info("[Admin] Solicitud de índices problemáticos para mantenimiento");
        return ResponseEntity.ok(maintenanceService.getProblematicIndexes());
    }

    /**
     * Devuelve los últimos 20 registros del historial de operaciones de
     * mantenimiento, ordenados por fecha descendente.
     *
     * @return 200 OK con lista de {@link MaintenanceLogDto}
     */
    @GetMapping("/history")
    public ResponseEntity<List<MaintenanceLogDto>> getMaintenanceHistory() {
        log.info("[Admin] Solicitud de historial de operaciones de mantenimiento");
        return ResponseEntity.ok(maintenanceService.getRecentHistory());
    }
}
