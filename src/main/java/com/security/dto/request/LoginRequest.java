package com.security.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.security.validation.SafeInput;

public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @SafeInput(type = SafeInput.SanitizationType.EMAIL, message = "Email format is invalid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(max = 255, message = "Password too long")
    private String password;

    @Size(max = 10, message = "Two factor token is too long")
    @SafeInput(type = SafeInput.SanitizationType.TEXT, message = "Invalid two factor token format")
    private String twoFactorToken;

    // Constructors
    public LoginRequest() {
    }

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    // Getters and Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTwoFactorToken() {
        return twoFactorToken;
    }

    public void setTwoFactorToken(String twoFactorToken) {
        this.twoFactorToken = twoFactorToken;
    }
}
