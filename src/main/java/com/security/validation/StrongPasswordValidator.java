package com.security.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    // Regex para validar contraseña fuerte
    // Debe contener al menos: 1 mayúscula, 1 minúscula, 1 número, 1 carácter
    // especial, mínimo 8 caracteres, sin espacios
    private static final String PASSWORD_PATTERN = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&._-])[A-Za-z\\d@$!%*?&._-]{8,}$";

    private static final Pattern pattern = Pattern.compile(PASSWORD_PATTERN);

    @Override
    public void initialize(StrongPassword constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        boolean isValid = pattern.matcher(password).matches();

        if (!isValid) {
            // Crear mensajes de error detallados
            context.disableDefaultConstraintViolation();

            StringBuilder errorMessage = new StringBuilder("Password requirements not met:");

            if (password.length() < 8) {
                errorMessage.append(" At least 8 characters required.");
            }

            if (!Pattern.compile(".*[A-Z].*").matcher(password).matches()) {
                errorMessage.append(" At least 1 uppercase letter required.");
            }

            if (!Pattern.compile(".*[a-z].*").matcher(password).matches()) {
                errorMessage.append(" At least 1 lowercase letter required.");
            }

            if (!Pattern.compile(".*\\d.*").matcher(password).matches()) {
                errorMessage.append(" At least 1 number required.");
            }

            if (!Pattern.compile(".*[@$!%*?&._-].*").matcher(password).matches()) {
                errorMessage.append(" At least 1 special character (@$!%*?&._-) required.");
            }

            if (Pattern.compile(".*\\s.*").matcher(password).matches()) {
                errorMessage.append(" No spaces allowed.");
            }

            context.buildConstraintViolationWithTemplate(errorMessage.toString())
                    .addConstraintViolation();
        }

        return isValid;
    }
}