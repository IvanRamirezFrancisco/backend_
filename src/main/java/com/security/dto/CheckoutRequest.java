package com.security.dto;

import com.security.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckoutRequest {

    @NotBlank(message = "La dirección de envío es obligatoria")
    private String shippingAddress;

    @NotBlank(message = "La dirección de facturación es obligatoria")
    private String billingAddress;

    @NotNull(message = "El método de pago es obligatorio")
    private PaymentMethod paymentMethod;

    private String notes;
}
