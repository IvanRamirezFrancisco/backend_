package com.security.dto.admin;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO de respuesta completo con todos los detalles del usuario Staff
 * Se usa para operaciones de detalle individual
 */
public class AdminUserResponseDTO {

    private Long id;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String phone;
    private Boolean enabled;
    private Boolean accountNonLocked;
    private Boolean accountNonExpired;
    private Boolean credentialsNonExpired;
    private Boolean twoFactorEnabled;
    private Boolean googleAuthEnabled;
    private Boolean emailEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Roles asignados al usuario (solo nombres)
     */
    private Set<String> roles;

    /**
     * Información detallada de roles con permisos
     */
    private Set<RoleDTO> rolesDetail;

    // ==================== Constructors ====================

    public AdminUserResponseDTO() {
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public Boolean getAccountNonExpired() {
        return accountNonExpired;
    }

    public void setAccountNonExpired(Boolean accountNonExpired) {
        this.accountNonExpired = accountNonExpired;
    }

    public Boolean getCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    public void setCredentialsNonExpired(Boolean credentialsNonExpired) {
        this.credentialsNonExpired = credentialsNonExpired;
    }

    public Boolean getTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public void setTwoFactorEnabled(Boolean twoFactorEnabled) {
        this.twoFactorEnabled = twoFactorEnabled;
    }

    public Boolean getGoogleAuthEnabled() {
        return googleAuthEnabled;
    }

    public void setGoogleAuthEnabled(Boolean googleAuthEnabled) {
        this.googleAuthEnabled = googleAuthEnabled;
    }

    public Boolean getEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(Boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public Set<RoleDTO> getRolesDetail() {
        return rolesDetail;
    }

    public void setRolesDetail(Set<RoleDTO> rolesDetail) {
        this.rolesDetail = rolesDetail;
    }

    // ==================== Nested DTO ====================

    /**
     * DTO simplificado de Role para incluir en la respuesta
     */
    public static class RoleDTO {
        private Long id;
        private String name;
        private Set<String> permissions;

        public RoleDTO() {
        }

        public RoleDTO(Long id, String name, Set<String> permissions) {
            this.id = id;
            this.name = name;
            this.permissions = permissions;
        }

        // Getters and Setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Set<String> getPermissions() {
            return permissions;
        }

        public void setPermissions(Set<String> permissions) {
            this.permissions = permissions;
        }
    }
}
