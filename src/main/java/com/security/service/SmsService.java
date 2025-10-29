package com.security.service;

import com.security.entity.User;
import com.security.entity.SmsVerificationCode;
import com.security.repository.SmsVerificationCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ContentType;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class SmsService {

    @Autowired
    private SmsVerificationCodeRepository smsVerificationCodeRepository;

    @Value("${app.notification.sms.project-id}")
    private String projectId;

    @Value("${app.notification.sms.access-key-id}")
    private String accessKeyId;

    @Value("${app.notification.sms.secret-key}")
    private String secretKey;

    @Value("${app.notification.sms.from-number}")
    private String fromNumber;

    @Value("${app.notification.sms.dev-mode:false}")
    private boolean devMode;

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String SINCH_SMS_URL = "https://sms.api.sinch.com/xms/v1/{project_id}/batches";

    @PostConstruct
    private void initialize() {
        System.out.println("🔧 Sinch SMS Service inicializado");
        System.out.println("📱 Modo desarrollo: " + devMode);
        System.out.println("📞 Número origen: " + fromNumber);
    }

    /**
     * Envía un código de verificación SMS al número especificado
     */
    public void sendVerificationCode(User user, String phoneNumber) {
        try {
            // Generar código de 6 dígitos
            String code = generateSixDigitCode();

            // Guardar código en base de datos
            SmsVerificationCode smsCode = new SmsVerificationCode();
            smsCode.setUser(user);
            smsCode.setPhone(phoneNumber);
            smsCode.setCode(code);
            smsCode.setExpiryDate(LocalDateTime.now().plusMinutes(5)); // 5 minutos de expiración
            smsCode.setUsed(false);
            smsCode.setAttempts(0);

            // Limpiar códigos anteriores no usados del mismo usuario y teléfono
            smsVerificationCodeRepository.deleteByUserAndPhoneAndUsedFalse(user, phoneNumber);

            // Guardar nuevo código
            smsVerificationCodeRepository.save(smsCode);

            // Enviar SMS
            sendSms(phoneNumber, "Tu código de verificación es: " + code + ". Válido por 5 minutos.");

            System.out.println("✅ Código SMS enviado a " + phoneNumber + " para usuario: " + user.getEmail());

        } catch (Exception e) {
            System.err.println("❌ Error enviando SMS: " + e.getMessage());
            throw new RuntimeException("Error al enviar código SMS", e);
        }
    }

    /**
     * Envía un código de verificación para el login 2FA
     */
    public void sendLoginVerificationCode(User user) {
        if (user.getPhone() == null || user.getPhone().trim().isEmpty()) {
            throw new RuntimeException("El usuario no tiene un número de teléfono configurado");
        }

        sendVerificationCode(user, user.getPhone());
    }

    /**
     * Verifica un código SMS
     */
    public boolean verifyCode(User user, String phone, String code) {
        Optional<SmsVerificationCode> smsCodeOpt = smsVerificationCodeRepository
                .findValidCode(code, phone, LocalDateTime.now());

        if (smsCodeOpt.isEmpty()) {
            return false;
        }

        SmsVerificationCode smsCode = smsCodeOpt.get();

        // Verificar que pertenece al usuario correcto
        if (!smsCode.getUser().getId().equals(user.getId())) {
            return false;
        }

        // Marcar como usado
        smsCode.setUsed(true);
        smsCode.setAttempts(smsCode.getAttempts() + 1);
        smsVerificationCodeRepository.save(smsCode);

        return true;
    }

    /**
     * Verifica código para login 2FA usando el teléfono guardado del usuario
     */
    public boolean verifyLoginCode(User user, String code) {
        if (user.getPhone() == null || user.getPhone().trim().isEmpty()) {
            return false;
        }

        return verifyCode(user, user.getPhone(), code);
    }

    /**
     * Envía SMS usando Twilio
     */
    private void sendSms(String toPhoneNumber, String messageBody) {
        // MODO DE DESARROLLO: Simular envío SMS sin usar Twilio
        if (devMode) {
            System.out.println("🧪 [MODO DESARROLLO] SMS simulado a: " + toPhoneNumber);
            System.out.println("🧪 [MODO DESARROLLO] Mensaje: " + messageBody);
            System.out.println("🧪 [MODO DESARROLLO] ¡SMS enviado exitosamente (simulado)!");
            return;
        }

        // MODO PRODUCCIÓN: Usar Sinch
        try {
            sendSinchSms(toPhoneNumber, messageBody);
            System.out.println("📱 SMS enviado exitosamente via Sinch a: " + toPhoneNumber);

        } catch (Exception e) {
            System.err.println("❌ Error en Sinch: " + e.getMessage());
            throw new RuntimeException("Error enviando SMS: " + e.getMessage(), e);
        }
    }

    /**
     * Genera un código de 6 dígitos
     */
    private String generateSixDigitCode() {
        return String.format("%06d", secureRandom.nextInt(1000000));
    }

    /**
     * Limpia códigos expirados
     */
    public void cleanupExpiredCodes() {
        smsVerificationCodeRepository.deleteExpiredCodes(LocalDateTime.now());
        System.out.println("🧹 Códigos SMS expirados eliminados");
    }

    /**
     * Valida formato de número de teléfono
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }

        // Formato básico: debe empezar con + y tener entre 10 y 15 dígitos
        String cleanPhone = phoneNumber.trim().replaceAll("\\s+", "");
        return cleanPhone.matches("^\\+[1-9]\\d{9,14}$");
    }

    /**
     * Normaliza el formato del número de teléfono
     */
    public String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }

        return phoneNumber.trim().replaceAll("\\s+", "");
    }

    /**
     * Envía SMS usando Sinch REST API
     */
    private void sendSinchSms(String toPhoneNumber, String messageBody) throws Exception {
        String url = SINCH_SMS_URL.replace("{project_id}", projectId);

        // Debug información
        System.out.println("🔧 Debug Sinch:");
        System.out.println("📍 URL: " + url);
        System.out.println("🆔 Project ID: " + projectId);
        System.out.println("🔑 Access Key ID: " + accessKeyId);
        System.out.println("📞 From Number (original): " + fromNumber);
        System.out.println("📱 To Number: " + toPhoneNumber);

        // Asegurar que el número "from" tenga el prefijo +
        String normalizedFromNumber = fromNumber.startsWith("+") ? fromNumber : "+" + fromNumber;
        System.out.println("📞 From Number (normalizado): " + normalizedFromNumber);

        // Crear el JSON body para Sinch
        String jsonBody = String.format("""
                {
                    "from": "%s",
                    "to": ["%s"],
                    "body": "%s"
                }
                """, normalizedFromNumber, toPhoneNumber, messageBody);

        System.out.println("📄 JSON Body: " + jsonBody);

        // Usar autenticación Bearer con API Token (secret-key contiene el API Token)
        String apiToken = secretKey;

        System.out.println("🔐 API Token: " + apiToken);
        System.out.println("🔐 Auth Header: Bearer " + apiToken);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);

            // Headers con Bearer Authentication
            httpPost.setHeader("Authorization", "Bearer " + apiToken);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Accept", "application/json");

            // Body
            httpPost.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

            // Ejecutar request
            CloseableHttpResponse response = httpClient.execute(httpPost);
            try {
                int statusCode = response.getCode();

                if (statusCode >= 200 && statusCode < 300) {
                    System.out.println("✅ SMS enviado exitosamente via Sinch");
                    System.out.println("📱 Status: " + statusCode);
                } else {
                    String responseBody = "";
                    if (response.getEntity() != null) {
                        responseBody = new String(response.getEntity().getContent().readAllBytes());
                    }
                    System.err.println("❌ Error Sinch - Status: " + statusCode);
                    System.err.println("❌ Response: " + responseBody);
                    throw new RuntimeException("Sinch API error: " + statusCode + " - " + responseBody);
                }
            } finally {
                response.close();
            }
        }
    }
}