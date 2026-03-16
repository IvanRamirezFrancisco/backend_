package com.security.controller.admin;

import com.security.entity.BackupLog;
import com.security.enums.BackupStatus;
import com.security.repository.BackupLogRepository;
import com.security.service.DatabaseBackupService;
import com.security.service.SupabaseStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Controller de backup de base de datos — arquitectura Supabase Storage.
 *
 * <ul>
 * <li>POST /api/admin/backups/trigger → 202 Accepted (backup asíncrono)</li>
 * <li>GET /api/admin/backups?page=0&amp;size=20 → historial paginado</li>
 * <li>GET /api/admin/backups/{id}/download-url → URL firmada de descarga (1
 * h)</li>
 * </ul>
 *
 * <p>
 * Seguridad: solo {@code ROLE_SUPER_ADMIN} puede acceder.
 * </p>
 */
@RestController
@RequestMapping("/api/admin/backups")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminBackupController {

    private static final int SIGNED_URL_EXPIRY_SECONDS = 3600; // 1 hora

    private final DatabaseBackupService backupService;
    private final SupabaseStorageService supabaseStorage;
    private final BackupLogRepository backupLogRepository;

    public AdminBackupController(DatabaseBackupService backupService,
            SupabaseStorageService supabaseStorage,
            BackupLogRepository backupLogRepository) {
        this.backupService = backupService;
        this.supabaseStorage = supabaseStorage;
        this.backupLogRepository = backupLogRepository;
    }

    // ── POST /trigger ─────────────────────────────────────────────────────────

    /**
     * Dispara el backup en segundo plano y retorna 202 Accepted inmediatamente.
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> trigger(Principal principal) {
        String user = (principal != null) ? principal.getName() : "SYSTEM";
        backupService.triggerManualBackup(user);
        return ResponseEntity.accepted().body(Map.of(
                "message", "Respaldo iniciado en segundo plano.",
                "status", BackupStatus.PENDING.name(),
                "triggeredBy", user,
                "timestamp", LocalDateTime.now().toString()));
    }

    // ── GET / (paginado) ──────────────────────────────────────────────────────

    /**
     * Retorna el historial de backups paginado, más recientes primero.
     *
     * @param page número de página (0-based, por defecto 0)
     * @param size registros por página (por defecto 20)
     */
    @GetMapping
    public ResponseEntity<Page<BackupLog>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<BackupLog> result = backupLogRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable);
        return ResponseEntity.ok(result);
    }

    // ── GET /{id}/download-url ────────────────────────────────────────────────

    /**
     * Genera una URL firmada de tiempo limitado para descargar el backup indicado.
     *
     * <p>
     * Solo funciona si el backup tiene estado {@code COMPLETED} y tiene
     * una ruta de objeto válida en Supabase Storage.
     * </p>
     *
     * @param id ID del registro de backup en {@code backup_logs}
     * @return JSON con {@code signedUrl} (válido por 1 hora)
     * @throws ResponseStatusException 404 si no existe, 409 si no está COMPLETED
     */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<Map<String, String>> getDownloadUrl(@PathVariable Long id) {
        BackupLog backup = backupLogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Backup con id=" + id + " no encontrado."));

        if (backup.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Este backup ha sido eliminado y ya no está disponible.");
        }

        if (backup.getStatus() != BackupStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El backup aún no está disponible (estado: " + backup.getStatus() + ").");
        }

        if (backup.getFilePath() == null || backup.getFilePath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El backup no tiene ruta de archivo en Supabase Storage.");
        }

        String signedUrl = supabaseStorage.generateSignedUrl(backup.getFilePath(), SIGNED_URL_EXPIRY_SECONDS); // Los
                                                                                                               // 3600
        return ResponseEntity.ok(Map.of(
                "signedUrl", signedUrl,
                "filename", backup.getFilename(),
                "expiresIn", SIGNED_URL_EXPIRY_SECONDS + "s"));
    }
}
