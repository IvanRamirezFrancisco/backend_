package com.security.controller.admin;

import com.security.dto.admin.MaintenanceConfigDto;
import com.security.dto.admin.MaintenanceLogDto;
import com.security.dto.admin.TableMaintenanceDto;
import com.security.service.DatabaseMaintenanceService;
import com.security.service.MaintenanceSchedulerService;
import com.security.util.LogSanitizer;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
@PreAuthorize("hasAuthority('DATABASE_MAINTAIN')")
public class AdminDbMaintenanceController {

    private static final Logger log = LoggerFactory.getLogger(AdminDbMaintenanceController.class);

    private final DatabaseMaintenanceService maintenanceService;
    private final MaintenanceSchedulerService schedulerService;

    public AdminDbMaintenanceController(
            DatabaseMaintenanceService maintenanceService,
            MaintenanceSchedulerService schedulerService) {
        this.maintenanceService = maintenanceService;
        this.schedulerService = schedulerService;
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
     * estadísticas del planificador, sin limpiar dead tuples) y registra
     * la operación en el historial.
     *
     * @param tableName nombre de la tabla
     * @return 200 OK con mensaje de éxito y timestamp de ejecución
     */
    @PostMapping("/analyze/{tableName}")
    public ResponseEntity<Map<String, Object>> runAnalyze(@PathVariable String tableName) {
        log.info("[Admin] Ejecutando ANALYZE en tabla '{}'...", LogSanitizer.sanitize(tableName));
        maintenanceService.runAnalyzeWithLog(tableName);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "operation", "ANALYZE " + tableName,
                "message", "ANALYZE ejecutado correctamente en la tabla '" + tableName + "'.",
                "executedAt", LocalDateTime.now().toString()));
    }

    /**
     * Ejecuta {@code ANALYZE} global sobre todas las tablas de la base de datos.
     * Operación ligera, no bloquea tablas.
     *
     * @return 200 OK con mensaje de éxito
     */
    @PostMapping("/analyze-all")
    public ResponseEntity<Map<String, Object>> runAnalyzeAll() {
        log.info("[Admin] Ejecutando ANALYZE global...");
        maintenanceService.runAnalyzeAll();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "operation", "ANALYZE (todas las tablas)",
                "message", "ANALYZE ejecutado correctamente en todas las tablas.",
                "executedAt", LocalDateTime.now().toString()));
    }

    // ── Endpoints de Automatización ─────────────────────────────────────────

    /**
     * Devuelve la configuración actual del programador de mantenimiento automático.
     *
     * @return 200 OK con {@link MaintenanceConfigDto}
     */
    @GetMapping("/automation")
    public ResponseEntity<MaintenanceConfigDto> getAutomationConfig() {
        log.info("[Admin] Solicitud de configuración de automatización de mantenimiento");
        return ResponseEntity.ok(schedulerService.getConfig());
    }

    /**
     * Actualiza la configuración del programador de mantenimiento automático.
     *
     * <p>
     * Validaciones de seguridad aplicadas:
     * <ul>
     * <li>frequencyHours: 1–24</li>
     * <li>preferredHour: 0–23</li>
     * <li>vacuumThresholdDeadTuples: 1–10 000</li>
     * <li>vacuumThresholdBloatPct: 1–100 %</li>
     * </ul>
     * </p>
     *
     * @param dto nueva configuración validada
     * @return 200 OK con la configuración actualizada
     */
    @PutMapping("/automation")
    public ResponseEntity<MaintenanceConfigDto> updateAutomationConfig(
            @Valid @RequestBody MaintenanceConfigDto dto) {
        log.info("[Admin] Actualizando configuración de automatización: enabled={}, freq={}h",
                dto.enabled(), dto.frequencyHours());
        return ResponseEntity.ok(schedulerService.updateConfig(dto));
    }

    /**
     * Ejecuta el ciclo de mantenimiento automático de forma inmediata,
     * sin esperar la próxima ejecución programada.
     *
     * @return 200 OK
     */
    @PostMapping("/automation/run-now")
    public ResponseEntity<Map<String, Object>> runAutomationNow() {
        log.info("[Admin] Ejecución inmediata del mantenimiento automático solicitada");
        schedulerService.runNow();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Mantenimiento automático ejecutado correctamente.",
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
