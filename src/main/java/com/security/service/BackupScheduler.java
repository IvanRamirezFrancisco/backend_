package com.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Programador de respaldos automáticos.
 * 
 *
 * <p>
 * Ejecuta un backup diario a las 03:00 AM (zona horaria del servidor)
 * usando el cron {@code 0 0 3 * * ?}.
 *
 * <p>
 * Requiere {@code @EnableScheduling} en la clase principal de la aplicación
 * (ya presente en {@code AuthSystemApplication}).
 */
@Component
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final DatabaseBackupService backupService;

    public BackupScheduler(DatabaseBackupService backupService) {
        this.backupService = backupService;
    }

    /**
     * Backup automático diario a las 03:00 AM.
     *
     * <p>
     * El trabajo real se ejecuta en el pool {@code backupTaskExecutor}
     * (ver {@link com.security.config.AsyncConfig}), por lo que este hilo
     * del scheduler se libera inmediatamente.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduleDailyBackup() {
        log.info("⏰ [Scheduler] Iniciando backup diario automático (SYSTEM_CRON)");
        backupService.triggerManualBackup("SYSTEM_CRON");
    }
}
