package com.security.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validación para emails con dominios oficiales únicamente.
 * 
 * PERMITIDO:
 * - Dominios oficiales: gmail.com, hotmail.com, outlook.com, yahoo.com, etc.
 * - Formato estándar: usuario@dominio.com
 * 
 * RECHAZADO:
 * - Dominios falsos: ivan@ivan.com, test@test.com
 * - Dominios temporales: 10minutemail, guerrillamail, etc.
 * - Dominios de desarrollo: localhost, example.com
 */
@Documented
@Constraint(validatedBy = ValidEmailDomainValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmailDomain {

    String message() default "El dominio del email no es válido. Use un email de un proveedor oficial (gmail.com, hotmail.com, outlook.com, yahoo.com)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}