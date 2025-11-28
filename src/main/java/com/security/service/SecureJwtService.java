package com.security.service;

import com.security.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Servicio de JWT con invalidación usando almacenamiento en memoria
 */
@Service
public class SecureJwtService {

    private static final Logger logger = LoggerFactory.getLogger(SecureJwtService.class);

    @Autowired
    private LoginSecurityService loginSecurityService;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.jwt.access-token-expiration}")
    private long accessTokenExpirationMs;

    @Value("${app.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    @Value("${app.security.jwt.issuer}")
    private String issuer;

    // Almacenamiento en memoria para tokens invalidados
    private final Map<String, LocalDateTime> blacklistedTokens = new ConcurrentHashMap<>();
    private final Map<String, RefreshTokenInfo> refreshTokens = new ConcurrentHashMap<>();

    // Clase para información de refresh tokens
    private static class RefreshTokenInfo {
        String userId;
        LocalDateTime expiresAt;
        String deviceInfo;

        RefreshTokenInfo(String userId, LocalDateTime expiresAt, String deviceInfo) {
            this.userId = userId;
            this.expiresAt = expiresAt;
            this.deviceInfo = deviceInfo;
        }
    }

    /**
     * Genera un token de acceso
     */
    public String generateAccessToken(Authentication authentication, String deviceInfo) {
        try {
            User user = (User) authentication.getPrincipal();

            Date issuedAt = new Date();
            Date expiresAt = new Date(issuedAt.getTime() + accessTokenExpirationMs);

            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", user.getId().toString());
            claims.put("email", user.getEmail());
            claims.put("roles", authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));
            claims.put("deviceInfo",
                    deviceInfo != null ? deviceInfo.substring(0, Math.min(100, deviceInfo.length())) : "unknown");
            claims.put("tokenType", "access");

            String token = Jwts.builder()
                    .setClaims(claims)
                    .setSubject(user.getEmail())
                    .setIssuer(issuer)
                    .setIssuedAt(issuedAt)
                    .setExpiration(expiresAt)
                    .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                    .compact();

            logger.debug("Access token generated for user: {}", user.getEmail());
            return token;

        } catch (Exception e) {
            logger.error("Error generating access token: {}", e.getMessage());
            throw new RuntimeException("Error generating access token", e);
        }
    }

    /**
     * Genera un refresh token
     */
    public String generateRefreshToken(String userEmail, String deviceInfo) {
        try {
            Date issuedAt = new Date();
            Date expiresAt = new Date(issuedAt.getTime() + refreshTokenExpirationMs);

            Map<String, Object> claims = new HashMap<>();
            claims.put("tokenType", "refresh");
            claims.put("deviceInfo",
                    deviceInfo != null ? deviceInfo.substring(0, Math.min(100, deviceInfo.length())) : "unknown");

            String token = Jwts.builder()
                    .setClaims(claims)
                    .setSubject(userEmail)
                    .setIssuer(issuer)
                    .setIssuedAt(issuedAt)
                    .setExpiration(expiresAt)
                    .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                    .compact();

            // Almacenar información del refresh token
            RefreshTokenInfo tokenInfo = new RefreshTokenInfo(
                    userEmail,
                    LocalDateTime.ofInstant(expiresAt.toInstant(), ZoneId.systemDefault()),
                    deviceInfo);
            refreshTokens.put(token, tokenInfo);

            logger.debug("Refresh token generated for user: {}", userEmail);
            return token;

        } catch (Exception e) {
            logger.error("Error generating refresh token: {}", e.getMessage());
            throw new RuntimeException("Error generating refresh token", e);
        }
    }

    /**
     * Valida un token JWT
     */
    public boolean validateToken(String token) {
        try {
            // Verificar si está en la blacklist
            if (isTokenBlacklisted(token)) {
                logger.warn("Token is blacklisted");
                return false;
            }

            // Parsear y validar
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .requireIssuer(issuer)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Verificar si el token está globalmente invalidado
            String userId = claims.get("userId", String.class);
            if (userId != null) {
                Date issuedAt = claims.getIssuedAt();
                LocalDateTime tokenIssuedAt = LocalDateTime.ofInstant(issuedAt.toInstant(), ZoneId.systemDefault());

                if (loginSecurityService.isTokenGloballyInvalidated(userId, tokenIssuedAt)) {
                    logger.warn("Token globally invalidated for user: {}", userId);
                    return false;
                }
            }

            return true;

        } catch (ExpiredJwtException e) {
            logger.warn("JWT token is expired");
            // Limpiar token expirado de la blacklist
            cleanupExpiredToken(token);
            return false;
        } catch (UnsupportedJwtException e) {
            logger.warn("JWT token is unsupported");
            return false;
        } catch (MalformedJwtException e) {
            logger.warn("JWT token is malformed");
            return false;
        } catch (SecurityException e) {
            logger.warn("JWT signature validation failed");
            return false;
        } catch (IllegalArgumentException e) {
            logger.warn("JWT token compact is illegal");
            return false;
        } catch (Exception e) {
            logger.error("Error validating token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene claims de un token válido
     */
    public Claims getClaimsFromToken(String token) {
        try {
            if (!validateToken(token)) {
                return null;
            }

            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

        } catch (Exception e) {
            logger.error("Error getting claims from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene el email del usuario del token
     */
    public String getUserEmailFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * Obtiene el ID del usuario del token
     */
    public String getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.get("userId", String.class) : null;
    }

    /**
     * Invalida un token específico
     */
    public void invalidateToken(String token) {
        try {
            // Obtener fecha de expiración para determinar cuánto tiempo mantener en
            // blacklist
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Date expiration = claims.getExpiration();
            LocalDateTime expirationTime = LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault());

            blacklistedTokens.put(token, expirationTime);

            logger.info("Token invalidated");

        } catch (Exception e) {
            logger.warn("Error invalidating token, adding to blacklist anyway: {}", e.getMessage());
            // Agregar con expiración por defecto
            blacklistedTokens.put(token, LocalDateTime.now().plusHours(24));
        }
    }

    /**
     * Invalida un refresh token
     */
    public void invalidateRefreshToken(String refreshToken) {
        try {
            refreshTokens.remove(refreshToken);
            logger.info("Refresh token invalidated");

        } catch (Exception e) {
            logger.error("Error invalidating refresh token: {}", e.getMessage());
        }
    }

    /**
     * Verifica si un token está en la blacklist
     */
    private boolean isTokenBlacklisted(String token) {
        LocalDateTime expiration = blacklistedTokens.get(token);
        if (expiration == null) {
            return false;
        }

        // Si ya expiró, limpiar de la blacklist
        if (LocalDateTime.now().isAfter(expiration)) {
            blacklistedTokens.remove(token);
            return false;
        }

        return true;
    }

    /**
     * Renueva un access token usando un refresh token
     */
    public String renewAccessToken(String refreshToken, String deviceInfo) {
        try {
            // Validar refresh token
            if (!isValidRefreshToken(refreshToken)) {
                throw new RuntimeException("Invalid refresh token");
            }

            // Obtener información del refresh token
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(refreshToken)
                    .getBody();

            String userEmail = claims.getSubject();

            // Crear nuevo access token (necesitarías obtener el User y Authentication)
            // Por simplicidad, usando claims básicos
            Date issuedAt = new Date();
            Date expiresAt = new Date(issuedAt.getTime() + accessTokenExpirationMs);

            Map<String, Object> newClaims = new HashMap<>();
            newClaims.put("email", userEmail);
            newClaims.put("deviceInfo",
                    deviceInfo != null ? deviceInfo.substring(0, Math.min(100, deviceInfo.length())) : "unknown");
            newClaims.put("tokenType", "access");

            String newToken = Jwts.builder()
                    .setClaims(newClaims)
                    .setSubject(userEmail)
                    .setIssuer(issuer)
                    .setIssuedAt(issuedAt)
                    .setExpiration(expiresAt)
                    .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                    .compact();

            logger.debug("Access token renewed for user: {}", userEmail);
            return newToken;

        } catch (Exception e) {
            logger.error("Error renewing access token: {}", e.getMessage());
            throw new RuntimeException("Error renewing access token", e);
        }
    }

    /**
     * Verifica si un refresh token es válido
     */
    public boolean isValidRefreshToken(String refreshToken) {
        try {
            RefreshTokenInfo tokenInfo = refreshTokens.get(refreshToken);
            if (tokenInfo == null) {
                return false;
            }

            // Verificar expiración
            if (LocalDateTime.now().isAfter(tokenInfo.expiresAt)) {
                refreshTokens.remove(refreshToken);
                return false;
            }

            // Validar firma y estructura
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .requireIssuer(issuer)
                    .build()
                    .parseClaimsJws(refreshToken);

            return true;

        } catch (Exception e) {
            logger.warn("Invalid refresh token: {}", e.getMessage());
            refreshTokens.remove(refreshToken);
            return false;
        }
    }

    /**
     * Limpia tokens expirados
     */
    private void cleanupExpiredToken(String token) {
        blacklistedTokens.remove(token);
    }

    /**
     * Limpieza automática de tokens y datos antiguos
     */
    public void cleanupExpiredData() {
        try {
            LocalDateTime now = LocalDateTime.now();

            // Limpiar tokens blacklisted expirados
            blacklistedTokens.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));

            // Limpiar refresh tokens expirados
            refreshTokens.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt));

            logger.debug("Expired JWT data cleanup completed");

        } catch (Exception e) {
            logger.error("Error during JWT cleanup: {}", e.getMessage());
        }
    }

    /**
     * Obtiene la clave de firma
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Extrae el token del header Authorization
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * Verifica si el token está próximo a expirar (últimos 5 minutos)
     */
    public boolean isTokenNearExpiry(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            if (claims == null) {
                return true;
            }

            Date expiration = claims.getExpiration();
            Date fiveMinutesFromNow = new Date(System.currentTimeMillis() + 5 * 60 * 1000);

            return expiration.before(fiveMinutesFromNow);

        } catch (Exception e) {
            logger.error("Error checking token expiry: {}", e.getMessage());
            return true;
        }
    }
}