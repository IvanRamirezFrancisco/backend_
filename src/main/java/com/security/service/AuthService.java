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

            // CORREGIDO: Usar enum name()
            Set<String> roles = user.getRoles().stream()
                    .map(role -> role.getName().name())
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
        userResponse.setFirstName(user.getFirstName());
        userResponse.setLastName(user.getLastName());
        userResponse.setEmail(user.getEmail());
        userResponse.setPhone(user.getPhone());
        userResponse.setEnabled(user.getEnabled());
        userResponse.setTwoFactorEnabled(user.getTwoFactorEnabled());
        userResponse.setCreatedAt(user.getCreatedAt());
        userResponse.setUpdatedAt(user.getUpdatedAt());

        // CORREGIDO: Usar enum name()
        Set<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet());
        userResponse.setRoles(roleNames);

        userResponse.setGoogleAuthEnabled(user.getGoogleAuthEnabled());
        userResponse.setSmsEnabled(user.getSmsEnabled());
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

    public String generateGoogleAuthSecret(String email) {
        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        return key.getKey();
    }

    public String generateGoogleAuthQrUrl(String email, String secret) {
        String issuer = "AuthSystem";
        String otpAuthUrl = String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s",
                issuer, email, secret, issuer);
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(otpAuthUrl, BarcodeFormat.QR_CODE, 200, 200);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
        } catch (WriterException | java.io.IOException e) {
            throw new RuntimeException("Error generando QR", e);
        }
    }

    public void saveGoogleAuthSecret(Long userId, String secret) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setGoogleAuthSecret(secret);
            userRepository.save(user);
        }
    }

    public boolean verifyGoogleAuthCode(Long userId, String code) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getGoogleAuthSecret() == null)
            return false;
        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        return gAuth.authorize(user.getGoogleAuthSecret(), Integer.parseInt(code));
    }

    public void enableGoogleAuthForUser(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setGoogleAuthEnabled(true); // Debes tener este campo en la entidad User
            userRepository.save(user);
        }
    }

}