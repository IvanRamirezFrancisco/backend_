package com.security.exception;

/**
 * Excepción cuando el carrito no se encuentra o está expirado
 */
public class CartNotFoundException extends RuntimeException {
    public CartNotFoundException(String message) {
        super(message);
    }

    public CartNotFoundException(Long cartId) {
        super("Carrito no encontrado con ID: " + cartId);
    }
}
