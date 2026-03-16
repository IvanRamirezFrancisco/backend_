package com.security.exception;

/**
 * Excepción cuando un cupón es inválido o no se puede aplicar
 */
public class InvalidCouponException extends RuntimeException {
    private final String couponCode;

    public InvalidCouponException(String message) {
        super(message);
        this.couponCode = null;
    }

    public InvalidCouponException(String couponCode, String reason) {
        super(String.format("Cupón '%s' inválido: %s", couponCode, reason));
        this.couponCode = couponCode;
    }

    public String getCouponCode() {
        return couponCode;
    }
}
