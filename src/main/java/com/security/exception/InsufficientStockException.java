package com.security.exception;

/**
 * Excepción cuando no hay suficiente stock de un producto
 */
public class InsufficientStockException extends RuntimeException {
    private final Long productId;
    private final Integer requested;
    private final Integer available;

    public InsufficientStockException(Long productId, Integer requested, Integer available) {
        super(String.format("Stock insuficiente para producto ID %d. Solicitado: %d, Disponible: %d",
                productId, requested, available));
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getRequested() {
        return requested;
    }

    public Integer getAvailable() {
        return available;
    }
}
