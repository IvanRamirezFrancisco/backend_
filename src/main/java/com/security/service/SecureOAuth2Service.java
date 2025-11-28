package com.security.service;

import com.security.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Servicio OAuth2.0 seguro con Authorization Code Flow
 * Implementa protección contra ataques CSRF y manejo seguro de tokens
 * Utiliza almacenamiento en memoria en lugar de Redis
 */
@Service
public class SecureOAuth2Service {

    private static final Logger logger = LoggerFactory.getLogger(SecureOAuth2Service.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SecureLoggingService loggingService;

    // Almacenamiento en memoria para todos los componentes OAuth2.0
    private final Map<String, OAuth2AuthorizationCode> authorizationCodes = new ConcurrentHashMap<>();
    private final Map<String, OAuth2AccessToken> accessTokens = new ConcurrentHashMap<>();
    private final Map<String, OAuth2RefreshToken> refreshTokens = new ConcurrentHashMap<>();
    private final Map<String, OAuth2State> stateStorage = new ConcurrentHashMap<>();

    // Ejecutor para limpieza automática de datos expirados
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    // Constructor para inicializar limpieza automática
    public SecureOAuth2Service() {
        // Ejecutar limpieza cada 30 minutos
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredTokens, 30, 30,
                java.util.concurrent.TimeUnit.MINUTES);
    }

    @Value("${app.oauth2.authorization-code-expiration:600}") // 10 minutos
    private int authCodeExpirationSeconds;

    @Value("${app.oauth2.access-token-expiration:3600}") // 1 hora
    private int accessTokenExpirationSeconds;

    @Value("${app.oauth2.refresh-token-expiration:2592000}") // 30 días
    private int refreshTokenExpirationSeconds;

    @Value("${app.oauth2.redirect-uri-validation:true}")
    private boolean validateRedirectUri;

    // Constantes para identificación de componentes OAuth2 (ya no se usan con
    // almacenamiento en memoria)

    // Clientes OAuth registrados
    private static final Map<String, OAuth2Client> REGISTERED_CLIENTS = Map.of(
            "casa-musica-web", new OAuth2Client(
                    "casa-musica-web",
                    "web_client_secret_here",
                    Set.of("http://localhost:4200/auth/callback", "https://casamusica.com/auth/callback"),
                    Set.of("read", "write"),
                    "authorization_code"),
            "casa-musica-mobile", new OAuth2Client(
                    "casa-musica-mobile",
                    "mobile_client_secret_here",
                    Set.of("app://casamusica/auth/callback"),
                    Set.of("read", "write"),
                    "authorization_code"));

    /**
     * Genera una URL de autorización OAuth2
     */
    public String generateAuthorizationUrl(String clientId, String redirectUri, String scope, String state) {
        try {
            // Validar cliente
            OAuth2Client client = REGISTERED_CLIENTS.get(clientId);
            if (client == null) {
                throw new IllegalArgumentException("Invalid client ID");
            }

            // Validar redirect URI
            if (validateRedirectUri && !client.getRedirectUris().contains(redirectUri)) {
                throw new IllegalArgumentException("Invalid redirect URI");
            }

            // Generar y almacenar state para protección CSRF
            String secureState = generateSecureState();
            storeState(secureState, clientId, redirectUri, scope, state);

            // Construir URL de autorización
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("/oauth2/authorize")
                    .append("?response_type=code")
                    .append("&client_id=").append(clientId)
                    .append("&redirect_uri=").append(java.net.URLEncoder.encode(redirectUri, "UTF-8"))
                    .append("&scope=").append(java.net.URLEncoder.encode(scope, "UTF-8"))
                    .append("&state=").append(secureState);

            logger.info("Authorization URL generated for client: {}", clientId);
            return urlBuilder.toString();

        } catch (Exception e) {
            logger.error("Error generating authorization URL: {}", e.getMessage());
            throw new RuntimeException("Failed to generate authorization URL", e);
        }
    }

