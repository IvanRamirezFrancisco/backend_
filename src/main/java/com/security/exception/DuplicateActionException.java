package com.security.exception;

/**
 * Excepción cuando un usuario intenta duplicar una acción
 */
public class DuplicateActionException extends RuntimeException {
    public DuplicateActionException(String message) {
        super(message);
    }
}
