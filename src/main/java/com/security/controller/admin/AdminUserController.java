package com.security.controller.admin;

import com.security.dto.admin.AdminUserCreateDTO;
import com.security.dto.admin.AdminUserListDTO;
import com.security.dto.admin.AdminUserResponseDTO;
import com.security.dto.admin.AdminUserUpdateDTO;
import com.security.service.admin.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST para gestión de usuarios Staff (is_customer = false)
 * Todos los endpoints están protegidos con @PreAuthorize
 */
@RestController
@RequestMapping("/api/admin/staff")
@CrossOrigin(origins = { "http://localhost:4200", "https://login.up.railway.app" }, allowCredentials = "true")
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    /**
     * GET /api/admin/staff - Listar todos los usuarios Staff (paginado)
     * Requiere: ROLE_ADMIN o ROLE_SUPER_ADMIN
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllStaff(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Page<AdminUserListDTO> staffPage = adminUserService.getAllStaff(page, size, sortBy, sortDir);

        Map<String, Object> response = new HashMap<>();
        response.put("staff", staffPage.getContent());
        response.put("currentPage", staffPage.getNumber());
        response.put("totalItems", staffPage.getTotalElements());
        response.put("totalPages", staffPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/admin/staff/search - Buscar usuarios Staff con filtros
     * Requiere: ROLE_ADMIN o ROLE_SUPER_ADMIN
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> searchStaff(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean accountNonLocked,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<AdminUserListDTO> staffPage = adminUserService.searchStaff(
                searchTerm, enabled, accountNonLocked, page, size);

        Map<String, Object> response = new HashMap<>();
        response.put("staff", staffPage.getContent());
        response.put("currentPage", staffPage.getNumber());
        response.put("totalItems", staffPage.getTotalElements());
        response.put("totalPages", staffPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/admin/staff/{id} - Obtener detalles de un usuario Staff
     * Requiere: ROLE_ADMIN o ROLE_SUPER_ADMIN
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AdminUserResponseDTO> getStaffById(@PathVariable Long id) {
        AdminUserResponseDTO staff = adminUserService.getStaffById(id);
        return ResponseEntity.ok(staff);
    }

    /**
     * POST /api/admin/staff - Crear un nuevo usuario Staff
     * Requiere: ROLE_ADMIN o ROLE_SUPER_ADMIN
     * CRITICAL: Fuerza is_customer = false y encripta el password
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> createStaffUser(@Valid @RequestBody AdminUserCreateDTO dto) {
        AdminUserResponseDTO createdUser = adminUserService.createStaffUser(dto);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Usuario Staff creado exitosamente");
        response.put("user", createdUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /api/admin/staff/{id} - Actualizar un usuario Staff
     * Requiere: ROLE_ADMIN o ROLE_SUPER_ADMIN
     * CRITICAL: Valida que no se modifique a sí mismo si intenta quitarse
     * ROLE_ADMIN
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateStaffUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateDTO dto) {
        AdminUserResponseDTO updatedUser = adminUserService.updateStaffUser(id, dto);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Usuario Staff actualizado exitosamente");
        response.put("user", updatedUser);

        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/admin/staff/{id}/toggle-enabled - Activar/Desactivar usuario (Soft
     * Delete)
     * Requiere: ROLE_ADMIN o ROLE_SUPER_ADMIN
     * CRITICAL: No permite desactivarse a sí mismo
     */
    @PatchMapping("/{id}/toggle-enabled")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> toggleEnabledStatus(@PathVariable Long id) {
        AdminUserResponseDTO updatedUser = adminUserService.toggleEnabledStatus(id);

        Map<String, Object> response = new HashMap<>();
        response.put("message", updatedUser.getEnabled()
                ? "Usuario activado exitosamente"
                : "Usuario desactivado exitosamente");
        response.put("user", updatedUser);

        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/admin/staff/{id}/toggle-locked - Bloquear/Desbloquear cuenta
     * Requiere: ROLE_ADMIN o ROLE_SUPER_ADMIN
     * CRITICAL: No permite bloquearse a sí mismo
     */
    @PatchMapping("/{id}/toggle-locked")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> toggleLockedStatus(@PathVariable Long id) {
        AdminUserResponseDTO updatedUser = adminUserService.toggleLockedStatus(id);

        Map<String, Object> response = new HashMap<>();
        response.put("message", updatedUser.getAccountNonLocked()
                ? "Cuenta desbloqueada exitosamente"
                : "Cuenta bloqueada exitosamente");
        response.put("user", updatedUser);

        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/admin/staff/{id} - Eliminar un usuario Staff
     * Requiere: ROLE_SUPER_ADMIN (operación destructiva)
     * CRITICAL: No permite eliminarse a sí mismo
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteStaffUser(@PathVariable Long id) {
        adminUserService.deleteStaffUser(id);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Usuario eliminado exitosamente");

        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/admin/staff/{id}/reset-failed-attempts - Resetear intentos
     * fallidos
     * Requiere: ROLE_ADMIN o ROLE_SUPER_ADMIN
     */
    @PatchMapping("/{id}/reset-failed-attempts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> resetFailedAttempts(@PathVariable Long id) {
        AdminUserResponseDTO updatedUser = adminUserService.resetFailedLoginAttempts(id);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Intentos fallidos reseteados y cuenta desbloqueada");
        response.put("user", updatedUser);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/admin/staff/export/csv - Exportar lista de Staff a CSV
     * Requiere: ROLE_ADMIN o ROLE_SUPER_ADMIN
     */
    @GetMapping("/export/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportToCsv(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long roleId) {
        byte[] csv = adminUserService.exportStaffToCsv(search, roleId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.setContentDispositionFormData("attachment",
                "staff-" + java.time.LocalDate.now() + ".csv");

        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }
}
