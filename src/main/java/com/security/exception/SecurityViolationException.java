package com.security.exception;

/**
 * Excepción lanzada cuando se intenta realizar una operación que viola
 * las reglas de seguridad del sistema (ej: admin intentando desactivarse a sí
 * mismo)
 */
public class SecurityViolationException extends RuntimeException {

    public SecurityViolationException(String message) {
        super(message);
    }

    public SecurityViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
