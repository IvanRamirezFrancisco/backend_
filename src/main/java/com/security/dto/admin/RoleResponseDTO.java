package com.security.dto.admin;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO de respuesta completo para un rol con sus permisos
 */
public class RoleResponseDTO {

    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Lista de permisos asignados al rol
     */
    private Set<PermissionDTO> permissions;

    /**
     * Cantidad de usuarios que tienen este rol asignado
     */
    private Long userCount;

    /**
     * Indica si el rol es inmutable (rol base del sistema: ROLE_SUPER_ADMIN,
     * ROLE_ADMIN, ROLE_USER). Los roles inmutables no pueden ser eliminados
     * ni tener sus permisos modificados.
     */
    private boolean immutable;

    // ==================== Constructors ====================

    public RoleResponseDTO() {
    }

    public RoleResponseDTO(Long id, String name, Set<PermissionDTO> permissions) {
        this.id = id;
        this.name = name;
        this.permissions = permissions;
    }

    // ==================== Getters and Setters ====================

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public Set<PermissionDTO> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<PermissionDTO> permissions) {
        this.permissions = permissions;
    }

    public Long getUserCount() {
        return userCount;
    }

    public void setUserCount(Long userCount) {
        this.userCount = userCount;
    }

    public boolean isImmutable() {
        return immutable;
    }

    public void setImmutable(boolean immutable) {
        this.immutable = immutable;
    }
}
