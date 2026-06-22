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

@Service
public class TotpService {
    private static final Logger log = LoggerFactory.getLogger(TotpService.class);

    @Value("${app.security.two-factor.issuer:AuthSystem}")
    private String issuer;

    private final GoogleAuthenticator gAuth;

    public TotpService() {
        this.gAuth = new GoogleAuthenticator();
        log.debug("GoogleAuthenticator inicializado con configuración estándar (ventana 30s)");
    }

    /**
     * Genera un secret key usando GoogleAuthenticator.
     * Este será el único secret que se use en toda la aplicación.
     */
    public String generateSecretKey() {
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();

        // Validar que el secret sea usable inmediatamente
        try {
            gAuth.getTotpPassword(secret);
            log.debug("Secret TOTP generado y validado correctamente");
        } catch (Exception e) {
            log.error("Secret TOTP generado pero inválido: {}", e.getMessage());
            throw new RuntimeException("Secret generado inválido", e);
        }

        return secret;
    }

    /**
     * Genera la URL otpauth:// con el formato exacto requerido por Google Authenticator.
     */
    public String generateOtpAuthUrl(String secret, String email) {
        try {
            String accountName = URLEncoder.encode(email, StandardCharsets.UTF_8);
            String issuerEncoded = URLEncoder.encode(issuer, StandardCharsets.UTF_8);

            String otpAuthUrl = String.format(
                    "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                    issuerEncoded,
                    accountName,
                    secret,
                    issuerEncoded);

            log.debug("URL otpauth generada para email [masked]");
            return otpAuthUrl;

        } catch (Exception e) {
            log.error("Error generando URL otpauth: {}", e.getMessage());
            throw new RuntimeException("Error generando URL otpauth", e);
        }
    }

    /**
     * Para compatibilidad con código existente.
     */
    public String generateQRCodeImageUri(String secret, String email) {
        return generateOtpAuthUrl(secret, email);
    }

    /**
     * Genera el QR code en base64 usando el secret exacto de la BD.
     */
    public String generateQRCodeBase64(String secret, String email) throws WriterException, IOException {
        log.debug("Generando QR code para email [masked]");

        String qrCodeUri = generateOtpAuthUrl(secret, email);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(qrCodeUri, BarcodeFormat.QR_CODE, 300, 300);

        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);

        String base64QR = Base64.getEncoder().encodeToString(pngOutputStream.toByteArray());

        log.debug("QR code generado exitosamente");
        return base64QR;
    }

    /**
     * Verifica un código TOTP usando el secret almacenado en la BD.
     * Intenta con ventana estándar y luego con ventanas de tiempo extendidas.
     */
    public boolean verifyCode(String secret, int code) {
        try {
            // Paso 1: Verificar usando authorize() estándar
            boolean isValid = gAuth.authorize(secret, code);
            if (isValid) {
                log.debug("Código TOTP válido con autorización estándar");
                return true;
            }

            // Paso 2: Verificación con ventanas de tiempo múltiples (compensación de drift)
            long timeSlot = System.currentTimeMillis() / 30000L;
            for (int i = -2; i <= 2; i++) {
                long testTime = (timeSlot + i) * 30000L;
                try {
                    int expectedCode = gAuth.getTotpPassword(secret, testTime);
                    if (code == expectedCode) {
                        log.debug("Código TOTP válido en slot temporal {}", i);
                        return true;
                    }
                } catch (Exception ex) {
                    log.trace("Error en slot temporal {}: {}", i, ex.getMessage());
                }
            }

            log.warn("Código TOTP inválido en todos los slots de tiempo probados");
            return false;

        } catch (Exception e) {
            log.error("Error crítico en verificación TOTP: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sobrecarga para código como String.
     */
    public boolean verifyCode(String secret, String code) {
        try {
            int codeInt = Integer.parseInt(code);
            return verifyCode(secret, codeInt);
        } catch (NumberFormatException e) {
            log.warn("Código TOTP inválido (no es número)");
            return false;
        }
    }

    /**
     * Genera el código TOTP actual válido.
     * Usar solo para debugging/testing.
     */
    public String generateCurrentValidCode(String secret) {
        try {
            int code = gAuth.getTotpPassword(secret);
            String formattedCode = String.format("%06d", code);
            log.debug("Código TOTP de testing generado");
            return formattedCode;
        } catch (Exception e) {
            log.error("Error generando código TOTP actual: {}", e.getMessage());
            throw new RuntimeException("Error generando código de prueba", e);
        }
    }
}
