package com.security.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Anotación para validar entrada sanitizada
 */
@Documented
@Constraint(validatedBy = SafeInputValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeInput {

    String message() default "Entrada contiene caracteres no permitidos o contenido malicioso";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Tipo de sanitización a aplicar
     */
    SanitizationType type() default SanitizationType.TEXT;

    enum SanitizationType {
        TEXT, // Solo texto sin HTML
        HTML, // HTML básico permitido
        EMAIL, // Validación de email
        PHONE, // Validación de teléfono
        GENERAL // Sanitización general
    }
}