package com.security.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validador personalizado para nombres que solo permite:
 * - Letras (a-z, A-Z)
 * - Acentos (áéíóúñüÁÉÍÓÚÑÜ)
 * - Espacios
 * - Guiones (-)
 * - Apóstrofes (')
 * 
 * Rechaza cualquier carácter de código como (, ), <, >, ;, {, }, =, números,
 * etc.
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidNameValidator.class)
public @interface ValidName {
    String message() default "El nombre contiene caracteres inválidos. Solo se permiten letras, acentos, espacios, guiones y apóstrofes.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
