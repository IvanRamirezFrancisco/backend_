package com.security.util;

import org.passay.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.security.service.SecuritySettingsService;

import java.util.ArrayList;
import java.util.List;

/**
 * Validador de contraseñas utilizando configuraciones dinámicas desde la base
 * de datos
 */
@Component
public class PasswordValidator {

    @Autowired
    private SecuritySettingsService securitySettingsService;

    /**
     * Validar contraseña basada en configuraciones de la BD
     */
    public ValidationResult validatePassword(String password) {
        List<Rule> rules = new ArrayList<>();

        try {
            // Obtener configuraciones desde la BD
            int minLength = securitySettingsService.getIntValue("PASSWORD_MIN_LENGTH", 8);
            boolean requireUppercase = securitySettingsService.getBooleanValue("PASSWORD_REQUIRE_UPPERCASE", true);
            boolean requireLowercase = securitySettingsService.getBooleanValue("PASSWORD_REQUIRE_LOWERCASE", true);
            boolean requireNumbers = securitySettingsService.getBooleanValue("PASSWORD_REQUIRE_NUMBERS", true);
            boolean requireSymbols = securitySettingsService.getBooleanValue("PASSWORD_REQUIRE_SYMBOLS", true);

            // Regla de longitud mínima
            rules.add(new LengthRule(minLength, 128));

            // Regla de espacios en blanco (no permitir)
            rules.add(new WhitespaceRule());

            // Reglas de caracteres
            if (requireUppercase) {
                rules.add(new CharacterRule(EnglishCharacterData.UpperCase, 1));
            }

            if (requireLowercase) {
                rules.add(new CharacterRule(EnglishCharacterData.LowerCase, 1));
            }

            if (requireNumbers) {
                rules.add(new CharacterRule(EnglishCharacterData.Digit, 1));
            }

            if (requireSymbols) {
                rules.add(new CharacterRule(EnglishCharacterData.Special, 1));
            }

            // Reglas adicionales de seguridad
            rules.add(new IllegalSequenceRule(EnglishSequenceData.Alphabetical, 4, false));
            rules.add(new IllegalSequenceRule(EnglishSequenceData.Numerical, 4, false));
            rules.add(new IllegalSequenceRule(EnglishSequenceData.USQwerty, 4, false));

            // Regla de repetición de caracteres
            rules.add(new RepeatCharactersRule(4));

        } catch (Exception e) {
            // Si hay error obteniendo configuraciones, usar valores por defecto
            rules.add(new LengthRule(8, 128));
            rules.add(new WhitespaceRule());
            rules.add(new CharacterRule(EnglishCharacterData.UpperCase, 1));
            rules.add(new CharacterRule(EnglishCharacterData.LowerCase, 1));
            rules.add(new CharacterRule(EnglishCharacterData.Digit, 1));
            rules.add(new CharacterRule(EnglishCharacterData.Special, 1));
        }

        org.passay.PasswordValidator validator = new org.passay.PasswordValidator(rules);
        RuleResult result = validator.validate(new PasswordData(password));

        return new ValidationResult(result.isValid(), validator.getMessages(result));
    }

    /**
     * Validar si la contraseña no ha sido usada anteriormente
     */
    public boolean isPasswordReused(String newPassword, List<String> previousPasswordHashes) {
        for (String previousHash : previousPasswordHashes) {
            // Comparar hashes (esto debería hacerse con el encoder correspondiente)
            if (previousHash.equals(newPassword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generar mensaje de requerimientos de contraseña
     */
    public String getPasswordRequirements() {
        StringBuilder requirements = new StringBuilder();

        try {
            int minLength = securitySettingsService.getIntValue("PASSWORD_MIN_LENGTH", 8);
            boolean requireUppercase = securitySettingsService.getBooleanValue("PASSWORD_REQUIRE_UPPERCASE", true);
            boolean requireLowercase = securitySettingsService.getBooleanValue("PASSWORD_REQUIRE_LOWERCASE", true);
            boolean requireNumbers = securitySettingsService.getBooleanValue("PASSWORD_REQUIRE_NUMBERS", true);
            boolean requireSymbols = securitySettingsService.getBooleanValue("PASSWORD_REQUIRE_SYMBOLS", true);

            requirements.append("La contraseña debe tener:\n");
            requirements.append("• Al menos ").append(minLength).append(" caracteres\n");

            if (requireUppercase) {
                requirements.append("• Al menos una letra mayúscula\n");
            }

            if (requireLowercase) {
                requirements.append("• Al menos una letra minúscula\n");
            }

            if (requireNumbers) {
                requirements.append("• Al menos un número\n");
            }

            if (requireSymbols) {
                requirements.append("• Al menos un símbolo especial\n");
            }

            requirements.append("• No debe contener espacios en blanco\n");
            requirements.append("• No debe tener secuencias repetitivas");

        } catch (Exception e) {
            requirements.append("La contraseña debe tener al menos 8 caracteres, " +
                    "incluyendo mayúsculas, minúsculas, números y símbolos especiales.");
        }

        return requirements.toString();
    }

    /**
     * Clase para encapsular resultados de validación
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> messages;

        public ValidationResult(boolean valid, List<String> messages) {
            this.valid = valid;
            this.messages = messages != null ? messages : new ArrayList<>();
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getMessages() {
            return messages;
        }

        public String getMessagesAsString() {
            return String.join(", ", messages);
        }
    }
}