    /**
     * Genera un código de autorización tras el consentimiento del usuario
     */
    public String generateAuthorizationCode(String userId, String clientId, String redirectUri,
            String scope, String state) {
        try {
            // Validar state para protección CSRF
            if (!validateState(state, clientId, redirectUri, scope)) {
                throw new SecurityException("Invalid state parameter - possible CSRF attack");
            }

            // Generar código de autorización seguro
            String authCode = generateSecureToken();

            // Almacenar código con metadata en memoria
            OAuth2AuthorizationCode codeData = new OAuth2AuthorizationCode(
                    authCode, userId, clientId, redirectUri, scope,
                    LocalDateTime.now().plus(authCodeExpirationSeconds, ChronoUnit.SECONDS));

            authorizationCodes.put(authCode, codeData);

            // Log del evento
            loggingService.logAuthenticationEvent("OAUTH2_CODE_GENERATED", userId, null,
                    getCurrentClientIp(), null, true);

            logger.info("Authorization code generated for user: {} and client: {}", userId, clientId);
            return authCode;

        } catch (Exception e) {
            logger.error("Error generating authorization code: {}", e.getMessage());
            throw new RuntimeException("Failed to generate authorization code", e);
        }
    }

    /**
     * Intercambia código de autorización por tokens de acceso
     */
    public OAuth2TokenResponse exchangeCodeForTokens(String code, String clientId, String clientSecret,
            String redirectUri) {
        try {
            // Validar cliente
            OAuth2Client client = REGISTERED_CLIENTS.get(clientId);
            if (client == null || !client.getClientSecret().equals(clientSecret)) {
                throw new SecurityException("Invalid client credentials");
            }

            // Obtener y validar código de autorización de memoria
            OAuth2AuthorizationCode codeData = authorizationCodes.get(code);

            if (codeData == null) {
                throw new RuntimeException("Invalid authorization code");
            }

            if (!codeData.getClientId().equals(clientId) ||
                    !codeData.getRedirectUri().equals(redirectUri)) {
                throw new RuntimeException("Invalid client credentials or redirect URI");
            }

            if (LocalDateTime.now().isAfter(codeData.getExpiresAt())) {
                authorizationCodes.remove(code);
                throw new RuntimeException("Authorization code expired");
            }

            // Eliminar código usado (one-time use)
            authorizationCodes.remove(code);

            // Generar tokens
            String accessToken = generateSecureToken();
            String refreshToken = generateSecureToken();

            // Almacenar tokens en memoria
            storeAccessToken(accessToken, codeData.getUserId(), clientId, codeData.getScope());
            storeRefreshToken(refreshToken, codeData.getUserId(), clientId, codeData.getScope());

            // Log del evento
            loggingService.logAuthenticationEvent("OAUTH2_TOKEN_EXCHANGE", codeData.getUserId(),
                    null, getCurrentClientIp(), null, true);

            logger.info("Tokens generated for user: {} and client: {}", codeData.getUserId(), clientId);

            return new OAuth2TokenResponse(
                    accessToken,
                    "Bearer",
                    accessTokenExpirationSeconds,
                    refreshToken,
                    codeData.getScope());

        } catch (Exception e) {
            logger.error("Error exchanging code for tokens: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange code for tokens", e);
        }
    }

    /**
     * Renueva token de acceso usando refresh token
     */
    public OAuth2TokenResponse refreshAccessToken(String refreshToken, String clientId, String clientSecret) {
        try {
            // Validar cliente
            OAuth2Client client = REGISTERED_CLIENTS.get(clientId);
            if (client == null || !client.getClientSecret().equals(clientSecret)) {
                throw new SecurityException("Invalid client credentials");
            }

            // Obtener y validar refresh token de memoria
            OAuth2RefreshToken tokenData = refreshTokens.get(refreshToken);

            if (tokenData == null) {
                throw new RuntimeException("Invalid refresh token");
            }

            if (!tokenData.getClientId().equals(clientId)) {
                throw new RuntimeException("Invalid client for refresh token");
            }

            if (LocalDateTime.now().isAfter(tokenData.getExpiresAt())) {
                refreshTokens.remove(refreshToken);
                throw new RuntimeException("Refresh token expired");
            }

            // Generar nuevo access token
            String newAccessToken = generateSecureToken();
            storeAccessToken(newAccessToken, tokenData.getUserId(), clientId, tokenData.getScope());

            // Log del evento
            loggingService.logAuthenticationEvent("OAUTH2_TOKEN_REFRESH", tokenData.getUserId(),
                    null, getCurrentClientIp(), null, true);

            logger.info("Access token refreshed for user: {} and client: {}", tokenData.getUserId(), clientId);

            return new OAuth2TokenResponse(
                    newAccessToken,
                    "Bearer",
                    accessTokenExpirationSeconds,
                    refreshToken, // Reutilizar refresh token
                    tokenData.getScope());

        } catch (Exception e) {
            logger.error("Error refreshing access token: {}", e.getMessage());
            throw new RuntimeException("Failed to refresh access token", e);
        }
    }

