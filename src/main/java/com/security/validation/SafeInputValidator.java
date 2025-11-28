package com.security.validation;

import com.security.util.SecurityUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Validador personalizado para entradas seguras
 */
@Component
public class SafeInputValidator implements ConstraintValidator<SafeInput, String> {
    
    @Autowired
    private SecurityUtils securityUtils;
    
    private SafeInput.SanitizationType sanitizationType;
    
    @Override
    public void initialize(SafeInput constraintAnnotation) {
        this.sanitizationType = constraintAnnotation.type();
    }
    
    @Override
    public boolean isValid(String input, ConstraintValidatorContext context) {
        if (input == null) {
            return true; // null values are handled by @NotNull if required
        }
        
        try {
            // Verificar si la entrada es segura antes de sanitizar
            if (!securityUtils.isSafeString(input)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    "Entrada contiene contenido potencialmente malicioso"
                ).addConstraintViolation();
                return false;
            }
            
            // Aplicar sanitización según el tipo
            String sanitized = applySanitization(input);
            
            // Verificar que la sanitización fue exitosa
            if (sanitized == null || !sanitized.equals(input.trim())) {
                // Si el contenido cambió significativamente después de la sanitización,
                // puede indicar contenido malicioso
                return true; // Permitir pero el contenido será sanitizado
            }
            
            return true;
        } catch (SecurityException e) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Entrada rechazada por seguridad: " + e.getMessage()
            ).addConstraintViolation();
            return false;
        } catch (Exception e) {
            // En caso de error en la validación, rechazar por seguridad
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Error validando entrada: " + e.getMessage()
            ).addConstraintViolation();
            return false;
        }
    }
    
    private String applySanitization(String input) {
        return switch (sanitizationType) {
            case TEXT -> securityUtils.sanitizeText(input);
            case HTML -> securityUtils.sanitizeHtml(input);
            case EMAIL -> securityUtils.sanitizeEmail(input);
            case PHONE -> securityUtils.sanitizePhone(input);
            case GENERAL -> securityUtils.sanitizeUserInput(input);
        };
    }
}