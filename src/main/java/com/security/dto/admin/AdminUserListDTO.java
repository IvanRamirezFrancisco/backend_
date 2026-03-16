package com.security.dto.admin;

import java.time.LocalDateTime;

/**
 * DTO simplificado para listados paginados de usuarios Staff
 * Contiene solo información esencial para mejorar performance
 */
public class AdminUserListDTO {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Boolean enabled;
    private Boolean accountNonLocked;
    private String roles; // Roles concatenados (ej: "ROLE_ADMIN, ROLE_MODERATOR")
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin; // Fecha del último login exitoso

    // ==================== Constructors ====================

    public AdminUserListDTO() {
    }

    public AdminUserListDTO(Long id, String firstName, String lastName, String email,
            Boolean enabled, Boolean accountNonLocked, String roles,
            LocalDateTime createdAt) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.roles = roles;
        this.createdAt = createdAt;
    }

    // ==================== Getters and Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getAccountNonLocked() {
        return accountNonLocked;
    }

    public void setAccountNonLocked(Boolean accountNonLocked) {
        this.accountNonLocked = accountNonLocked;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    // ==================== Helper Methods ====================

    /**
     * Retorna el nombre completo del usuario
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }
}