    /**
     * Valida un token de acceso OAuth2
     */
    public OAuth2TokenValidation validateAccessToken(String accessToken) {
        try {
            OAuth2AccessToken tokenData = accessTokens.get(accessToken);

            if (tokenData == null) {
                return OAuth2TokenValidation.invalid("Token not found");
            }

            if (LocalDateTime.now().isAfter(tokenData.getExpiresAt())) {
                accessTokens.remove(accessToken);
                return OAuth2TokenValidation.invalid("Token expired");
            }

            return OAuth2TokenValidation.valid(tokenData.getUserId(), tokenData.getClientId(),
                    tokenData.getScope());

        } catch (Exception e) {
            logger.error("Error validating access token: {}", e.getMessage());
            return OAuth2TokenValidation.invalid("Validation error");
        }
    }

    /**
     * Revoca un token OAuth2
     */
    public boolean revokeToken(String token, String clientId, String clientSecret) {
        try {
            // Validar cliente
            OAuth2Client client = REGISTERED_CLIENTS.get(clientId);
            if (client == null || !client.getClientSecret().equals(clientSecret)) {
                return false;
            }

            // Intentar revocar como access token
            boolean accessRevoked = accessTokens.remove(token) != null;

            // Intentar revocar como refresh token
            boolean refreshRevoked = refreshTokens.remove(token) != null;

            boolean revoked = accessRevoked || refreshRevoked;

            if (revoked) {
                logger.info("Token revoked for client: {}", clientId);
            }

            return revoked;

        } catch (Exception e) {
            logger.error("Error revoking token: {}", e.getMessage());
            return false;
        }
    }

    // Métodos auxiliares

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateSecureState() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void storeState(String state, String clientId, String redirectUri, String scope, String originalState) {
        OAuth2State stateData = new OAuth2State(state, clientId, redirectUri, scope, originalState,
                LocalDateTime.now().plus(10, ChronoUnit.MINUTES));
        stateStorage.put(state, stateData);
    }

    private boolean validateState(String state, String clientId, String redirectUri, String scope) {
        OAuth2State stateData = stateStorage.get(state);

        if (stateData == null || LocalDateTime.now().isAfter(stateData.getExpiresAt())) {
            return false;
        }

        stateStorage.remove(state); // One-time use

        return stateData.getClientId().equals(clientId) &&
                stateData.getRedirectUri().equals(redirectUri);
    }

    private void storeAccessToken(String token, String userId, String clientId, String scope) {
        OAuth2AccessToken tokenData = new OAuth2AccessToken(
                token, userId, clientId, scope,
                LocalDateTime.now().plus(accessTokenExpirationSeconds, ChronoUnit.SECONDS));
        accessTokens.put(token, tokenData);
    }

    private void storeRefreshToken(String token, String userId, String clientId, String scope) {
        OAuth2RefreshToken tokenData = new OAuth2RefreshToken(
                token, userId, clientId, scope,
                LocalDateTime.now().plus(refreshTokenExpirationSeconds, ChronoUnit.SECONDS));
        refreshTokens.put(token, tokenData);
    }

    private String getCurrentClientIp() {
        // Implementar según el contexto
        return "unknown";
    }

    /**
     * Limpieza automática de tokens y códigos expirados
     */
    public void cleanupExpiredTokens() {
        try {
            LocalDateTime now = LocalDateTime.now();

            // Limpiar códigos de autorización expirados
            authorizationCodes.entrySet().removeIf(entry -> now.isAfter(entry.getValue().getExpiresAt()));

            // Limpiar access tokens expirados
            accessTokens.entrySet().removeIf(entry -> now.isAfter(entry.getValue().getExpiresAt()));

            // Limpiar refresh tokens expirados
            refreshTokens.entrySet().removeIf(entry -> now.isAfter(entry.getValue().getExpiresAt()));

            // Limpiar estados expirados
            stateStorage.entrySet().removeIf(entry -> now.isAfter(entry.getValue().getExpiresAt()));

            logger.debug("OAuth2 cleanup completed. Active tokens: {} access, {} refresh",
                    accessTokens.size(), refreshTokens.size());

        } catch (Exception e) {
            logger.error("Error during OAuth2 cleanup: {}", e.getMessage());
        }
    }

    // Clases de datos internas

    public static class OAuth2Client {
        private final String clientId;
        private final String clientSecret;
        private final Set<String> redirectUris;
        private final Set<String> scopes;
        private final String grantType;

        public OAuth2Client(String clientId, String clientSecret, Set<String> redirectUris,
                Set<String> scopes, String grantType) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.redirectUris = redirectUris;
            this.scopes = scopes;
            this.grantType = grantType;
        }

