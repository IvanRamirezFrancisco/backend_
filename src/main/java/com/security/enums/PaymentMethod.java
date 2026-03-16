package com.security.enums;

/**
 * Métodos de pago disponibles
 */
public enum PaymentMethod {
    CREDIT_CARD("Tarjeta de Crédito"),
    DEBIT_CARD("Tarjeta de Débito"),
    PAYPAL("PayPal"),
    BANK_TRANSFER("Transferencia Bancaria"),
    CASH_ON_DELIVERY("Contra Entrega"),
    MERCADO_PAGO("Mercado Pago"),
    STRIPE("Stripe");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
