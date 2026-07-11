package com.security.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.security.dto.request.LoginRequest;
import com.security.dto.request.RegisterRequest;
import com.security.dto.response.JwtAuthResponse;
import com.security.dto.response.UserResponse;
import com.security.entity.Role;
import com.security.entity.User;
import com.security.exception.BadRequestException;
import com.security.repository.UserRepository;
import com.security.security.JwtTokenProvider;
import com.security.security.UserPrincipal;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Optional;

@Service
@Transactional
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private com.security.service.AdminHierarchyService adminHierarchyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationService verificationService; // ← AÑADIR esta inyección

    public UserResponse registerUser(RegisterRequest registerRequest) {
        User user = userService.createUser(registerRequest);

        // Enviar email de verificación
        // verificationService.sendVerificationEmail(user);

        return convertToUserResponse(user);
    }

    public JwtAuthResponse authenticateUser(LoginRequest loginRequest) {
        // First authenticate with email and password
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()));

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userService.getUserById(userPrincipal.getId());

        // Check if user is enabled
        if (!user.getEnabled()) {
            throw new BadRequestException("User account is not verified. Please check your email.");
        }

        // Check if 2FA is enabled
        if (user.getTwoFactorEnabled()) {
            if (loginRequest.getTwoFactorToken() == null || loginRequest.getTwoFactorToken().isEmpty()) {
                // Return response indicating 2FA is required
                JwtAuthResponse response = new JwtAuthResponse();
                response.setTwoFactorRequired(true);
                response.setUser(convertToUserResponse(user));
                return response;
            }

            // Verify 2FA token
            if (!twoFactorService.verifyToken(user.getId(), loginRequest.getTwoFactorToken())) {
                throw new BadRequestException("Invalid two-factor authentication token.");
            }
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        UserResponse userResponse = convertToUserResponse(user);

        return new JwtAuthResponse(jwt, tokenProvider.getExpirationTime(), userResponse);
    }

    public JwtAuthResponse refreshToken(String token) {
        if (tokenProvider.validateToken(token)) {
            Long userId = tokenProvider.getUserIdFromJWT(token);
            User user = userService.getUserById(userId);

            // Obtener nombres de roles (String directamente)
            Set<String> roles = user.getRoles().stream()
                    .map(role -> role.getName())
                    .collect(Collectors.toSet());

            String newToken = tokenProvider.generateTokenFromUserId(userId, user.getEmail(), roles);
            UserResponse userResponse = convertToUserResponse(user);

            return new JwtAuthResponse(newToken, tokenProvider.getExpirationTime(), userResponse);
        }

        throw new BadRequestException("Invalid refresh token");
    }

    public void verifyEmail(String token) {

        boolean verified = verificationService.verifyToken(token);
        if (!verified) {
            throw new BadRequestException("Invalid or expired verification token");
        }
    }

    public void resetPassword(String email) {
        // TODO: Implement password reset logic
        // passwordResetService.sendPasswordResetEmail(email);
    }

    public void confirmPasswordReset(String token, String newPassword) {
        // TODO: Implement password reset confirmation
        // passwordResetService.resetPassword(token, newPassword);
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

        userResponse.setGoogleAuthEnabled(user.getGoogleAuthEnabled());
        userResponse.setEmailEnabled(user.getEmailEnabled());

        return userResponse;
    }

    public UserResponse getUserFromToken(String token) {
        Long userId = tokenProvider.getUserIdFromJWT(token);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return null;
        return convertToUserResponse(user);
    }

    // ===== MÉTODOS DE GOOGLE AUTHENTICATOR REMOVIDOS =====
    // ESTOS MÉTODOS SE MOVIERON A TotpService PARA EVITAR DUPLICACIÓN
    // Y CONFLICTOS EN EL MANEJO DEL SECRET.
    //
    // AHORA TODA LA LÓGICA DE TOTP ESTÁ CENTRALIZADA EN:
    // - TotpService: Generación, verificación y QR codes
    // - TwoFactorService: Orquestación y persistencia
    //
    // AuthService se enfoca únicamente en autenticación básica (login/register)

}