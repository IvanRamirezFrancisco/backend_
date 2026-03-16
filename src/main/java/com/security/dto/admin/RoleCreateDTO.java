package com.security.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * DTO para crear un nuevo rol con permisos asignados
 */
public class RoleCreateDTO {

    @NotBlank(message = "Role name is required")
    @Size(min = 3, max = 50, message = "Role name must be between 3 and 50 characters")
    @Pattern(regexp = "^ROLE_[A-Z_]+$", message = "Role name must start with 'ROLE_' and contain only uppercase letters and underscores")
    private String name;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    /**
     * IDs de los permisos a asignar al nuevo rol
     * Debe contener al menos un permiso
     */
    @NotEmpty(message = "At least one permission must be assigned to the role")
    private Set<Long> permissionIds;

    // ==================== Constructors ====================

    public RoleCreateDTO() {
    }

    public RoleCreateDTO(String name, Set<Long> permissionIds) {
        this.name = name;
        this.permissionIds = permissionIds;
    }

    // ==================== Getters and Setters ====================

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

    public Set<Long> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(Set<Long> permissionIds) {
        this.permissionIds = permissionIds;
    }
}
