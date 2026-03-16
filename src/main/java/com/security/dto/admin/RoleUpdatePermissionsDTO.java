package com.security.dto.admin;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/**
 * DTO para actualizar los permisos de un rol existente
 */
public class RoleUpdatePermissionsDTO {

    /**
     * Nuevos IDs de permisos que reemplazarán los actuales
     * Debe contener al menos un permiso
     */
    @NotEmpty(message = "At least one permission must be assigned to the role")
    private Set<Long> permissionIds;

    // ==================== Constructors ====================

    public RoleUpdatePermissionsDTO() {
    }

    public RoleUpdatePermissionsDTO(Set<Long> permissionIds) {
        this.permissionIds = permissionIds;
    }

    // ==================== Getters and Setters ====================

    public Set<Long> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(Set<Long> permissionIds) {
        this.permissionIds = permissionIds;
    }
}
