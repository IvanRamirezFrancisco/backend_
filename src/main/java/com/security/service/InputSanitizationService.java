package com.security.service;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Servicio de sanitización y validación de entrada para prevenir XSS
 * Implementa las mejores prácticas de OWASP para validación de entrada
 */
@Service
public class InputSanitizationService {

    private static final Logger logger = LoggerFactory.getLogger(InputSanitizationService.class);

    // Política de sanitización OWASP para contenido HTML básico
    private static final PolicyFactory BASIC_HTML_POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.IMAGES);

    // Política estricta para texto plano (remueve todo HTML)
    private static final PolicyFactory TEXT_ONLY_POLICY = Sanitizers.FORMATTING.and(Sanitizers.BLOCKS);

    // Patrones para detección de ataques
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)<[^>]*script[^>]*>.*?</[^>]*script[^>]*>" +
                    "|javascript:" +
                    "|vbscript:" +
                    "|onload\\s*=" +
                    "|onerror\\s*=" +
                    "|onclick\\s*=" +
                    "|onmouseover\\s*=" +
                    "|onfocus\\s*=" +
                    "|onblur\\s*=" +
                    "|onsubmit\\s*=" +
                    "|<[^>]*iframe[^>]*>.*?</[^>]*iframe[^>]*>" +
                    "|<[^>]*object[^>]*>.*?</[^>]*object[^>]*>" +
                    "|<[^>]*embed[^>]*>" +
                    "|<[^>]*link[^>]*>" +
                    "|<[^>]*meta[^>]*>" +
                    "|<[^>]*style[^>]*>.*?</[^>]*style[^>]*>");

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(\\bSELECT\\b.*\\bFROM\\b)" +
                    "|(\\bINSERT\\b.*\\bINTO\\b)" +
                    "|(\\bUPDATE\\b.*\\bSET\\b)" +
                    "|(\\bDELETE\\b.*\\bFROM\\b)" +
                    "|(\\bDROP\\b.*\\bTABLE\\b)" +
                    "|(\\bALTER\\b.*\\bTABLE\\b)" +
                    "|(\\bCREATE\\b.*\\bTABLE\\b)" +
                    "|(\\bTRUNCATE\\b.*\\bTABLE\\b)" +
                    "|(\\bEXEC\\b)" +
                    "|(\\bEXECUTE\\b)" +
                    "|(\\bSP_\\w+)" +
                    "|(\\bXP_\\w+)" +
                    "|('\\s*(OR|AND)\\s*')" +
                    "|('\\s*=\\s*')" +
                    "|(--)" +
                    "|(;\\s*(DROP|DELETE|UPDATE|INSERT))" +
                    "|(\\bUNION\\b.*\\bSELECT\\b)");

    /**
     * Sanitiza texto para prevenir XSS, permitiendo formato básico
     */
    public String sanitizeHtml(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        try {
            // Detectar posible ataque XSS
            if (XSS_PATTERN.matcher(input).find()) {
                logger.warn("Potential XSS attack detected and blocked: {}",
                        input.length() > 100 ? input.substring(0, 100) + "..." : input);
            }

            // Aplicar sanitización
            String sanitized = BASIC_HTML_POLICY.sanitize(input);

            // Log si hubo cambios significativos
            if (!input.equals(sanitized)) {
                logger.debug("Input sanitized - Original length: {}, Sanitized length: {}",
                        input.length(), sanitized.length());
            }

            return sanitized;

        } catch (Exception e) {
            logger.error("Error sanitizing HTML input: {}", e.getMessage());
            // En caso de error, ser conservador y retornar texto plano
            return sanitizeText(input);
        }
    }

    /**
     * Sanitiza texto removiendo todo HTML y caracteres peligrosos
     */
    public String sanitizeText(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        try {
            // Detectar posible ataque XSS
            if (XSS_PATTERN.matcher(input).find()) {
                logger.warn("Potential XSS attack detected in text input and blocked: {}",
                        input.length() > 100 ? input.substring(0, 100) + "..." : input);
            }

            // Remover todo HTML
            String sanitized = input.replaceAll("<[^>]*>", "")
                    .replaceAll("&[^;]*;", "")
                    .replaceAll("javascript:", "")
                    .replaceAll("vbscript:", "")
                    .replaceAll("data:", "");

            // Normalizar espacios en blanco
            sanitized = sanitized.replaceAll("\\s+", " ").trim();

            return sanitized;

        } catch (Exception e) {
            logger.error("Error sanitizing text input: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Valida entrada para prevenir SQL Injection
     */
    public boolean isValidForDatabase(String input) {
        if (input == null || input.trim().isEmpty()) {
            return true;
        }

        try {
            // Detectar patrones de SQL Injection
            if (SQL_INJECTION_PATTERN.matcher(input).find()) {
                logger.warn("Potential SQL injection attempt detected and blocked: {}",
                        input.length() > 100 ? input.substring(0, 100) + "..." : input);
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.error("Error validating input for database: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Sanitiza parámetros de URL
     */
    public String sanitizeUrlParameter(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        try {
            // Sanitizar caracteres peligrosos en URLs
            String sanitized = input.replaceAll("[<>\"']", "")
                    .replaceAll("javascript:", "")
                    .replaceAll("vbscript:", "")
                    .replaceAll("data:", "")
                    .replaceAll("\\s", "%20");

            return sanitized;

        } catch (Exception e) {
            logger.error("Error sanitizing URL parameter: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Valida email de forma segura
     */
    public String sanitizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "";
        }

        try {
            // Sanitizar y validar formato básico de email
            String sanitized = email.toLowerCase().trim()
                    .replaceAll("[<>\"'()]", "")
                    .replaceAll("\\s", "");

            // Verificar que no contenga patrones sospechosos
            if (XSS_PATTERN.matcher(sanitized).find() || SQL_INJECTION_PATTERN.matcher(sanitized).find()) {
                logger.warn("Malicious content detected in email field: {}", email);
                return "";
            }

            // Validación básica de formato de email
            if (!sanitized.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
                logger.debug("Invalid email format: {}", sanitized);
                return "";
            }

            return sanitized;

        } catch (Exception e) {
            logger.error("Error sanitizing email: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Sanitiza nombres (firstName, lastName)
     */
    public String sanitizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }

        try {
            // Permitir solo letras, espacios, hifens y apostrofes
            String sanitized = name.trim()
                    .replaceAll("[^a-zA-ZÀ-ÿ\\s'-]", "")
                    .replaceAll("\\s+", " ");

            // Verificar longitud razonable
            if (sanitized.length() > 50) {
                sanitized = sanitized.substring(0, 50);
            }

            // Verificar patrones sospechosos
            if (XSS_PATTERN.matcher(sanitized).find() || SQL_INJECTION_PATTERN.matcher(sanitized).find()) {
                logger.warn("Malicious content detected in name field: {}", name);
                return "";
            }

            return sanitized;

        } catch (Exception e) {
            logger.error("Error sanitizing name: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Sanitiza números de teléfono
     */
    public String sanitizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return "";
        }

        try {
            // Permitir solo números, espacios, hifens, paréntesis y el signo +
            String sanitized = phone.trim()
                    .replaceAll("[^0-9\\s+()-]", "")
                    .replaceAll("\\s+", " ");

            // Verificar longitud razonable
            if (sanitized.length() > 20) {
                sanitized = sanitized.substring(0, 20);
            }

            return sanitized;

        } catch (Exception e) {
            logger.error("Error sanitizing phone: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Valida entrada de forma general
     */
    public boolean isInputSafe(String input) {
        if (input == null || input.trim().isEmpty()) {
            return true;
        }

        return !XSS_PATTERN.matcher(input).find() &&
                !SQL_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * Escapar caracteres especiales para JSON
     */
    public String escapeJsonString(String input) {
        if (input == null) {
            return null;
        }

        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Validar y sanitizar contenido de formularios completos
     */
    public boolean validateFormData(Object... inputs) {
        try {
            for (Object input : inputs) {
                if (input instanceof String) {
                    String stringInput = (String) input;
                    if (!isInputSafe(stringInput)) {
                        return false;
                    }
                }
            }
            return true;

        } catch (Exception e) {
            logger.error("Error validating form data: {}", e.getMessage());
            return false;
        }
    }
}