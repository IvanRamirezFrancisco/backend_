package com.security.service;

import com.security.entity.User;
import com.security.entity.VerificationToken;
import com.security.enums.TokenType;
import com.security.repository.VerificationTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class VerificationService {

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @Value("${app.security.verification.email.expiration:86400000}")
    private long emailTokenExpirationMs;

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public String generateVerificationToken(User user) {
        // Generate secure random token
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            token.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
        }
        String tokenValue = token.toString();

        // Delete any existing verification tokens for this user
        verificationTokenRepository.deleteByUser(user);

        // Create new verification token
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setToken(tokenValue);
        verificationToken.setUser(user);
        verificationToken.setTokenType(TokenType.EMAIL_VERIFICATION);
        verificationToken.setExpiryDate(LocalDateTime.now().plusSeconds(emailTokenExpirationMs / 1000));
        verificationToken.setUsed(false);

        verificationTokenRepository.save(verificationToken);

        return tokenValue;
    }

    public boolean verifyToken(String token) {
        Optional<VerificationToken> verificationTokenOpt = verificationTokenRepository.findValidToken(token,
                LocalDateTime.now());

        if (verificationTokenOpt.isPresent()) {
            VerificationToken verificationToken = verificationTokenOpt.get();
            User user = verificationToken.getUser();

            // Activate user
            user.setEnabled(true);
            userService.save(user);

            // Mark token as used
            verificationTokenRepository.markTokenAsUsed(verificationToken.getId());

            System.out.println("✅ Usuario verificado: " + user.getEmail());
            return true;
        }

        System.out.println("❌ Token inválido o expirado: " + token);
        return false;
    }

    public void sendVerificationEmail(User user) {
        String token = generateVerificationToken(user);
        emailService.sendVerificationEmail(user, token);
    }

    public void resendVerificationEmail(String email) {
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isEnabled()) {
            throw new RuntimeException("User is already verified");
        }

        sendVerificationEmail(user);
    }

    public void cleanupExpiredTokens() {
        verificationTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        System.out.println("🧹 Tokens expirados eliminados");
    }

    public boolean hasValidVerificationToken(User user) {
        return verificationTokenRepository.countActiveTokensByUserId(user.getId(), LocalDateTime.now()) > 0;
    }
}