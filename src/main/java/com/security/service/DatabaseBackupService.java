package com.security.service;

import com.security.config.DatabaseBackupProperties;
import com.security.entity.BackupLog;
import com.security.enums.BackupStatus;
import com.security.repository.BackupLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Servicio asíncrono de respaldos de base de datos — arquitectura Supabase
 * Storage.
 *
 * <h3>Flujo de 9 pasos</h3>
 * <ol>
 * <li>Crear directorio temporal si no existe.</li>
 * <li>Generar nombre de archivo con marca de tiempo.</li>
 * <li>Insertar {@link BackupLog} con estado {@code PENDING}.</li>
 * <li>Ejecutar {@code pg_dump --format=custom} → archivo temporal.</li>
 * <li>Verificar integridad con {@code pg_restore --list}.</li>
 * <li>Subir el archivo a Supabase Storage (bucket privado).</li>
 * <li>Eliminar el archivo temporal local.</li>
 * <li>Actualizar el registro a {@code COMPLETED} con metadatos.</li>
 * <li>Capturar cualquier excepción no controlada → {@code FAILED}.</li>
 * </ol>
 */
@Service
public class DatabaseBackupService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupService.class);
    private static final int TIMEOUT_SECONDS = 300;
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    private final DatabaseBackupProperties backupProperties;
    private final BackupLogRepository backupLogRepository;
    private final SupabaseStorageService supabaseStorage;

    public DatabaseBackupService(DatabaseBackupProperties backupProperties,
            BackupLogRepository backupLogRepository,
            SupabaseStorageService supabaseStorage) {
        this.backupProperties = backupProperties;
        this.backupLogRepository = backupLogRepository;
        this.supabaseStorage = supabaseStorage;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Punto de entrada público — anotado con {@code @Async} para que Spring AOP
     * lo intercepte correctamente y lo ejecute en el pool
     * {@code backupTaskExecutor}.
     * El controller recibe el control de vuelta de inmediato (202 Accepted).
     *
     * <p>
     * <strong>Importante:</strong> {@code @Async} solo funciona en llamadas
     * externas al bean (a través del proxy de Spring). Nunca llamar a este método
     * desde otro método de la misma clase.
     * </p>
     */
    @Async("backupTaskExecutor")
    public void triggerManualBackup(String triggeredBy) {

        // ── PASO 1: parsear coordenadas de la BD ─────────────────────────────
        DbCoordinates db;
        try {
            db = parseJdbcUrl(datasourceUrl);
        } catch (BackupException e) {
            log.error("❌ No se pudo parsear la URL JDBC: {}", e.getMessage());
            return;
        }

        // ── PASO 2: nombre de archivo y ruta temporal ─────────────────────────
        String timestamp = LocalDateTime.now().format(FILE_TS);
        String filename = String.format("backup_%s_%s.dump", db.dbName(), timestamp);
        Path tempDir = Paths.get(backupProperties.getTempDir());
        Path tempFile = tempDir.resolve(filename);

        // ── PASO 3: insertar BackupLog PENDING ────────────────────────────────
        BackupLog backupLog = new BackupLog(filename, null, triggeredBy);
        backupLogRepository.save(backupLog);

        long startMs = System.currentTimeMillis();

        try {
            // ── PASO 1b: crear directorio temporal ───────────────────────────
            Files.createDirectories(tempDir);

            // ── PASO 4: ejecutar ──────────────────────────────────────
            List<String> dumpCmd = buildDumpCommand(db, tempFile);
            runProcess(dumpCmd, "pg_dump");

            // ── PASO 5: verificar integridad — comprobar que el archivo existe
            // y tiene tamaño razonable (> 1 KB).
            // No se usa pg_restore --list porque en Windows el proceso
            // se puede colgar esperando stdin al no necesitar conexión.
            if (!Files.exists(tempFile) || Files.size(tempFile) < 1024) {
                throw new BackupException(
                        "El archivo generado por pg_dump no existe o está vacío: " + tempFile.getFileName());
            }
            log.debug("✔ Archivo verificado: {} bytes", Files.size(tempFile));

            // Capturar tamaño antes de borrar
            long fileSize = Files.exists(tempFile) ? Files.size(tempFile) : 0L;

            // ── PASO 6: subir a Supabase Storage ─────────────────────────────
            String objectPath;
            try {
                objectPath = supabaseStorage.uploadBackup(tempFile, filename);
            } catch (RuntimeException ex) {
                fail(backupLog, startMs, "Error al subir a Supabase: " + ex.getMessage());
                deleteQuietly(tempFile);
                return;
            }

            // ── PASO 7: eliminar archivo temporal ────────────────────────────
            deleteQuietly(tempFile);
            log.debug("🗑️  Archivo temporal eliminado: {}", tempFile.getFileName());

            // ── PASO 8: actualizar registro a COMPLETED ───────────────────────
            long elapsed = System.currentTimeMillis() - startMs;
            backupLog.setStatus(BackupStatus.COMPLETED);
            backupLog.setFilePath(objectPath); // ruta del objeto en Supabase
            backupLog.setFileSizeBytes(fileSize);
            backupLog.setExecutionTimeMs(elapsed);
            backupLogRepository.save(backupLog);

            log.info("✅ Backup completado y subido: {} ({} bytes, {}ms)", filename, fileSize, elapsed);

        } catch (BackupException | IOException e) {
            // ── PASO 9: captura de errores no controlados ─────────────────────
            fail(backupLog, startMs, e.getMessage());
            deleteQuietly(tempFile);
        } catch (Exception e) {
            fail(backupLog, startMs, "Error inesperado: " + e.getMessage());
            deleteQuietly(tempFile);
            log.error("❌ Error inesperado en backup", e);
        }
    }

    /**
     * Retorna los últimos 50 registros de backup no eliminados, más recientes
     * primero.
     */
    public List<BackupLog> getHistory() {
        return backupLogRepository.findTop50ByIsDeletedFalseOrderByCreatedAtDesc();
    }

    // ── Utilidades internas ───────────────────────────────────────────────────

    private void fail(BackupLog bl, long startMs, String message) {
        String msg = (message != null && message.length() > 2000) ? message.substring(0, 2000) : message;
        bl.setStatus(BackupStatus.FAILED);
        bl.setErrorMessage(msg);
        bl.setExecutionTimeMs(System.currentTimeMillis() - startMs);
        backupLogRepository.save(bl);
        log.error("❌ Backup fallido: {}", msg);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    // ── Construcción de comandos ──────────────────────────────────────────────

    private List<String> buildDumpCommand(DbCoordinates db, Path outputFile) throws BackupException {
        String pgDumpPath = backupProperties.getPgDumpPath();
        File pgDumpExec = new File(pgDumpPath);
        if (pgDumpExec.isAbsolute() && !pgDumpExec.exists()) {
            throw new BackupException(
                    "pg_dump no encontrado en: " + pgDumpPath +
                            ". Verifica 'app.backup.pg-dump-path' en application-local.yml");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(pgDumpPath); // la ruta que se tiene configurada en application-local.yml, ej. "C:/Program
                             // Files/PostgreSQL/18/bin/pg_dump.exe" en Windows o "pg_dump" si está en el
                             // PATH del sistema en Linux/Railway
        cmd.add("--host=" + db.host());
        cmd.add("--port=" + db.port());
        cmd.add("--username=" + datasourceUsername);
        cmd.add("--dbname=" + db.dbName());
        cmd.add("--format=custom"); // formato comprimido específico de PostgreSQL, format custom
        cmd.add("--compress=6");
        cmd.add("--encoding=UTF8");
        cmd.add("--no-password");
        cmd.add("--file=" + outputFile.toAbsolutePath());
        return cmd;
    }

    /**
     * Ejecuta un proceso externo y lanza {@link BackupException} si falla.
     */
    private void runProcess(List<String> command, String label)
            throws BackupException, IOException {

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("PGPASSWORD", datasourcePassword);
        pb.redirectErrorStream(false);
        // Redirigir stdin al NUL del sistema para que el proceso hijo no quede
        // esperando entrada por consola (comportamiento común en Windows con
        // pg_dump/pg_restore).
        pb.redirectInput(ProcessBuilder.Redirect.from(new File(
                System.getProperty("os.name", "").toLowerCase().contains("win")
                        ? "NUL"
                        : "/dev/null")));

        Process process = null;
        try {
            process = pb.start();
            final Process fp = process;
            StringBuilder stderr = new StringBuilder();
            Thread stderrReader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(fp.getErrorStream()))) {
                    String line;
                    while ((line = r.readLine()) != null)
                        stderr.append(line).append("\n");
                } catch (IOException ignored) {
                }
            });
            stderrReader.setDaemon(true);
            stderrReader.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BackupException(label + " superó el tiempo límite de " + TIMEOUT_SECONDS + "s.");
            }
            stderrReader.join(2000);

            int exit = process.exitValue();
            if (exit != 0) {
                String errMsg = stderr.toString().trim();
                log.error("❌ {} falló (exit {}): {}", label, exit, errMsg);
                throw new BackupException(label + " terminó con error (código " + exit + "): " + errMsg);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null)
                process.destroyForcibly();
            throw new BackupException("El proceso fue interrumpido: " + label, e);
        }
    }

    // ── URL JDBC parsing ──────────────────────────────────────────────────────

    private DbCoordinates parseJdbcUrl(String jdbcUrl) throws BackupException {
        try {
            String raw = jdbcUrl.replace("jdbc:", "");
            int qIdx = raw.indexOf('?');
            String clean = (qIdx >= 0) ? raw.substring(0, qIdx) : raw;
            URI uri = new URI(clean);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String db = uri.getPath().replaceFirst("^/", "");
            if (host == null || host.isBlank() || db == null || db.isBlank()) {
                throw new BackupException("No se pudo extraer host o BD de la URL JDBC: " + jdbcUrl);
            }
            return new DbCoordinates(host, port, db);
        } catch (URISyntaxException e) {
            throw new BackupException("URL JDBC con formato inválido: " + jdbcUrl, e);
        }
    }

    // ── Tipos internos ────────────────────────────────────────────────────────

    private record DbCoordinates(String host, int port, String dbName) {
    }

    public static class BackupException extends Exception {
        public BackupException(String message) {
            super(message);
        }

        public BackupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}