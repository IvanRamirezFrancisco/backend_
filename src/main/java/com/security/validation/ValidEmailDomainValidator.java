package com.security.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Validador para dominios de email oficiales.
 * Solo permite emails de proveedores reconocidos para evitar registros con
 * emails falsos.
 */
public class ValidEmailDomainValidator implements ConstraintValidator<ValidEmailDomain, String> {

    // Lista de dominios oficiales permitidos
    private static final Set<String> VALID_DOMAINS = new HashSet<>();

    static {
        // Proveedores principales
        VALID_DOMAINS.add("gmail.com");
        VALID_DOMAINS.add("googlemail.com");
        VALID_DOMAINS.add("hotmail.com");
        VALID_DOMAINS.add("outlook.com");
        VALID_DOMAINS.add("live.com");
        VALID_DOMAINS.add("msn.com");
        VALID_DOMAINS.add("yahoo.com");
        VALID_DOMAINS.add("yahoo.es");
        VALID_DOMAINS.add("yahoo.com.mx");
        VALID_DOMAINS.add("aol.com");
        VALID_DOMAINS.add("protonmail.com");
        VALID_DOMAINS.add("icloud.com");
        VALID_DOMAINS.add("me.com");
        VALID_DOMAINS.add("mac.com");

        // Proveedores regionales importantes
        VALID_DOMAINS.add("terra.com");
        VALID_DOMAINS.add("terra.com.mx");
        VALID_DOMAINS.add("terra.es");
        VALID_DOMAINS.add("movistar.es");
        VALID_DOMAINS.add("orange.es");
        VALID_DOMAINS.add("vodafone.es");

        // Proveedores educativos comunes
        VALID_DOMAINS.add("edu.mx");
        VALID_DOMAINS.add("edu.es");
        VALID_DOMAINS.add("edu.com");

        // Universidades e instituciones educativas mexicanas
        VALID_DOMAINS.add("unam.mx");
        VALID_DOMAINS.add("ipn.mx");
        VALID_DOMAINS.add("itesm.mx");
        VALID_DOMAINS.add("tec.mx");
        VALID_DOMAINS.add("tecnm.mx");
        VALID_DOMAINS.add("uthh.edu.mx");
        VALID_DOMAINS.add("utec.edu.mx");
        VALID_DOMAINS.add("uaem.mx");
        VALID_DOMAINS.add("uanl.mx");
        VALID_DOMAINS.add("udg.mx");
        VALID_DOMAINS.add("uat.edu.mx");
        VALID_DOMAINS.add("uach.mx");
        VALID_DOMAINS.add("uaslp.mx");
        VALID_DOMAINS.add("uas.edu.mx");
        VALID_DOMAINS.add("uabcs.mx");
        VALID_DOMAINS.add("ujat.mx");
        VALID_DOMAINS.add("uady.mx");

        // Universidades internacionales reconocidas
        VALID_DOMAINS.add("harvard.edu");
        VALID_DOMAINS.add("mit.edu");
        VALID_DOMAINS.add("stanford.edu");
        VALID_DOMAINS.add("berkeley.edu");
        VALID_DOMAINS.add("ucla.edu");
        VALID_DOMAINS.add("oxford.ac.uk");
        VALID_DOMAINS.add("cambridge.ac.uk");

        // Proveedores empresariales comunes
        VALID_DOMAINS.add("empresas.com");
        VALID_DOMAINS.add("corporativo.com");
    }

    // Dominios prohibidos (temporales, falsos, de prueba)
    private static final Set<String> FORBIDDEN_DOMAINS = new HashSet<>();

    static {
        // Dominios falsos comunes
        FORBIDDEN_DOMAINS.add("test.com");
        FORBIDDEN_DOMAINS.add("example.com");
        FORBIDDEN_DOMAINS.add("fake.com");
        FORBIDDEN_DOMAINS.add("falso.com");
        FORBIDDEN_DOMAINS.add("prueba.com");
        FORBIDDEN_DOMAINS.add("localhost");
        FORBIDDEN_DOMAINS.add("127.0.0.1");

        // Dominios temporales conocidos
        FORBIDDEN_DOMAINS.add("10minutemail.com");
        FORBIDDEN_DOMAINS.add("guerrillamail.com");
        FORBIDDEN_DOMAINS.add("mailinator.com");
        FORBIDDEN_DOMAINS.add("tempmail.org");
        FORBIDDEN_DOMAINS.add("throwaway.email");
        FORBIDDEN_DOMAINS.add("temp-mail.org");
        FORBIDDEN_DOMAINS.add("dispostable.com");

        // Patrones de dominios falsos (un solo nombre)
        FORBIDDEN_DOMAINS.add("ivan.com");
        FORBIDDEN_DOMAINS.add("juan.com");
        FORBIDDEN_DOMAINS.add("maria.com");
        FORBIDDEN_DOMAINS.add("admin.com");
        FORBIDDEN_DOMAINS.add("user.com");
    }

    // Patrón para validar formato básico del email
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    @Override
    public void initialize(ValidEmailDomain constraintAnnotation) {
        // No necesita inicialización específica
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null y vacío se manejan por @NotBlank y @Email
        if (value == null || value.trim().isEmpty()) {
            return true;
        }

        String email = value.trim().toLowerCase();

        // Verificar formato básico
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            updateErrorMessage(context, "Formato de email inválido");
            return false;
        }

        // Extraer el dominio
        String domain = extractDomain(email);
        if (domain == null) {
            updateErrorMessage(context, "No se pudo extraer el dominio del email");
            return false;
        }

