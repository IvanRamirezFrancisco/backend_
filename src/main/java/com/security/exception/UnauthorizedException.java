package com.security.exception;

/**
 * Excepción cuando un usuario no tiene permisos para una acción
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