        // Getters
        public String getClientId() {
            return clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public Set<String> getRedirectUris() {
            return redirectUris;
        }

        public Set<String> getScopes() {
            return scopes;
        }

        public String getGrantType() {
            return grantType;
        }
    }

    public static class OAuth2AuthorizationCode {
        private final String code;
        private final String userId;
        private final String clientId;
        private final String redirectUri;
        private final String scope;
        private final LocalDateTime expiresAt;

        public OAuth2AuthorizationCode(String code, String userId, String clientId,
                String redirectUri, String scope, LocalDateTime expiresAt) {
            this.code = code;
            this.userId = userId;
            this.clientId = clientId;
            this.redirectUri = redirectUri;
            this.scope = scope;
            this.expiresAt = expiresAt;
        }

        // Getters
        public String getCode() {
            return code;
        }

        public String getUserId() {
            return userId;
        }

        public String getClientId() {
            return clientId;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public String getScope() {
            return scope;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }
    }

    public static class OAuth2AccessToken {
        private final String token;
        private final String userId;
        private final String clientId;
        private final String scope;
        private final LocalDateTime expiresAt;

        public OAuth2AccessToken(String token, String userId, String clientId,
                String scope, LocalDateTime expiresAt) {
            this.token = token;
            this.userId = userId;
            this.clientId = clientId;
            this.scope = scope;
            this.expiresAt = expiresAt;
        }

        // Getters
        public String getToken() {
            return token;
        }

        public String getUserId() {
            return userId;
        }

        public String getClientId() {
            return clientId;
        }

        public String getScope() {
            return scope;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }
    }

    public static class OAuth2RefreshToken {
        private final String token;
        private final String userId;
        private final String clientId;
        private final String scope;
        private final LocalDateTime expiresAt;

        public OAuth2RefreshToken(String token, String userId, String clientId,
                String scope, LocalDateTime expiresAt) {
            this.token = token;
            this.userId = userId;
            this.clientId = clientId;
            this.scope = scope;
            this.expiresAt = expiresAt;
        }

        // Getters
        public String getToken() {
            return token;
        }

        public String getUserId() {
            return userId;
        }

        public String getClientId() {
            return clientId;
        }

        public String getScope() {
            return scope;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }
    }

    public static class OAuth2State {
        private final String state;
        private final String clientId;
        private final String redirectUri;
        private final String scope;
        private final String originalState;
        private final LocalDateTime expiresAt;

        public OAuth2State(String state, String clientId, String redirectUri, String scope,
                String originalState, LocalDateTime expiresAt) {
            this.state = state;
            this.clientId = clientId;
            this.redirectUri = redirectUri;
            this.scope = scope;
            this.originalState = originalState;
            this.expiresAt = expiresAt;
        }

        // Getters
        public String getState() {
            return state;
        }

        public String getClientId() {
            return clientId;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public String getScope() {
            return scope;
        }

        public String getOriginalState() {
            return originalState;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }
    }

    public static class OAuth2TokenResponse {
        private final String accessToken;
        private final String tokenType;
        private final int expiresIn;
        private final String refreshToken;
        private final String scope;

        public OAuth2TokenResponse(String accessToken, String tokenType, int expiresIn,
                String refreshToken, String scope) {
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.expiresIn = expiresIn;
            this.refreshToken = refreshToken;
            this.scope = scope;
        }

        // Getters
        public String getAccessToken() {
            return accessToken;
        }

        public String getTokenType() {
            return tokenType;
        }

        public int getExpiresIn() {
            return expiresIn;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public String getScope() {
            return scope;
        }
    }

    public static class OAuth2TokenValidation {
        private final boolean valid;
        private final String userId;
        private final String clientId;
        private final String scope;
        private final String error;

        private OAuth2TokenValidation(boolean valid, String userId, String clientId,
                String scope, String error) {
            this.valid = valid;
            this.userId = userId;
            this.clientId = clientId;
            this.scope = scope;
            this.error = error;
        }

        public static OAuth2TokenValidation valid(String userId, String clientId, String scope) {
            return new OAuth2TokenValidation(true, userId, clientId, scope, null);
        }

        public static OAuth2TokenValidation invalid(String error) {
            return new OAuth2TokenValidation(false, null, null, null, error);
        }

        // Getters
        public boolean isValid() {
            return valid;
        }

        public String getUserId() {
            return userId;
        }

        public String getClientId() {
            return clientId;
        }

        public String getScope() {
            return scope;
        }

        public String getError() {
            return error;
        }
    }
}