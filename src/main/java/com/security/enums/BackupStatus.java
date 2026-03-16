package com.security.enums;

/**
 * Estados del ciclo de vida de un respaldo de base de datos.
 *
 * <pre>
 *   PENDING  → el proceso @Async fue disparado, pg_dump todavía no termina
 *   COMPLETED → pg_dump finalizó con exit code 0, el archivo existe en disco
 *   FAILED    → pg_dump falló (exit code ≠ 0), timeout, o excepción de IO
 * </pre>
 */
public enum BackupStatus {
    PENDING,
    COMPLETED,
    FAILED
}
