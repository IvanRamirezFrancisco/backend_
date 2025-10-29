package com.security.service;

import com.security.dto.request.RegisterRequest;
import com.security.dto.response.UserResponse;
import com.security.entity.Role;
import com.security.entity.User;
///agregue
import com.security.entity.VerificationToken;

import com.security.enums.RoleName;
//
import com.security.enums.TokenType;

import com.security.exception.ResourceNotFoundException;
import com.security.exception.BadRequestException;
import com.security.repository.RoleRepository;
import com.security.repository.UserRepository;
//esto
import com.security.repository.VerificationTokenRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

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

    public User createUser(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new BadRequestException("Email address already in use!");
        }

        User user = new User();
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setPhone(registerRequest.getPhone());
        user.setEnabled(false); // Will be enabled after email verification
        user.setTwoFactorEnabled(false);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
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

            System.out.println("✅ Email de verificación enviado a: " + savedUser.getEmail());
            /*
             * System.out.println("🔥 DEBUG: Email enviado desde UserService a: " +
             * savedUser.getEmail());
             * System.out.println("🔥 DEBUG: Token generado: " + tokenValue);
             */
        } catch (Exception e) {
            System.err.println("❌ Error enviando email de verificación: " + e.getMessage());
            // No fallar el registro, solo log del error
            e.printStackTrace();
        }

        return savedUser;
    }

    public boolean verifyEmailToken(String token) {
        Optional<VerificationToken> verificationTokenOpt = verificationTokenRepository
                .findValidToken(token, LocalDateTime.now());

        if (verificationTokenOpt.isEmpty()) {
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
            System.out.println("✅ Email de verificación reenviado a: " + user.getEmail());
        } catch (Exception e) {
            System.err.println("❌ Error reenviando email: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error enviando email de verificación");
        }
    }

    private String generateVerificationToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
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
    }

    public void disableUser(Long userId) {
        User user = getUserById(userId);
        user.setEnabled(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void changePassword(Long userId, String newPassword) {
        User user = getUserById(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setCredentialsNonExpired(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
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

    // Método para buscar por email (lo necesita VerificationService)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
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
        userResponse.setSmsEnabled(user.getSmsEnabled());
        userResponse.setTwoFactorEnabled(user.getTwoFactorEnabled());
        userResponse.setCreatedAt(user.getCreatedAt());
        userResponse.setUpdatedAt(user.getUpdatedAt());

        // CORREGIDO: Usar enum name()
        Set<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet());
        userResponse.setRoles(roleNames);

        // <-- AGREGA ESTA LÍNEA
        userResponse.setGoogleAuthEnabled(user.getGoogleAuthEnabled());

        return userResponse;
    }
}