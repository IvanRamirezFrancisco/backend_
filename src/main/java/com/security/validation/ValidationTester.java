package com.security.validation;

import com.security.dto.request.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Clase de prueba para verificar las validaciones de nombres
 * Se ejecuta al iniciar la aplicación para validar que todo funciona
 * TEMPORALMENTE DESHABILITADA
 */
// @Component
public class ValidationTester implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n=== INICIANDO PRUEBAS COMPLETAS DE VALIDACIÓN ===");

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        // Prueba 1: Nombres válidos e inválidos
        testNames(validator);

        // Prueba 2: Emails válidos e inválidos
        testEmails(validator);

        // Prueba 3: Contraseñas válidas e inválidas
        testPasswords(validator);

        System.out.println("=== PRUEBAS COMPLETADAS ===\n");
    }

    private void testNames(Validator validator) {
        System.out.println("\n--- PRUEBAS DE NOMBRES ---");
        testValidNames(validator);
        testInvalidNames(validator);
    }

    private void testEmails(Validator validator) {
        System.out.println("\n--- PRUEBAS DE EMAILS ---");
        testValidEmails(validator);
        testInvalidEmails(validator);
    }

    private void testPasswords(Validator validator) {
        System.out.println("\n--- PRUEBAS DE CONTRASEÑAS ---");
        testValidPasswords(validator);
        testInvalidPasswords(validator);
    }

    private void testValidEmails(Validator validator) {
        System.out.println("Probando emails VÁLIDOS:");

        String[] validEmails = {
                "test@gmail.com", "user@outlook.com", "admin@yahoo.com",
                "example@hotmail.com", "contact@protonmail.com"
        };

        for (String email : validEmails) {
            RegisterRequest request = createTestRequest();
            request.setEmail(email);

            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

            boolean hasEmailViolation = violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("email"));

            if (!hasEmailViolation) {
                System.out.println("✅ VÁLIDO: '" + email + "'");
            } else {
                System.out.println("❌ ERROR: '" + email + "' debería ser válido");
                violations.stream()
                        .filter(v -> v.getPropertyPath().toString().equals("email"))
                        .forEach(v -> System.out.println("   - " + v.getMessage()));
            }
        }
    }

    private void testInvalidEmails(Validator validator) {
        System.out.println("Probando emails INVÁLIDOS:");

        String[] invalidEmails = {
                "ivan@ivan.com", // El email que reportaste como problema
                "test@test.com", "admin@fake.com", "user@example.com",
                "contact@localhost", "demo@demo.com", "prueba@prueba.com"
        };

        for (String email : invalidEmails) {
            RegisterRequest request = createTestRequest();
            request.setEmail(email);

            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

            boolean hasEmailViolation = violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("email"));

            if (hasEmailViolation) {
                System.out.println("✅ BLOQUEADO: '" + email + "'");
                violations.stream()
                        .filter(v -> v.getPropertyPath().toString().equals("email"))
                        .forEach(v -> System.out.println("   - " + v.getMessage()));
            } else {
                System.out.println("❌ ERROR: '" + email + "' debería ser bloqueado");
            }
        }
    }

    private void testValidPasswords(Validator validator) {
        System.out.println("Probando contraseñas VÁLIDAS:");

        String[] validPasswords = {
                "SecurePass123!", "MyStr0ng$Pass", "C0mplex#2024",
                "SafeP@ssw0rd", "Univ3rs3*2024"
        };

        for (String password : validPasswords) {
            RegisterRequest request = createTestRequest();
            request.setPassword(password);

            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

            boolean hasPasswordViolation = violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("password"));

            if (!hasPasswordViolation) {
                System.out.println("✅ VÁLIDA: '" + password + "'");
            } else {
                System.out.println("❌ ERROR: '" + password + "' debería ser válida");
                violations.stream()
                        .filter(v -> v.getPropertyPath().toString().equals("password"))
                        .forEach(v -> System.out.println("   - " + v.getMessage()));
            }
        }
    }

    private void testInvalidPasswords(Validator validator) {
        System.out.println("Probando contraseñas INVÁLIDAS (patrones simples):");

        String[] invalidPasswords = {
                "123456", "qwerty", "password", "abc123",
                "admin123", "111111", "123456789", "qwerty123"
        };

        for (String password : invalidPasswords) {
            RegisterRequest request = createTestRequest();
            request.setPassword(password);

            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

            boolean hasPasswordViolation = violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("password"));

            if (hasPasswordViolation) {
                System.out.println("✅ BLOQUEADA: '" + password + "'");
                violations.stream()
                        .filter(v -> v.getPropertyPath().toString().equals("password"))
                        .limit(1) // Solo mostrar el primer error para no saturar
                        .forEach(v -> System.out.println("   - " + v.getMessage()));
            } else {
                System.out.println("❌ ERROR: '" + password + "' debería ser bloqueada");
            }
        }
    }

    private void testValidNames(Validator validator) {
        System.out.println("Probando nombres VÁLIDOS:");

        String[] validNames = {
                "María", "José-Luis", "O'Connor", "François",
                "Ana María", "José Antonio", "María de los Ángeles"
        };

        for (String name : validNames) {
            RegisterRequest request = createTestRequest();
            request.setFirstName(name);
            request.setLastName("García");

            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

            // Filtrar solo violaciones de firstName
            boolean hasNameViolation = violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("firstName"));

            if (!hasNameViolation) {
                System.out.println("✅ VÁLIDO: '" + name + "'");
            } else {
                System.out.println("❌ ERROR: '" + name + "' debería ser válido");
                violations.stream()
                        .filter(v -> v.getPropertyPath().toString().equals("firstName"))
                        .forEach(v -> System.out.println("   - " + v.getMessage()));
            }
        }
    }

    private void testInvalidNames(Validator validator) {
        System.out.println("Probando nombres INVÁLIDOS (ataques de seguridad):");

        String[] invalidNames = {
                "<script>alert('xss')</script>", // XSS
                "DROP TABLE users", // SQL Injection
                "John123", // Números
                "user@domain.com", // Símbolos
                "alert('hack')", // JavaScript
                "!!!", // Solo símbolos
        };

        for (String name : invalidNames) {
            RegisterRequest request = createTestRequest();
            request.setFirstName(name);
            request.setLastName("García");

            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

            // Filtrar solo violaciones de firstName
            boolean hasNameViolation = violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("firstName"));

            if (hasNameViolation) {
                System.out.println("✅ BLOQUEADO: '" + name + "'");
            } else {
                System.out.println("❌ ERROR: '" + name + "' debería ser bloqueado");
            }
        }
    }

    private RegisterRequest createTestRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setFirstName("Juan");
        request.setLastName("García");
        request.setEmail("test@gmail.com");
        request.setPassword("SecurePass123!");
        return request;
    }
}