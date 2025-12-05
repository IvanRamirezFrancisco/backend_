package com.security.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Implementación del validador para nombres seguros.
 * 
 * PERMITIDO:
 * - Letras básicas: a-z, A-Z
 * - Acentos: áéíóúñüÁÉÍÓÚÑÜ y otros acentos latinos
 * - Espacios simples (no múltiples)
 * - Guiones (-)
 * - Apóstrofes (')
 * 
 * RECHAZADO:
 * - Números (0-9)
 * - Símbolos de código: ( ) < > ; { } = ! @ # $ % ^ & *
 * - Scripts maliciosos: <script>, alert, eval, etc.
 * - Caracteres especiales: +, *, ?, [, ], \, |, etc.
 */
public class ValidNameValidator implements ConstraintValidator<ValidName, String> {

    // Patrón que SOLO permite letras, acentos, espacios, guiones y apóstrofes
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile(
            "^[a-zA-ZáéíóúñüÁÉÍÓÚÑÜàèìòùÀÈÌÒÙâêîôûÂÊÎÔÛçÇ\\s'\\-]+$");

    // Patrones de ataques comunes que debemos detectar
    private static final Pattern[] MALICIOUS_PATTERNS = {
            Pattern.compile(".*<.*>.*", Pattern.CASE_INSENSITIVE), // Tags HTML
            Pattern.compile(".*script.*", Pattern.CASE_INSENSITIVE), // JavaScript
            Pattern.compile(".*alert.*", Pattern.CASE_INSENSITIVE), // Alert JS
            Pattern.compile(".*eval.*", Pattern.CASE_INSENSITIVE), // Eval JS
            Pattern.compile(".*onload.*", Pattern.CASE_INSENSITIVE), // Event handlers
            Pattern.compile(".*onclick.*", Pattern.CASE_INSENSITIVE), // Event handlers
            Pattern.compile(".*javascript:.*", Pattern.CASE_INSENSITIVE), // JS protocol
            Pattern.compile(".*drop.*table.*", Pattern.CASE_INSENSITIVE), // SQL injection
            Pattern.compile(".*insert.*into.*", Pattern.CASE_INSENSITIVE), // SQL injection
            Pattern.compile(".*select.*from.*", Pattern.CASE_INSENSITIVE), // SQL injection
            Pattern.compile(".*union.*select.*", Pattern.CASE_INSENSITIVE), // SQL injection
            Pattern.compile(".*\\d.*"), // Cualquier número
            Pattern.compile(".*[(){}\\[\\]=;!@#$%^&*+?|\\\\].*") // Símbolos especiales
    };

    @Override
    public void initialize(ValidName constraintAnnotation) {
        // No necesita inicialización específica
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null y vacío se manejan por @NotBlank
        if (value == null || value.trim().isEmpty()) {
            return true;
        }

        // Sanitizar: eliminar espacios extra y normalizar
        String sanitized = value.trim().replaceAll("\\s+", " ");

        // Verificar longitud razonable
        if (sanitized.length() > 50) {
            updateErrorMessage(context, "El nombre es demasiado largo (máximo 50 caracteres)");
            return false;
        }

        if (sanitized.length() < 2) {
            updateErrorMessage(context, "El nombre es demasiado corto (mínimo 2 caracteres)");
            return false;
        }

        // Verificar patrones maliciosos primero
        for (Pattern pattern : MALICIOUS_PATTERNS) {
            if (pattern.matcher(sanitized).matches()) {
                updateErrorMessage(context, "El nombre contiene caracteres inválidos o código malicioso");
                return false;
            }
        }

        // Verificar que solo contenga caracteres permitidos
        if (!VALID_NAME_PATTERN.matcher(sanitized).matches()) {
            updateErrorMessage(context,
                    "El nombre contiene caracteres inválidos. Solo se permiten letras, acentos, espacios, guiones y apóstrofes");
            return false;
        }

        // Verificar que no tenga solo espacios, guiones o apóstrofes
        if (sanitized.matches("^[\\s'\\-]+$")) {
            updateErrorMessage(context, "El nombre debe contener al menos una letra");
            return false;
        }

        // Verificar que no empiece o termine con espacio, guión o apóstrofe
        if (sanitized.matches("^[\\s'\\-].*") || sanitized.matches(".*[\\s'\\-]$")) {
            updateErrorMessage(context, "El nombre no puede empezar o terminar con espacios, guiones o apóstrofes");
            return false;
        }

        return true;
    }

    private void updateErrorMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}