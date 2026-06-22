package com.security.controller.admin;

import com.security.dto.admin.AutomationDto;
import com.security.dto.admin.ExecutionLogDto;
import com.security.dto.admin.StaffRecipientDto;
import com.security.dto.admin.ToggleAutomationRequest;
import com.security.dto.admin.UpdateAutomationRequest;
import com.security.entity.User;
import com.security.repository.UserRepository;
import com.security.security.UserPrincipal;
import com.security.service.automation.DynamicSchedulerService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Endpoints REST para gestionar las automatizaciones del sistema.
 *
 * <p>
 * Todos los endpoints requieren {@code ROLE_SUPER_ADMIN}.
 * La URL base cae bajo {@code /api/admin/**}, que ya está protegida
 * por la configuración de seguridad.
 * </p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 * <li>{@code GET    /api/admin/automations} — Lista todas</li>
 * <li>{@code PATCH  /api/admin/automations/{id}/status} — Toggle on/off</li>
 * <li>{@code PUT    /api/admin/automations/{id}} — Actualiza config</li>
 * <li>{@code POST   /api/admin/automations/{id}/run} — Ejecución manual</li>
 * <li>{@code GET    /api/admin/automations/tables} — Lista tablas de la BD</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/automations")
@PreAuthorize("hasAuthority('DATABASE_AUTOMATE')")
public class AdminAutomationController {

    private static final Logger log = LoggerFactory.getLogger(AdminAutomationController.class);

    private final DynamicSchedulerService schedulerService;
    private final DataSource dataSource;
    private final UserRepository userRepository;

    public AdminAutomationController(DynamicSchedulerService schedulerService, DataSource dataSource,
            UserRepository userRepository) {
        this.schedulerService = schedulerService;
        this.dataSource = dataSource;
        this.userRepository = userRepository;
    }

    // ── GET: Listar todas las automatizaciones ──────────────────────────────

    @GetMapping
    public ResponseEntity<List<AutomationDto>> listAll() {
        return ResponseEntity.ok(schedulerService.listAll());
    }

    // ── PATCH: Encender/Apagar (Toggle) ─────────────────────────────────────

    @PatchMapping("/{id}/status")
    public ResponseEntity<AutomationDto> toggleStatus(
            @PathVariable Long id,
            @Valid @RequestBody ToggleAutomationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[AdminAutomation] {} → {} toggle (enabled={})",
                principal.getUsername(), id, request.enabled());

        AutomationDto result = schedulerService.toggleStatus(id, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    // ── PUT: Actualizar cron, timezone, parámetros ──────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<AutomationDto> updateConfig(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAutomationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[AdminAutomation] {} → {} update (cron={}, tz={})",
                principal.getUsername(), id, request.cronExpression(), request.timezone());

        AutomationDto result = schedulerService.updateConfig(id, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    // ── POST: Ejecución manual inmediata ────────────────────────────────────

    @PostMapping("/{id}/run")
    public ResponseEntity<Map<String, Object>> runNow(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[AdminAutomation] {} → {} ejecución manual", principal.getUsername(), id);

        AutomationDto result = schedulerService.runNow(id, principal.getId(), principal.getUsername());
        return ResponseEntity.accepted().body(Map.of(
                "message", "Tarea '" + result.displayName() + "' ejecutándose en segundo plano.",
                "jobName", result.jobName(),
                "status", "IN_PROGRESS"));
    }

    // ── GET: Historial de ejecuciones de una automatización ─────────────────

    /**
     * Retorna el historial paginado de ejecuciones de una automatización.
     *
     * @param id   ID de la automatización
     * @param page Número de página (0-based, default 0)
     * @param size Tamaño de página (default 10, máx 50)
     */
    @GetMapping("/{id}/executions")
    public ResponseEntity<Page<ExecutionLogDto>> getExecutionLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(schedulerService.getExecutionLogs(id, page, size));
    }

    // ── GET: Listar empleados activos para selector de destinatarios ──────

    /**
     * Retorna la lista de empleados activos (habilitados y no bloqueados)
     * para el selector de destinatarios de notificaciones.
     * Solo expone datos no-sensibles: id, nombre completo, email y rol principal.
     */
    @GetMapping("/staff-recipients")
    public ResponseEntity<List<StaffRecipientDto>> listStaffRecipients() {
        List<User> activeStaff = userRepository.findActiveStaffMembers();

        List<StaffRecipientDto> recipients = activeStaff.stream()
                .map(u -> {
                    String role = u.getRoles().stream()
                            .map(r -> r.getName())
                            .findFirst()
                            .orElse("Sin rol");
                    boolean isSuperAdmin = u.getRoles().stream()
                            .anyMatch(r -> "ROLE_SUPER_ADMIN".equals(r.getName()));
                    return new StaffRecipientDto(
                            u.getId(),
                            u.getFirstName() + " " + u.getLastName(),
                            u.getEmail(),
                            role,
                            isSuperAdmin);
                })
                .toList();

        return ResponseEntity.ok(recipients);
    }

    // ── GET: Listar tablas de la BD (para respaldos parciales) ──────────────

    /**
     * Retorna los nombres de todas las tablas del esquema público de la base de
     * datos. Se usa en el frontend para el selector de tablas en respaldos
     * parciales. La consulta usa {@code information_schema.tables} (estándar SQL)
     * y filtra solo tablas del usuario (excluye Flyway, system_automations, etc.
     * de monitoreo).
     */
    @GetMapping("/tables")
    public ResponseEntity<List<Map<String, Object>>> listDatabaseTables() {
        List<Map<String, Object>> tables = new ArrayList<>();
        String sql = """
                SELECT t.table_schema || '.' || t.table_name AS table_name,
                       COALESCE(s.n_live_tup, 0) AS row_estimate
                FROM information_schema.tables t
                LEFT JOIN pg_stat_user_tables s
                       ON s.relname = t.table_name
                         AND s.schemaname = t.table_schema
                WHERE t.table_schema IN ('auth','security','catalog','sales','customer','ops')
                  AND t.table_type   = 'BASE TABLE'
                  AND t.table_name NOT LIKE 'flyway%'
                ORDER BY COALESCE(s.n_live_tup, 0) DESC, t.table_name
                """;
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tables.add(Map.of(
                        "name", rs.getString("table_name"),
                        "rowEstimate", rs.getLong("row_estimate")));
            }
        } catch (Exception e) {
            log.error("[AdminAutomation] Error listando tablas: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok(tables);
    }
}
