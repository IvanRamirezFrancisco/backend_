package com.security.validation;

import com.security.util.PasswordValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validador personalizado para contraseñas seguras.
 * Rechaza patrones simples, secuencias y contraseñas comunes.
 */
@Component
public class SecurePasswordValidator implements ConstraintValidator<SecurePassword, String> {

    @Autowired
    private PasswordValidator passwordValidator;

    // Patrones simples que deben ser rechazados
    private static final Set<String> FORBIDDEN_PATTERNS = Set.of(
            "123456", "1234567", "12345678", "123456789", "1234567890",
            "password", "password123", "admin123", "qwerty", "qwerty123",
            "abc123", "admin", "root", "user", "guest", "demo",
            "asdf", "zxcv", "qwertyuiop", "asdfghjkl", "zxcvbnm",
            "111111", "000000", "123123", "321321", "654321",
            "aaaaaa", "bbbbbb", "cccccc", "abcdef", "fedcba");

    // Patrones de secuencias a rechazar
    private static final Pattern[] SEQUENCE_PATTERNS = {
            Pattern.compile("(\\w)\\1{2,}"), // 3+ caracteres repetidos
            Pattern.compile("(012|123|234|345|456|567|678|789|890)"), // Secuencias numéricas
            Pattern.compile(
                    "(abc|bcd|cde|def|efg|fgh|ghi|hij|ijk|jkl|klm|lmn|mno|nop|opq|pqr|qrs|rst|stu|tuv|uvw|vwx|wxy|xyz)"), // Secuencias
                                                                                                                          // alfabéticas
            Pattern.compile("(qwe|wer|ert|rty|tyu|yui|uio|iop)"), // Secuencias de teclado
            Pattern.compile("(asd|sdf|dfg|fgh|ghj|hjk|jkl)"), // Secuencias de teclado
            Pattern.compile("(zxc|xcv|cvb|vbn|bnm)") // Secuencias de teclado
    };

    @Override
    public void initialize(SecurePassword constraintAnnotation) {
        // Inicialización si es necesaria
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        String passwordLower = password.toLowerCase();

        // Verificar patrones prohibidos
        for (String forbidden : FORBIDDEN_PATTERNS) {
            if (passwordLower.contains(forbidden)) {
                updateErrorMessage(context, "La contraseña contiene un patrón simple prohibido: '" + forbidden + "'");
                return false;
            }
        }

        // Verificar secuencias
        for (Pattern pattern : SEQUENCE_PATTERNS) {
            if (pattern.matcher(passwordLower).find()) {
                updateErrorMessage(context,
                        "La contraseña contiene secuencias simples. Evite patrones como 123, abc o qwerty");
                return false;
            }
        }

        // Verificar contraseña muy corta
        if (password.length() < 8) {
            updateErrorMessage(context, "La contraseña debe tener al menos 8 caracteres");
            return false;
        }

        // Verificar que tenga variedad de caracteres
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\|,.<>\\/?].*");

        int complexity = 0;
        if (hasLower)
            complexity++;
        if (hasUpper)
            complexity++;
        if (hasDigit)
            complexity++;
        if (hasSpecial)
            complexity++;

        if (complexity < 3) {
            updateErrorMessage(context,
                    "La contraseña debe contener al menos 3 tipos de caracteres: minúsculas, mayúsculas, números y símbolos");
            return false;
        }

        // Usar el validador original para verificaciones adicionales
        try {
            PasswordValidator.ValidationResult result = passwordValidator.validatePassword(password);

            if (!result.isValid()) {
                updateErrorMessage(context, "Contraseña inválida: " + result.getMessagesAsString());
                return false;
            }

            return true;
        } catch (Exception e) {
            updateErrorMessage(context, "Error validando contraseña: " + e.getMessage());
            return false;
        }
    }

    private void updateErrorMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}