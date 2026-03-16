package com.security.enums;

/**
 * Estados de pago de una orden
 */
public enum PaymentStatus {
    PENDING("Pendiente"),
    PAID("Pagado"),
    FAILED("Fallido"),
    REFUNDED("Reembolsado"),
    PARTIALLY_REFUNDED("Parcialmente Reembolsado");

    private final String displayName;

    PaymentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
