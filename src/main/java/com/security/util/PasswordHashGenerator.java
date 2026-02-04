package com.security.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utilidad para generar hashes BCrypt de contraseñas
 * Ejecutar esta clase para generar el hash de la contraseña del admin
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // Contraseña del administrador
        String password = "1234557@@@";

        // Generar hash
        String hash = encoder.encode(password);

        System.out.println("==============================================");
        System.out.println("GENERADOR DE HASH BCRYPT");
        System.out.println("==============================================");
        System.out.println("Contraseña original: " + password);
        System.out.println("Hash BCrypt generado:");
        System.out.println(hash);
        System.out.println("==============================================");
        System.out.println("\nCopia este hash y úsalo en el UPDATE de la base de datos:");
        System.out.println("UPDATE users SET password = '" + hash + "' WHERE email = 'admin@casamusica.com';");
        System.out.println("==============================================");

        // Verificar que el hash funciona
        boolean matches = encoder.matches(password, hash);
        System.out.println("\n✅ Verificación: " + (matches ? "Hash válido" : "Hash inválido"));
    }
}
