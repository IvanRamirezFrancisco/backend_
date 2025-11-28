package com.security.validation;

import com.security.util.PasswordValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Validador personalizado para contraseñas seguras
 */
@Component
public class SecurePasswordValidator implements ConstraintValidator<SecurePassword, String> {

    @Autowired
    private PasswordValidator passwordValidator;

    @Override
    public void initialize(SecurePassword constraintAnnotation) {
        // Inicialización si es necesaria
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        try {
            PasswordValidator.ValidationResult result = passwordValidator.validatePassword(password);

            if (!result.isValid()) {
                // Personalizar el mensaje de error con los detalles específicos
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        "Contraseña inválida: " + result.getMessagesAsString()).addConstraintViolation();
                return false;
            }

            return true;
        } catch (Exception e) {
            // En caso de error en la validación, rechazar por seguridad
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Error validando contraseña: " + e.getMessage()).addConstraintViolation();
            return false;
        }
    }
}