        // Verificar si está en la lista de dominios prohibidos
        if (FORBIDDEN_DOMAINS.contains(domain)) {
            updateErrorMessage(context,
                    "El dominio '" + domain + "' no está permitido. Use un email de un proveedor oficial");
            return false;
        }

        // Verificar patrones de dominios específicamente sospechosos
        if (isSuspiciousDomain(domain)) {
            updateErrorMessage(context,
                    "El dominio '" + domain + "' no está permitido. Use un email de un proveedor oficial");
            return false;
        }

        // Verificar si está en la lista de dominios válidos conocidos
        if (VALID_DOMAINS.contains(domain)) {
            return true; // Dominios conocidos son siempre válidos
        }

        // Verificar si es un dominio educativo válido
        if (isEducationalDomain(domain)) {
            return true; // Los dominios educativos son válidos automáticamente
        }

        // Para dominios no conocidos, verificar si tienen un patrón razonable
        if (isLikelyRealDomain(domain)) {
            return true; // Permitir dominios que parecen legítimos aunque no estén en la lista
        }

        // Solo rechazar si claramente es problemático
        updateErrorMessage(context,
                "Por favor use un email de un proveedor reconocido (gmail.com, hotmail.com, outlook.com, yahoo.com, etc.)");
        return false;
    }

    private String extractDomain(String email) {
        int atIndex = email.lastIndexOf('@');
        if (atIndex == -1 || atIndex == email.length() - 1) {
            return null;
        }
        return email.substring(atIndex + 1);
    }

    private boolean isSuspiciousDomain(String domain) {
        // Primero, verificar si es un dominio conocido y legítimo
        if (VALID_DOMAINS.contains(domain)) {
            return false; // Los dominios válidos nunca son sospechosos
        }

        // Verificar contra la lista de dominios prohibidos
        for (String forbiddenDomain : FORBIDDEN_DOMAINS) {
            if (domain.equals(forbiddenDomain)) {
                return true;
            }
        }

        // Verificar patrones específicamente problemáticos
        if (domain.matches(
                "^(ivan|juan|maria|jose|ana|carlos|luis|pedro|admin|user|test|demo|example|prueba|ejemplo)\\.(com|org|net)$")) {
            return true;
        }

        // Dominios que claramente parecen de prueba
        if (domain.matches("^(test|demo|example|fake|temp|temporary|throwaway)\\d*\\.(com|org|net)$")) {
            return true;
        }

        // Dominios muy cortos sospechosos (pero excluir los legítimos conocidos)
        if (domain.length() < 5 && !Set.of("me.com", "qq.com", "ya.ru", "t.co").contains(domain)) {
            return true;
        }

        return false;
    }

    private boolean isEducationalDomain(String domain) {
        // Verificar patrones educativos comunes

        // Dominios que terminan en .edu (principalmente USA)
        if (domain.endsWith(".edu")) {
            return true;
        }

        // Dominios educativos mexicanos
        if (domain.endsWith(".edu.mx")) {
            return true;
        }

        // Dominios educativos de otros países
        if (domain.endsWith(".edu.es") || domain.endsWith(".edu.ar") ||
                domain.endsWith(".edu.co") || domain.endsWith(".edu.pe") ||
                domain.endsWith(".ac.uk") || domain.endsWith(".edu.au")) {
            return true;
        }

        // Universidades tecnológicas mexicanas (patrón común)
        if (domain.matches("^ut[a-z]+\\.edu\\.mx$")) {
            return true; // uthh.edu.mx, utec.edu.mx, etc.
        }

        // Universidades mexicanas con patrón común
        if (domain.matches("^u[a-z]+\\.(mx|edu\\.mx)$")) {
            return true; // unam.mx, uaem.mx, etc.
        }

        // Institutos tecnológicos mexicanos
        if (domain.matches("^it[a-z]+\\.edu\\.mx$")) {
            return true; // itsm.edu.mx, iteso.edu.mx, etc.
        }

        return false;
    }

    private boolean isLikelyRealDomain(String domain) {
        // Dominios que tienen patrones de empresas reales
        String[] parts = domain.split("\\.");

        // Debe tener al menos 2 partes
        if (parts.length < 2) {
            return false;
        }

        String name = parts[0];
        String tld = parts[parts.length - 1];

        // Verificar TLD válido
        if (!isValidTLD(tld)) {
            return false;
        }

        // El nombre debe tener al menos 2 caracteres
        if (name.length() < 2) {
            return false;
        }

        // Rechazar solo si es claramente un nombre común problemático
        if (isCommonPersonName(name) && name.length() < 6) {
            return false;
        }

        // Rechazar patrones obviamente de prueba
        if (name.matches("^(test|demo|fake|temp)\\d*$")) {
            return false;
        }

        // Todo lo demás se considera legítimo
        return true;
    }

    private boolean isValidTLD(String tld) {
        Set<String> validTLDs = Set.of(
                "com", "org", "net", "edu", "gov", "mil", "int",
                "es", "mx", "ar", "co", "cl", "pe", "ve", "br",
                "fr", "de", "it", "uk", "ru", "jp", "cn", "in");
        return validTLDs.contains(tld);
    }

    private boolean isCommonPersonName(String name) {
        Set<String> commonNames = Set.of(
                "ivan", "juan", "maria", "jose", "ana", "carlos", "luis",
                "pedro", "sofia", "diego", "alejandra", "fernando", "admin",
                "user", "test", "demo", "prueba", "ejemplo");
        return commonNames.contains(name.toLowerCase());
    }

    private void updateErrorMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}