package com.security.dto.admin;

/**
 * Bloqueo activo entre dos sesiones de PostgreSQL.
 *
 * @param blockedPid           PID de la sesión bloqueada
 * @param blockedUser          Usuario de la sesión bloqueada
 * @param blockedApp           Nombre de aplicación de la sesión bloqueada
 * @param blockingPid          PID de la sesión bloqueadora
 * @param blockingUser         Usuario de la sesión bloqueadora
 * @param waitSeconds          Segundos que lleva esperando la sesión bloqueada
 * @param blockedQueryPreview  Primeros 300 caracteres de la query bloqueada
 * @param blockingQueryPreview Primeros 300 caracteres de la query bloqueadora
 */
public record ActiveLockDto(
        int blockedPid,
        String blockedUser,
        String blockedApp,
        int blockingPid,
        String blockingUser,
        int waitSeconds,
        String blockedQueryPreview,
        String blockingQueryPreview) {
}
