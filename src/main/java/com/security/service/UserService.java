package com.security.service;

import com.security.dto.request.RegisterRequest;
import com.security.dto.response.UserResponse;
import com.security.entity.Role;
import com.security.entity.User;
import com.security.entity.VerificationToken;
import com.security.enums.TokenType;
import com.security.exception.ResourceNotFoundException;
import com.security.exception.BadRequestException;
import com.security.repository.RoleRepository;
import com.security.repository.UserRepository;
import com.security.repository.VerificationTokenRepository;
import com.security.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;
    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private AuditLogService auditLogService;

    public User createUser(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new BadRequestException("Email address already in use!");
        }

        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new BadRequestException("Username already in use!");
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setPhone(registerRequest.getPhone());
        user.setEnabled(false); // Se habilitará tras verificar el email
        user.setTwoFactorEnabled(false);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        // CRÍTICO: Los usuarios registrados públicamente son CLIENTES, no Staff.
        // Sin esta línea el valor por defecto de la entidad es false → aparecerían como
        // Staff.
        user.setIsCustomer(true);

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("User Role not set."));

        user.setRoles(Collections.singleton(userRole));

        // Guardar usuario primero
        User savedUser = userRepository.save(user);

        // Crear y guardar token de verificación
        String tokenValue = generateVerificationToken();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setToken(tokenValue);
        verificationToken.setUser(savedUser);
        verificationToken.setTokenType(TokenType.EMAIL_VERIFICATION); // 🔴 USAR ENUM
        verificationToken.setExpiryDate(LocalDateTime.now().plusHours(24));
        verificationToken.setUsed(false); // 🔴 IMPORTANTE
        verificationTokenRepository.save(verificationToken);

        // Enviar email de verificación
        try {
            emailService.sendVerificationEmail(savedUser, tokenValue);
            logger.info("Email de verificacion enviado a: {}",
                    LogSanitizer.maskEmail(savedUser.getEmail()));
        } catch (Exception e) {
            logger.error("Error enviando email de verificacion a {}: {}",
                    LogSanitizer.maskEmail(savedUser.getEmail()), e.getMessage());
            // No fallar el registro, solo log del error
        }

        return savedUser;
    }

    public boolean verifyEmailToken(String token) {
        logger.debug("Verificando token de email");

        Optional<VerificationToken> verificationTokenOpt = verificationTokenRepository
                .findValidToken(token, LocalDateTime.now());

        if (verificationTokenOpt.isEmpty()) {
            logger.warn("Token de verificacion invalido o expirado");
            throw new BadRequestException("Token de verificación inválido o expirado");
        }

        VerificationToken verificationToken = verificationTokenOpt.get();
        User user = verificationToken.getUser();

        // Activar usuario
        user.setEnabled(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Marcar token como usado
        verificationTokenRepository.markTokenAsUsed(verificationToken.getId());

        logger.info("Verificacion completada para: {}", LogSanitizer.maskEmail(user.getEmail()));

        return true;
    }

    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (user.getEnabled()) {
            throw new BadRequestException("El usuario ya está verificado");
        }

        // Eliminar tokens anteriores del usuario
        verificationTokenRepository.deleteByUser(user);

        // Crear nuevo token
        String tokenValue = generateVerificationToken();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setToken(tokenValue);
        verificationToken.setUser(user);
        verificationToken.setTokenType(TokenType.EMAIL_VERIFICATION);
        verificationToken.setExpiryDate(LocalDateTime.now().plusHours(24));
        verificationToken.setUsed(false);
        verificationTokenRepository.save(verificationToken);

        // Enviar email
        try {
            emailService.sendVerificationEmail(user, tokenValue);
            logger.info("Email de verificacion reenviado a: {}",
                    LogSanitizer.maskEmail(user.getEmail()));
        } catch (Exception e) {
            logger.error("Error reenviando email a {}: {}",
                    LogSanitizer.maskEmail(user.getEmail()), e.getMessage());
            throw new RuntimeException("Error enviando email de verificación");
        }
    }

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    /** Alfabeto alfanumérico para generación de tokens de verificación */
    private static final String ALPHANUM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * Genera un token de verificacion de 32 caracteres alfanumericos aleatorios.
     * UUID.randomUUID().substring(0,8) es inseguro (solo 4 bytes de entropía).
     */
    private String generateVerificationToken() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(ALPHANUM_CHARS.charAt(TOKEN_RANDOM.nextInt(ALPHANUM_CHARS.length())));
        }
        return sb.toString();
    }

    ///////////////////////

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    public UserResponse getUserResponseById(Long userId) {
        User user = getUserById(userId);
        return convertToUserResponse(user);
    }

    public Page<UserResponse> getAllUsers(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);
        return users.map(this::convertToUserResponse);
    }

    public User updateUser(Long userId, User updatedUser) {
        User user = getUserById(userId);

        if (updatedUser.getFirstName() != null) {
            user.setFirstName(updatedUser.getFirstName());
        }
        if (updatedUser.getLastName() != null) {
            user.setLastName(updatedUser.getLastName());
        }
        if (updatedUser.getPhone() != null) {
            user.setPhone(updatedUser.getPhone());
        }

        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public void deleteUser(Long userId) {
        User user = getUserById(userId);
        userRepository.delete(user);
    }

    public void enableUser(Long userId) {
        User user = getUserById(userId);
        user.setEnabled(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        try {
            auditLogService.log("UPDATE", "USER_ENABLE", "USER", userId,
                    null, null, "INFO", true);
        } catch (Exception auditEx) {
            logger.warn("⚠️ No se pudo registrar audit log para habilitación de usuario {}: {}",
                    userId, auditEx.getMessage());
        }
    }

    public void disableUser(Long userId) {
        User user = getUserById(userId);
        user.setEnabled(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        try {
            auditLogService.log("UPDATE", "USER_DISABLE", "USER", userId,
                    null, null, "WARNING", true);
        } catch (Exception auditEx) {
            logger.warn("⚠️ No se pudo registrar audit log para deshabilitación de usuario {}: {}",
                    userId, auditEx.getMessage());
        }
    }

    public void changePassword(Long userId, String newPassword) {
        User user = getUserById(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setCredentialsNonExpired(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        try {
            auditLogService.log("UPDATE", "USER_PASSWORD_CHANGE", "USER", userId,
                    null, null, "WARNING", true);
        } catch (Exception auditEx) {
            logger.warn("⚠️ No se pudo registrar audit log para cambio de contraseña de usuario {}: {}",
                    userId, auditEx.getMessage());
        }
    }

    public void enableTwoFactor(Long userId, String secret) {
        User user = getUserById(userId);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret(secret);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void disableTwoFactor(Long userId) {
        User user = getUserById(userId);
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    // Método para buscar por email (lo necesita VerificationService)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    // Método para guardar usuario (lo necesita VerificationService)
    public User save(User user) {
        return userRepository.save(user);
    }

    public UserResponse convertToUserResponse(User user) {
        UserResponse userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setFirstName(user.getFirstName());
        userResponse.setLastName(user.getLastName());
        userResponse.setEmail(user.getEmail());
        userResponse.setPhone(user.getPhone());
        userResponse.setEnabled(user.getEnabled());
        userResponse.setGoogleAuthEnabled(user.getGoogleAuthEnabled());
        userResponse.setEmailEnabled(user.getEmailEnabled());
        userResponse.setBackupCodesEnabled(user.getBackupCodesEnabled());
        userResponse.setTwoFactorEnabled(user.getTwoFactorEnabled());
        userResponse.setCreatedAt(user.getCreatedAt());
        userResponse.setUpdatedAt(user.getUpdatedAt());

        // Obtener nombres de roles (ahora es String directamente)
        Set<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toSet());
        userResponse.setRoles(roleNames);

        // <-- AGREGA ESTA LÍNEA
        userResponse.setGoogleAuthEnabled(user.getGoogleAuthEnabled());

        return userResponse;
    }

    // ===== MÉTODOS PARA ADMINISTRACIÓN =====

    /**
     * Obtener total de usuarios
     */
    public long getTotalUsersCount() {
        return userRepository.count();
    }

    /**
     * Obtener usuarios activos (habilitados)
     */
    public long getActiveUsersCount() {
        return userRepository.countByEnabledTrue();
    }

    /**
     * Obtener usuarios verificados
     */
    public long getVerifiedUsersCount() {
        return userRepository.countByEnabledTrue(); // Enabled implica verificado
    }

    /**
     * Obtener usuarios con MFA habilitado
     */
    public long getUsersWithMFACount() {
        return userRepository.countByTwoFactorEnabledTrue();
    }

    /**
     * Obtener todos los usuarios paginados
     */
    public Page<User> getAllUsersPaginated(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

}