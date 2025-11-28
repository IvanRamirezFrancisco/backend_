package com.security.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Anotación para validar contraseñas seguras
 */
@Documented
@Constraint(validatedBy = SecurePasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SecurePassword {
    
    String message() default "La contraseña no cumple con los requisitos de seguridad";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}