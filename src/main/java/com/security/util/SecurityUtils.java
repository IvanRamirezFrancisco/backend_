package com.security.util;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Utilidades de seguridad para sanitización de datos y validaciones
 */
@Component
public class SecurityUtils {

    // Política de sanitización para HTML - permite solo texto básico
    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.IMAGES);

    // Patrones para detección de ataques
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)('|(\\-\\-)|(;)|(\\||\\|)|(\\*|\\*))" +
                    "|(((select|union|delete|insert|update|create|drop|alter|exec|execute)\\s+))" +
                    "|((script|javascript|vbscript|onload|onerror|onclick))",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)<script[^>]*>.*?</script>|javascript:|vbscript:|onload=|onerror=|onclick=|onmouseover=",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Sanitiza HTML permitiendo solo elementos seguros
     */
    public String sanitizeHtml(String input) {
        if (input == null)
            return null;
        return POLICY.sanitize(input.trim());
    }

    /**
     * Sanitización estricta - solo texto sin HTML
     */
    public String sanitizeText(String input) {
        if (input == null)
            return null;

        String sanitized = input.trim();
        // Elimina todos los tags HTML
        sanitized = sanitized.replaceAll("<[^>]*>", "");
        // Elimina caracteres peligrosos
        sanitized = sanitized.replaceAll("[<>\"'&]", "");

        return sanitized;
    }

    /**
     * Sanitiza entradas para prevenir inyecciones SQL
     */
    public String sanitizeSql(String input) {
        if (input == null)
            return null;

        String sanitized = input.trim();

        // Detectar patrones de inyección SQL
        if (SQL_INJECTION_PATTERN.matcher(sanitized).find()) {
            throw new SecurityException("Entrada potencialmente maliciosa detectada");
        }

        // Escapar caracteres especiales
        return sanitized.replaceAll("'", "''")
                .replaceAll("\"", "\\\"")
                .replaceAll("\\\\", "\\\\\\\\");
    }

    /**
     * Validar y limpiar entrada para prevenir XSS
     */
    public String sanitizeXss(String input) {
        if (input == null)
            return null;

        String sanitized = input.trim();

        // Detectar patrones XSS
        if (XSS_PATTERN.matcher(sanitized).find()) {
            throw new SecurityException("Contenido XSS detectado");
        }

        return sanitizeHtml(sanitized);
    }

    /**
     * Sanitización general para inputs de usuario
     */
    public String sanitizeUserInput(String input) {
        if (input == null)
            return null;

        // Aplicar múltiples capas de sanitización
        String sanitized = input.trim();
        sanitized = sanitizeXss(sanitized);
        sanitized = sanitizeText(sanitized);

        // Limitar longitud
        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 1000);
        }

        return sanitized;
    }

    /**
     * Sanitizar email
     */
    public String sanitizeEmail(String email) {
        if (email == null)
            return null;

        String sanitized = email.trim().toLowerCase();

        // Validación básica de formato email
        Pattern emailPattern = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
        if (!emailPattern.matcher(sanitized).matches()) {
            throw new IllegalArgumentException("Formato de email inválido");
        }

        return sanitized;
    }

    /**
     * Sanitizar número de teléfono
     */
    public String sanitizePhone(String phone) {
        if (phone == null)
            return null;

        // Conservar solo números, + y espacios
        String sanitized = phone.replaceAll("[^0-9+\\s-]", "").trim();

        if (sanitized.length() < 7 || sanitized.length() > 20) {
            throw new IllegalArgumentException("Longitud de teléfono inválida");
        }

        return sanitized;
    }

    /**
     * Validar si una cadena es segura
     */
    public boolean isSafeString(String input) {
        if (input == null)
            return true;

        return !SQL_INJECTION_PATTERN.matcher(input).find() &&
                !XSS_PATTERN.matcher(input).find();
    }
}