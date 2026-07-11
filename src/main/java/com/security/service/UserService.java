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
import org.springframework.dao.DataIntegrityViolationException;
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

    @Autowired
    private AdminHierarchyService adminHierarchyService;

    /**
     * Patrón UPSERT anti-enumeración para registro de usuarios.
     *
     * Tres ramas:
     * 1. Email NO existe → creación normal (enabled=false, enviar verificación).
     * 2. Email existe + enabled=false (ghost) → actualizar datos, regenerar token,
     * reenviar email.
     * 3. Email existe + enabled=true (activo) → retorno silencioso (sin email, sin
     * error).
     *
     * En TODOS los casos la respuesta HTTP es 200 con mensaje genérico idéntico,
     * impidiendo que un atacante determine si el email ya está registrado.
     *
     * @return el User persistido (nuevo o actualizado) — nunca null.
     */
    public User createUser(RegisterRequest registerRequest) {
        Optional<User> existingOpt = userRepository.findByEmail(registerRequest.getEmail());

        if (existingOpt.isPresent()) {
            User existing = existingOpt.get();

            if (Boolean.TRUE.equals(existing.getEnabled())) {
                // ── Rama 3: usuario activo → retorno silencioso ──
                // No enviar email, no lanzar excepción → respuesta idéntica al caso normal.
                logger.info("Registro silencioso (usuario activo): {}", LogSanitizer.maskEmail(existing.getEmail()));
                return existing;
            }

            // ── Rama 2: ghost user (enabled=false) → actualizar datos + reenviar
            // verificación ──
            logger.info("Registro upsert (ghost user): {}", LogSanitizer.maskEmail(existing.getEmail()));

            existing.setUsername(registerRequest.getUsername());
            existing.setFirstName(registerRequest.getFirstName());
            existing.setLastName(registerRequest.getLastName());
            existing.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
            existing.setPhone(registerRequest.getPhone());
            existing.setUpdatedAt(LocalDateTime.now());

            User savedUser;
            try {
                savedUser = userRepository.save(existing);
            } catch (DataIntegrityViolationException ex) {
                // Username duplicado con otro usuario
                logger.warn("Race condition en upsert: {}", LogSanitizer.maskEmail(registerRequest.getEmail()));
                // Retorno silencioso para no revelar información
                return existing;
            }

            // Eliminar tokens anteriores y crear uno nuevo
            verificationTokenRepository.deleteByUser(savedUser);
            createAndSendVerificationToken(savedUser);

            return savedUser;
        }

        // ── Rama 1: usuario nuevo → creación normal ──
        // Validar username duplicado de forma preventiva (la protección real es el
        // unique constraint)
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            // Retorno silencioso para no revelar que el username existe
            logger.info("Registro silencioso (username existente): {}",
                    LogSanitizer.maskEmail(registerRequest.getEmail()));
            // Crear un objeto User dummy para retornar respuesta uniforme
            // No persistimos nada — simplemente devolvemos un user con los datos del
            // request
            User dummy = new User();
            dummy.setId(0L);
            dummy.setEmail(registerRequest.getEmail());
            dummy.setFirstName(registerRequest.getFirstName());
            dummy.setLastName(registerRequest.getLastName());
            dummy.setEnabled(false);
            dummy.setIsCustomer(true);
            return dummy;
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
        user.setIsCustomer(true);

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("User Role not set."));

        user.setRoles(Collections.singleton(userRole));

        // Guardar usuario — protección contra race condition usando unique constraint
        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Race condition: otro hilo insertó el mismo email/username
            logger.warn("Race condition detectada en registro: {}", LogSanitizer.maskEmail(registerRequest.getEmail()));
            // Retorno silencioso — no revelar información
            User dummy = new User();
            dummy.setId(0L);
            dummy.setEmail(registerRequest.getEmail());
            dummy.setFirstName(registerRequest.getFirstName());
            dummy.setLastName(registerRequest.getLastName());
            dummy.setEnabled(false);
            dummy.setIsCustomer(true);
            return dummy;
        }

        // Crear y enviar token de verificación
        createAndSendVerificationToken(savedUser);

        return savedUser;
    }

    /**
     * Genera un token de verificación, lo persiste y envía el email.
     * Método extraído para evitar duplicación entre las ramas del upsert.
     */
    private void createAndSendVerificationToken(User user) {
        String tokenValue = generateVerificationToken();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setToken(tokenValue);
        verificationToken.setUser(user);
        verificationToken.setTokenType(TokenType.EMAIL_VERIFICATION);
        verificationToken.setExpiryDate(LocalDateTime.now().plusHours(24));
        verificationToken.setUsed(false);
        verificationTokenRepository.save(verificationToken);

        try {
            emailService.sendVerificationEmail(user, tokenValue);
            logger.info("Email de verificacion enviado a: {}",
                    LogSanitizer.maskEmail(user.getEmail()));
        } catch (Exception e) {
            logger.error("Error enviando email de verificacion a {}: {}",
                    LogSanitizer.maskEmail(user.getEmail()), e.getMessage());
            // No fallar el registro, solo log del error
        }
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
        assertCanActOn(user, "UPDATE");

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
        assertCanActOn(user, "DELETE");
        userRepository.delete(user);
    }

    public void enableUser(Long userId) {
        User user = getUserById(userId);
        assertCanActOn(user, "ENABLE");
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
        assertCanActOn(user, "DISABLE");
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
        assertCanActOn(user, "PASSWORD");
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
        assertCanActOn(user, "UPDATE");
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret(secret);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void disableTwoFactor(Long userId) {
        User user = getUserById(userId);
        assertCanActOn(user, "RESET_2FA");
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
        userResponse.setProtectedOwner(adminHierarchyService.isProtectedOwner(user));
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

        // Nombres de roles (ej. ["ROLE_VR_DASHBOARD", "ROLE_ADMIN"])
        Set<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toSet());
        userResponse.setRoles(roleNames);

        // Permisos granulares expandidos de TODOS los roles (ej. ["DASHBOARD_VIEW",
        // "PRODUCT_READ"])
        Set<String> permissionNames = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getName())
                .collect(Collectors.toSet());
        userResponse.setPermissions(permissionNames);

        // Flag de tipo de usuario: true = cliente, false = empleado/staff
        userResponse.setIsCustomer(user.getIsCustomer());

        return userResponse;
    }

    private void assertCanActOn(User targetUser, String action) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getName()) || "SYSTEM".equals(auth.getName())) {
            return; // Internal call
        }
        
        String email = auth.getName();
        User actor = userRepository.findByEmail(email).orElse(null);
        if (actor != null) {
            if ("DELETE".equals(action)) {
                adminHierarchyService.assertCanDeleteUser(actor, targetUser);
            } else if ("DISABLE".equals(action)) {
                adminHierarchyService.assertCanDisableUser(actor, targetUser);
            } else if ("RESET_2FA".equals(action)) {
                adminHierarchyService.assertCanResetTwoFactor(actor, targetUser);
            } else if ("PASSWORD".equals(action)) {
                adminHierarchyService.assertCanChangePasswordAdmin(actor, targetUser);
            } else {
                adminHierarchyService.assertCanManageUser(actor, targetUser);
            }
        }
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