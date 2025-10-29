package com.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SmsSetupRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+[1-9]\\d{9,14}$", message = "Phone number must be in international format (+1234567890)")
    private String phoneNumber;

    // Constructors
    public SmsSetupRequest() {
    }

    public SmsSetupRequest(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    // Getters and Setters
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}