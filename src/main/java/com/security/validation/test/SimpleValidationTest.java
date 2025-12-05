package com.security.validation.test;

import com.security.dto.request.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;

/**
 * Test simple para probar validaciones sin depender de Spring Context
 */
public class SimpleValidationTest {

    public static void main(String[] args) {
        System.out.println("=== PRUEBA SIMPLE DE VALIDACIONES ===\n");

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        // Probar el email problemático que reportaste
        testProblematicEmail(validator);

        // Probar nombres
        testNames(validator);

        System.out.println("=== PRUEBAS COMPLETADAS ===");
    }

    private static void testProblematicEmail(Validator validator) {
        System.out.println("--- PROBANDO EL EMAIL PROBLEMÁTICO ---");

        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setFirstName("Juan");
        request.setLastName("García");
        request.setEmail("ivan@ivan.com"); // Este es el email que no debe ser válido
        request.setPassword("SecurePass123!");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        boolean hasEmailViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("email"));

        if (hasEmailViolation) {
            System.out.println("✅ CORRECTO: 'ivan@ivan.com' es RECHAZADO");
            violations.stream()
                    .filter(v -> v.getPropertyPath().toString().equals("email"))
                    .forEach(v -> System.out.println("   - " + v.getMessage()));
        } else {
            System.out.println("❌ ERROR: 'ivan@ivan.com' NO fue rechazado (problema reportado)");
        }

        System.out.println();
    }

    private static void testNames(Validator validator) {
        System.out.println("--- PROBANDO NOMBRES ---");

        // Nombre válido
        RegisterRequest request1 = new RegisterRequest();
        request1.setUsername("testuser");
        request1.setFirstName("María");
        request1.setLastName("García");
        request1.setEmail("test@gmail.com");
        request1.setPassword("SecurePass123!");

        Set<ConstraintViolation<RegisterRequest>> violations1 = validator.validate(request1);
        boolean hasNameViolation1 = violations1.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("firstName"));

        if (!hasNameViolation1) {
            System.out.println("✅ CORRECTO: 'María' es aceptado");
        } else {
            System.out.println("❌ ERROR: 'María' fue rechazado");
        }

        // Nombre inválido
        RegisterRequest request2 = new RegisterRequest();
        request2.setUsername("testuser");
        request2.setFirstName("John123");
        request2.setLastName("García");
        request2.setEmail("test@gmail.com");
        request2.setPassword("SecurePass123!");

        Set<ConstraintViolation<RegisterRequest>> violations2 = validator.validate(request2);
        boolean hasNameViolation2 = violations2.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("firstName"));

        if (hasNameViolation2) {
            System.out.println("✅ CORRECTO: 'John123' es RECHAZADO");
        } else {
            System.out.println("❌ ERROR: 'John123' NO fue rechazado");
        }

        System.out.println();
    }
}