package com.security.dto.request;

import jakarta.validation.constraints.NotBlank;

public class VerifyEmailRequest {

    @NotBlank(message = "Token is required")
    private String token;

    // Constructor vacío
    public VerifyEmailRequest() {
    }

    // Constructor con parámetros
    public VerifyEmailRequest(String token) {
        this.token = token;
    }

    // Getter
    public String getToken() {
        return token;
    }

    // Setter
    public void setToken(String token) {
        this.token = token;
    }
}