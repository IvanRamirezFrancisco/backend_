package com.security.util;

/**
 * Utilidad de sanitización para logging seguro.
 *
 * <p>
 * Previene <strong>Log Injection (CWE-117)</strong>: un atacante podría incluir
 * caracteres {@code \r}, {@code \n} en datos controlados por el usuario
 * (email, filename, token…) para falsificar entradas en los ficheros de log.
 * </p>
 *
 * <p>
 * Uso recomendado en cualquier {@code logger.info/warn/error} que reciba
 * datos provenientes del cliente:
 * 
 * <pre>{@code
 * logger.warn("Login fallido para: {}", LogSanitizer.sanitize(email));
 * }</pre>
 * </p>
 *
 * <p>
 * Esta clase es <strong>final</strong> y sólo contiene métodos estáticos;
 * no debe instanciarse.
 * </p>
 */
public final class LogSanitizer {

    /** Longitud máxima de cualquier valor antes de truncar. */
    private static final int MAX_LENGTH = 200;

    private LogSanitizer() {
        // Utility class — sin instancias
    }

    /**
     * Elimina saltos de línea, tabuladores y caracteres de control que permiten
     * Log Injection, recorta espacios extremos y trunca a {@value #MAX_LENGTH}
     * caracteres.
     *
     * @param value dato controlado por el usuario (puede ser null)
     * @return cadena segura para loggear, nunca null
     */
    public static String sanitize(String value) {
        if (value == null) {
            return "(null)";
        }
        // Eliminar CR, LF y TAB — vectores de Log Injection
        String safe = value.replaceAll("[\\r\\n\\t]", " ").trim();
        // Truncar para evitar log flooding
        if (safe.length() > MAX_LENGTH) {
            safe = safe.substring(0, MAX_LENGTH) + "…(truncado)";
        }
        return safe;
    }

    /**
     * Sanitiza y enmascara un email para logging.
     * Muestra los 2 primeros caracteres del local-part y el dominio completo.
     *
     * <p>
     * Ejemplo: {@code ju***@example.com}
     * </p>
     *
     * @param email dirección de correo (puede ser null)
     * @return email enmascarado y sanitizado, nunca null
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return "(null)";
        }
        String safe = sanitize(email);
        int atIdx = safe.indexOf('@');
        if (atIdx > 0) {
            String local = safe.substring(0, atIdx);
            String domain = safe.substring(atIdx); // incluye '@'
            String masked = local.length() > 2 ? local.substring(0, 2) + "***" : "***";
            return masked + domain;
        }
        // No es un email válido — devolver versión genérica
        return safe.length() > 3 ? safe.substring(0, 3) + "***" : "***";
    }

    /**
     * Sanitiza y enmascara la primera porción de un token / identificador opaco.
     * Muestra sólo los 8 primeros caracteres seguidos de {@code …}.
     *
     * @param token token o UUID (puede ser null)
     * @return prefijo seguro del token, nunca null
     */
    public static String maskToken(String token) {
        if (token == null) {
            return "(null)";
        }
        String safe = sanitize(token);
        return safe.length() > 8 ? safe.substring(0, 8) + "…" : safe;
    }

    /**
     * Sanitiza un nombre de archivo proveniente de
     * {@code MultipartFile#getOriginalFilename()}.
     * Además elimina separadores de ruta para prevenir path traversal en logs.
     *
     * @param filename nombre original del archivo (puede ser null)
     * @return nombre seguro para loggear, nunca null
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "(null)";
        }
        // Eliminar separadores de ruta además de CR/LF/TAB
        String safe = filename.replaceAll("[\\r\\n\\t/\\\\]", "_").trim();
        if (safe.length() > MAX_LENGTH) {
            safe = safe.substring(0, MAX_LENGTH) + "…(truncado)";
        }
        return safe;
    }
}
