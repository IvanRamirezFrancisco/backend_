package com.security.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TotpService {
    private static final Logger logger = LoggerFactory.getLogger(TotpService.class);

    @Value("${app.security.two-factor.issuer:AuthSystem}")
    private String issuer;

    private final GoogleAuthenticator gAuth;

    // Constructor para configurar GoogleAuthenticator correctamente
    public TotpService() {
        this.gAuth = new GoogleAuthenticator();
        System.out.println("🔧 GoogleAuthenticator inicializado con configuración estándar");
        System.out.println("  - Ventana de tiempo: 30 segundos");
        System.out.println("  - Tolerancia: Ventanas múltiples");
        System.out.println("  - Issuer por defecto: " + issuer);
    }

    /**
     * MÉTODO PRINCIPAL: Genera un secret key usando GoogleAuthenticator.
     * Este será el ÚNICO secret que se use en toda la aplicación.
     */
    public String generateSecretKey() {
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();

        System.out.println("🔑 Secret generado: " + secret.substring(0, 4) + "... (length: " + secret.length() + ")");

        // VALIDACIÓN: Verificar que el secret se puede usar inmediatamente
        try {
            int testCode = gAuth.getTotpPassword(secret);
            System.out.println("✅ Secret válido - código de prueba generado: " + testCode);
        } catch (Exception e) {
            logger.error("❌ Error validando secret generado: " + e.getMessage());
            throw new RuntimeException("Secret generado inválido", e);
        }

        return secret;
    }

    /**
     * MÉTODO CRÍTICO: Genera la URL otpauth:// con el formato EXACTO requerido.
     * Esta URL es la que va dentro del QR code.
     */
    public String generateOtpAuthUrl(String secret, String email) {
        try {
            // FORMATO EXACTO requerido por Google Authenticator:
            // otpauth://totp/ISSUER:EMAIL?secret=SECRET&issuer=ISSUER&algorithm=SHA1&digits=6&period=30

            String accountName = URLEncoder.encode(email, StandardCharsets.UTF_8);
            String issuerEncoded = URLEncoder.encode(issuer, StandardCharsets.UTF_8);

            String otpAuthUrl = String.format(
                    "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                    issuerEncoded,
                    accountName,
                    secret, // ¡NO codificar el secret! Ya viene en base32 de GoogleAuth
                    issuerEncoded);

            System.out.println("🔗 URL otpauth generada:");
            System.out.println("  " + otpAuthUrl);
            System.out.println("🔍 Componentes:");
            System.out.println("  - Issuer: " + issuer);
            System.out.println("  - Email: " + email);
            System.out.println("  - Secret: " + secret.substring(0, 4) + "...");
            System.out.println("  - Algorithm: SHA1");
            System.out.println("  - Digits: 6");
            System.out.println("  - Period: 30");

            return otpAuthUrl;

        } catch (Exception e) {
            logger.error("❌ Error generando URL otpauth: " + e.getMessage());
            throw new RuntimeException("Error generando URL otpauth", e);
        }
    }

    /**
     * MÉTODO LEGACY: Para compatibilidad con código existente
     */
    public String generateQRCodeImageUri(String secret, String email) {
        return generateOtpAuthUrl(secret, email);
    }

    /**
     * MÉTODO PRINCIPAL: Genera el QR code en base64 usando el secret exacto de la
     * BD
     */
    public String generateQRCodeBase64(String secret, String email) throws WriterException, IOException {
        System.out.println("🖼️  Generando QR code para:");
        System.out.println("  - Email: " + email);
        System.out.println("  - Secret: " + secret.substring(0, 4) + "... (usando secret EXACTO de BD)");

        String qrCodeUri = generateOtpAuthUrl(secret, email);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(qrCodeUri, BarcodeFormat.QR_CODE, 300, 300);

        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);

        String base64QR = Base64.getEncoder().encodeToString(pngOutputStream.toByteArray());

        System.out.println("✅ QR code generado exitosamente (base64 length: " + base64QR.length() + ")");

        return base64QR;
    }

    /**
     * MÉTODO DE VERIFICACIÓN PRINCIPAL
     * Verifica un código TOTP usando el secret EXACTO almacenado en la BD
     */
    public boolean verifyCode(String secret, int code) {
        try {
            long currentTimeMillis = System.currentTimeMillis();

            System.out.println("🔐 === VERIFICACIÓN TOTP ===");
            System.out.println("  - Código ingresado: " + code);
            System.out.println(
                    "  - Secret (primeros 4 chars): " + secret.substring(0, Math.min(4, secret.length())) + "...");
            System.out.println("  - Secret length: " + secret.length());
            System.out.println("  - Timestamp actual: " + currentTimeMillis);
            System.out.println("  - Fecha actual: " + new java.util.Date(currentTimeMillis));

            // PASO 1: Verificar usando authorize() estándar (usa tiempo del sistema)
            boolean isValid = gAuth.authorize(secret, code);
            if (isValid) {
                System.out.println("  ✅ ÉXITO: Código válido con authorize() estándar");
                return true;
            }

            System.out.println("  ⚠️  authorize() estándar falló, probando ventanas de tiempo...");

            // PASO 2: Verificación con ventanas de tiempo múltiples
            long timeSlot = currentTimeMillis / 30000L;

            System.out.println("  🔄 Probando ventanas de tiempo:");
            System.out.println("    Time slot base: " + timeSlot);

            // Probar slots: -2, -1, 0, +1, +2 (ventana de 2.5 minutos total)
            for (int i = -2; i <= 2; i++) {
                long testTimeSlot = timeSlot + i;
                long testTime = testTimeSlot * 30000L;

                try {
                    // Generar código esperado para este slot
                    int expectedCode = gAuth.getTotpPassword(secret, testTime);
                    System.out.println("    Slot " + String.format("%+2d", i) + ": esperado "
                            + String.format("%06d", expectedCode) + " (time: " + testTime + ")");

                    // Verificar si el código ingresado coincide
                    if (code == expectedCode) {
                        System.out.println("  ✅ ÉXITO: Código válido en slot " + i);
                        return true;
                    }

                } catch (Exception ex) {
                    System.out.println("    Slot " + String.format("%+2d", i) + ": Error - " + ex.getMessage());
                }
            }

            System.out.println("  ❌ FALLO: Código inválido en todos los slots probados");
            System.out.println("  🔍 Código ingresado: " + String.format("%06d", code));
            System.out.println(
                    "  💡 Sugerencia: Verifica que Google Authenticator y servidor tengan tiempo sincronizado");

            return false;

        } catch (Exception e) {
            logger.error("❌ ERROR CRÍTICO en verificación TOTP: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sobrecarga para código como String
     */
    public boolean verifyCode(String secret, String code) {
        try {
            int codeInt = Integer.parseInt(code);
            return verifyCode(secret, codeInt);
        } catch (NumberFormatException e) {
            logger.error("❌ Código inválido (no es número): {}", code);
            return false;
        }
    }

    /**
     * MÉTODO DE TESTING: Genera el código TOTP actual válido
     * Útil para debugging y testing
     */
    public String generateCurrentValidCode(String secret) {
        try {
            int code = gAuth.getTotpPassword(secret);
            String formattedCode = String.format("%06d", code);

            System.out.println("🧪 Código TOTP actual para testing: " + formattedCode);
            System.out.println("  - Secret usado: " + secret.substring(0, Math.min(4, secret.length())) + "...");
            System.out.println("  - Timestamp: " + System.currentTimeMillis());

            return formattedCode;
        } catch (Exception e) {
            logger.error("❌ Error generando código actual: " + e.getMessage());
            throw new RuntimeException("Error generando código de prueba", e);
        }
    }
}
