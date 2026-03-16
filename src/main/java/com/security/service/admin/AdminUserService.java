package com.security.service.admin;

import com.security.dto.admin.*;
import com.security.entity.Role;
import com.security.entity.User;
import com.security.exception.ResourceNotFoundException;
import com.security.exception.SecurityViolationException;
import com.security.repository.RoleRepository;
import com.security.repository.UserRepository;
import com.security.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio para gestión de usuarios Staff (is_customer = false)
 * Implementa RBAC con validaciones de seguridad Enterprise
 */
@Service
public class AdminUserService {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogService auditLogService;

    /**
     * Obtener todos los usuarios Staff con paginación
     */
    @Transactional(readOnly = true)
    public Page<AdminUserListDTO> getAllStaff(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<User> usersPage = userRepository.findAllStaff(pageable);

        return usersPage.map(this::convertToListDTO);
    }

    /**
     * Buscar usuarios Staff con filtros
     */
    @Transactional(readOnly = true)
    public Page<AdminUserListDTO> searchStaff(String searchTerm, Boolean enabled, Boolean accountNonLocked,
            int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        String search = (searchTerm != null && !searchTerm.trim().isEmpty()) ? searchTerm.trim() : "";

        Page<User> usersPage = userRepository.findStaffWithFilters(search, enabled, accountNonLocked, pageable);

        return usersPage.map(this::convertToListDTO);
    }

    /**
     * Obtener un usuario Staff por ID con detalles completos
     */
    @Transactional(readOnly = true)
    public AdminUserResponseDTO getStaffById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        if (user.getIsCustomer()) {
            throw new SecurityViolationException("El usuario solicitado no es Staff");
        }

        return convertToResponseDTO(user);
    }

    /**
     * Crear un nuevo usuario Staff
     * CRITICAL: Fuerza is_customer = false y encripta el password
     */
    @Transactional
    public AdminUserResponseDTO createStaffUser(AdminUserCreateDTO dto) {
        // Validar que el email no exista
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario con el email: " + dto.getEmail());
        }

        // Si se envió un username explícito, validar que no esté en uso
        if (dto.getUsername() != null && !dto.getUsername().trim().isEmpty()
                && userRepository.existsByUsername(dto.getUsername().trim())) {
            throw new IllegalArgumentException("Ya existe un usuario con el username: " + dto.getUsername());
        }

        // Crear nueva entidad User
        User newUser = new User();
        newUser.setFirstName(dto.getFirstName().trim());
        newUser.setLastName(dto.getLastName().trim());
        newUser.setEmail(dto.getEmail().trim().toLowerCase());

        // Si no viene username en el DTO, se genera automáticamente desde el email
        // (parte antes del @) garantizando unicidad con un sufijo numérico si es
        // necesario
        String baseUsername;
        if (dto.getUsername() != null && !dto.getUsername().trim().isEmpty()) {
            baseUsername = dto.getUsername().trim();
        } else {
            String localPart = dto.getEmail().trim().toLowerCase().split("@")[0]
                    .replaceAll("[^a-zA-Z0-9_-]", "_");
            // Truncar a 25 chars para dejar espacio para sufijo numérico (max 30 en BD)
            baseUsername = localPart.length() > 25 ? localPart.substring(0, 25) : localPart;
        }
        // Garantizar unicidad del username
        String finalUsername = baseUsername;
        int suffix = 1;
        while (userRepository.existsByUsername(finalUsername)) {
            finalUsername = baseUsername + suffix;
            suffix++;
        }
        newUser.setUsername(finalUsername);

        newUser.setPhone(dto.getPhone());

        // CRITICAL: Forzar is_customer = false (es Staff)
        newUser.setIsCustomer(false);

        // Encriptar password con BCrypt
        newUser.setPassword(passwordEncoder.encode(dto.getPassword()));

        // Establecer estados
        newUser.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        newUser.setAccountNonLocked(dto.getAccountNonLocked() != null ? dto.getAccountNonLocked() : true);
        newUser.setAccountNonExpired(true);
        newUser.setCredentialsNonExpired(true);

        // Asignar roles
        Set<Role> roles = new HashSet<>();
        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            roles = dto.getRoleIds().stream()
                    .map(roleId -> roleRepository.findById(roleId)
                            .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado con ID: " + roleId)))
                    .collect(Collectors.toSet());

            // SECURITY: Prevent privilege escalation — only SUPER_ADMIN can assign
            // ROLE_SUPER_ADMIN
            boolean requestsSuperAdmin = roles.stream()
                    .anyMatch(r -> "ROLE_SUPER_ADMIN".equals(r.getName()));
            if (requestsSuperAdmin && !currentUserHasRole("ROLE_SUPER_ADMIN")) {
                throw new SecurityViolationException(
                        "No tienes permisos para asignar el rol SUPER_ADMIN");
            }
        } else {
            // Por defecto asignar ROLE_USER
            Role defaultRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new ResourceNotFoundException("Rol ROLE_USER no encontrado"));
            roles.add(defaultRole);
        }
        newUser.setRoles(roles);

        // Guardar usuario — capturamos DataIntegrityViolationException para mensajes
        // claros
        User savedUser;
        try {
            savedUser = userRepository.save(newUser);
        } catch (DataIntegrityViolationException ex) {
            String msg = ex.getMostSpecificCause().getMessage();
            logger.error("❌ Error de integridad al crear Staff: {}", msg);
            if (msg != null && msg.contains("username")) {
                throw new IllegalArgumentException(
                        "El username generado ya existe. Intente de nuevo o especifique un username distinto.");
            }
            if (msg != null && msg.contains("email")) {
                throw new IllegalArgumentException("Ya existe un usuario con ese email.");
            }
            throw new IllegalArgumentException("Error de base de datos al crear el usuario: " + msg);
        }

        // Auditoría
        String currentAdmin = getCurrentUsername();
        String rolesStr = roles.stream()
                .map(r -> r.getName())
                .collect(Collectors.joining(", "));

        try {
            auditLogService.logUserCreation(savedUser.getId(), savedUser.getEmail(), currentAdmin);
            auditLogService.logRoleAssignment(savedUser.getId(), savedUser.getEmail(), rolesStr, currentAdmin);
        } catch (Exception auditEx) {
            // El usuario ya fue guardado; el fallo de auditoría no debe revertir la
            // creación
            logger.warn("⚠️ No se pudo registrar audit log para el usuario creado {}: {}", savedUser.getEmail(),
                    auditEx.getMessage());
        }

        logger.info("✅ Usuario Staff creado: {} (ID: {}) por {}", savedUser.getEmail(), savedUser.getId(),
                currentAdmin);

        return convertToResponseDTO(savedUser);
    }

    /**
     * Actualizar un usuario Staff existente
     * CRITICAL: Valida que no se modifique a sí mismo si intenta quitarse
     * ROLE_ADMIN
     */
    @Transactional
    public AdminUserResponseDTO updateStaffUser(Long id, AdminUserUpdateDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        if (user.getIsCustomer()) {
            throw new SecurityViolationException("No se puede modificar un usuario Customer desde este endpoint");
        }

        String currentAdmin = getCurrentUsername();
        Long currentAdminId = getCurrentUserId();
        StringBuilder changes = new StringBuilder();

        // Validar que no se esté modificando a sí mismo
        boolean isSelfUpdate = user.getId().equals(currentAdminId);

        // Actualizar campos básicos
        if (dto.getFirstName() != null && !dto.getFirstName().equals(user.getFirstName())) {
            changes.append("firstName: ").append(user.getFirstName()).append(" -> ").append(dto.getFirstName())
                    .append("; ");
            user.setFirstName(dto.getFirstName().trim());
        }

        if (dto.getLastName() != null && !dto.getLastName().equals(user.getLastName())) {
            changes.append("lastName: ").append(user.getLastName()).append(" -> ").append(dto.getLastName())
                    .append("; ");
            user.setLastName(dto.getLastName().trim());
        }

        if (dto.getEmail() != null && !dto.getEmail().equalsIgnoreCase(user.getEmail())) {
            // Validar que el nuevo email no exista
            if (userRepository.existsByEmail(dto.getEmail())) {
                throw new IllegalArgumentException("Ya existe un usuario con el email: " + dto.getEmail());
            }
            changes.append("email: ").append(user.getEmail()).append(" -> ").append(dto.getEmail()).append("; ");
            user.setEmail(dto.getEmail().trim().toLowerCase());
        }

        if (dto.getUsername() != null && !dto.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(dto.getUsername())) {
                throw new IllegalArgumentException("Ya existe un usuario con el username: " + dto.getUsername());
            }
            changes.append("username: ").append(user.getUsername()).append(" -> ").append(dto.getUsername())
                    .append("; ");
            user.setUsername(dto.getUsername().trim());
        }

        if (dto.getPhone() != null && !dto.getPhone().equals(user.getPhone())) {
            changes.append("phone: ").append(user.getPhone()).append(" -> ").append(dto.getPhone()).append("; ");
            user.setPhone(dto.getPhone());
        }

        // Actualizar estados (con validación de seguridad)
        if (dto.getEnabled() != null && !dto.getEnabled().equals(user.getEnabled())) {
            if (isSelfUpdate && !dto.getEnabled()) {
                throw new SecurityViolationException("❌ No puedes desactivar tu propia cuenta");
            }
            changes.append("enabled: ").append(user.getEnabled()).append(" -> ").append(dto.getEnabled()).append("; ");
            user.setEnabled(dto.getEnabled());

            // Log específico de activación/desactivación
            if (dto.getEnabled()) {
                auditLogService.logUserActivation(user.getId(), user.getEmail(), currentAdmin);
            } else {
                auditLogService.logUserDeactivation(user.getId(), user.getEmail(), currentAdmin);
            }
        }

        if (dto.getAccountNonLocked() != null && !dto.getAccountNonLocked().equals(user.getAccountNonLocked())) {
            if (isSelfUpdate && !dto.getAccountNonLocked()) {
                throw new SecurityViolationException("❌ No puedes bloquear tu propia cuenta");
            }
            changes.append("accountNonLocked: ").append(user.getAccountNonLocked()).append(" -> ")
                    .append(dto.getAccountNonLocked()).append("; ");
            user.setAccountNonLocked(dto.getAccountNonLocked());

            // Log específico de bloqueo/desbloqueo
            if (dto.getAccountNonLocked()) {
                auditLogService.logAccountUnlock(user.getId(), user.getEmail(), currentAdmin);
            } else {
                auditLogService.logAccountLock(user.getId(), user.getEmail(), currentAdmin);
            }
        }

        // Actualizar password (opcional — solo si se envió uno nuevo)
        if (dto.getPassword() != null && !dto.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
            user.setCredentialsNonExpired(true);
            changes.append("password: [actualizado]; ");
        }

        // Actualizar roles (con validación de seguridad CRÍTICA)
        if (dto.getRoleIds() != null) {
            Set<Role> currentRoles = user.getRoles();
            Set<Role> newRoles = dto.getRoleIds().stream()
                    .map(roleId -> roleRepository.findById(roleId)
                            .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado con ID: " + roleId)))
                    .collect(Collectors.toSet());

            // SECURITY: Prevent privilege escalation — only SUPER_ADMIN can assign
            // ROLE_SUPER_ADMIN
            boolean requestsSuperAdmin = newRoles.stream()
                    .anyMatch(r -> "ROLE_SUPER_ADMIN".equals(r.getName()));
            if (requestsSuperAdmin && !currentUserHasRole("ROLE_SUPER_ADMIN")) {
                throw new SecurityViolationException(
                        "No tienes permisos para asignar el rol SUPER_ADMIN");
            }

            // VALIDACION CRITICA: No permitir que un admin se quite el rol ROLE_ADMIN a si
            // mismo
            if (isSelfUpdate) {
                boolean hadAdminRole = currentRoles.stream()
                        .anyMatch(r -> "ROLE_ADMIN".equals(r.getName()) || "ROLE_SUPER_ADMIN".equals(r.getName()));
                boolean hasAdminRoleInNew = newRoles.stream()
                        .anyMatch(r -> "ROLE_ADMIN".equals(r.getName()) || "ROLE_SUPER_ADMIN".equals(r.getName()));

                if (hadAdminRole && !hasAdminRoleInNew) {
                    throw new SecurityViolationException("No puedes quitarte el rol de ADMIN a ti mismo");
                }
            }

            String oldRoles = currentRoles.stream().map(r -> r.getName()).collect(Collectors.joining(", "));
            String newRolesStr = newRoles.stream().map(r -> r.getName()).collect(Collectors.joining(", "));

            if (!oldRoles.equals(newRolesStr)) {
                changes.append("roles: [").append(oldRoles).append("] -> [").append(newRolesStr).append("]; ");
                user.setRoles(newRoles);
                auditLogService.logRoleAssignment(user.getId(), user.getEmail(), newRolesStr, currentAdmin);
            }
        }

        // Guardar cambios
        User updatedUser = userRepository.save(user);

        // Auditoría general de actualización
        if (changes.length() > 0) {
            auditLogService.logUserUpdate(updatedUser.getId(), updatedUser.getEmail(), currentAdmin,
                    changes.toString());
            logger.info("✅ Usuario Staff actualizado: {} (ID: {}) por {}. Cambios: {}",
                    updatedUser.getEmail(), updatedUser.getId(), currentAdmin, changes.toString());
        }

        return convertToResponseDTO(updatedUser);
    }

    /**
     * Cambiar estado enabled de un usuario (Soft Delete)
     * CRITICAL: No permite desactivarse a sí mismo
     */
    @Transactional
    public AdminUserResponseDTO toggleEnabledStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        if (user.getIsCustomer()) {
            throw new SecurityViolationException("No se puede modificar un usuario Customer desde este endpoint");
        }

        String currentAdmin = getCurrentUsername();
        Long currentAdminId = getCurrentUserId();

        // VALIDACIÓN CRÍTICA: No permitir desactivarse a sí mismo
        if (user.getId().equals(currentAdminId) && user.getEnabled()) {
            throw new SecurityViolationException("❌ No puedes desactivar tu propia cuenta");
        }

        // Toggle del estado
        user.setEnabled(!user.getEnabled());
        User updatedUser = userRepository.save(user);

        // Auditoría
        if (updatedUser.getEnabled()) {
            auditLogService.logUserActivation(updatedUser.getId(), updatedUser.getEmail(), currentAdmin);
            logger.info("✅ Usuario Staff activado: {} (ID: {}) por {}", updatedUser.getEmail(), updatedUser.getId(),
                    currentAdmin);
        } else {
            auditLogService.logUserDeactivation(updatedUser.getId(), updatedUser.getEmail(), currentAdmin);
            logger.info("⚠️ Usuario Staff desactivado: {} (ID: {}) por {}", updatedUser.getEmail(), updatedUser.getId(),
                    currentAdmin);
        }

        return convertToResponseDTO(updatedUser);
    }

    /**
     * Cambiar estado accountNonLocked de un usuario
     * CRITICAL: No permite bloquearse a sí mismo
     */
    @Transactional
    public AdminUserResponseDTO toggleLockedStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        if (user.getIsCustomer()) {
            throw new SecurityViolationException("No se puede modificar un usuario Customer desde este endpoint");
        }

        String currentAdmin = getCurrentUsername();
        Long currentAdminId = getCurrentUserId();

        // VALIDACIÓN CRÍTICA: No permitir bloquearse a sí mismo
        if (user.getId().equals(currentAdminId) && user.getAccountNonLocked()) {
            throw new SecurityViolationException("❌ No puedes bloquear tu propia cuenta");
        }

        // Toggle del estado
        user.setAccountNonLocked(!user.getAccountNonLocked());
        User updatedUser = userRepository.save(user);

        // Auditoría
        if (updatedUser.getAccountNonLocked()) {
            auditLogService.logAccountUnlock(updatedUser.getId(), updatedUser.getEmail(), currentAdmin);
            logger.info("✅ Cuenta desbloqueada: {} (ID: {}) por {}", updatedUser.getEmail(), updatedUser.getId(),
                    currentAdmin);
        } else {
            auditLogService.logAccountLock(updatedUser.getId(), updatedUser.getEmail(), currentAdmin);
            logger.info("⚠️ Cuenta bloqueada: {} (ID: {}) por {}", updatedUser.getEmail(), updatedUser.getId(),
                    currentAdmin);
        }

        return convertToResponseDTO(updatedUser);
    }

    /**
     * Eliminar un usuario Staff (hard delete)
     * CRITICAL: No permite eliminarse a sí mismo
     */
    @Transactional
    public void deleteStaffUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        if (user.getIsCustomer()) {
            throw new SecurityViolationException("No se puede eliminar un usuario Customer desde este endpoint");
        }

        String currentAdmin = getCurrentUsername();
        Long currentAdminId = getCurrentUserId();

        // VALIDACIÓN CRÍTICA: No permitir eliminarse a sí mismo
        if (user.getId().equals(currentAdminId)) {
            throw new SecurityViolationException("❌ No puedes eliminar tu propia cuenta");
        }

        String userEmail = user.getEmail();
        Long userId = user.getId();

        try {
            auditLogService.logUserDeactivation(userId, userEmail, currentAdmin);
        } catch (Exception auditEx) {
            logger.warn("⚠️ No se pudo registrar audit log de eliminación para {}: {}", userEmail,
                    auditEx.getMessage());
        }

        userRepository.delete(user);
        logger.info("🗑️ Usuario Staff eliminado: {} (ID: {}) por {}", userEmail, userId, currentAdmin);
    }

    /**
     * Resetear intentos de login fallidos de un usuario Staff
     */
    @Transactional
    public AdminUserResponseDTO resetFailedLoginAttempts(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        if (user.getIsCustomer()) {
            throw new SecurityViolationException("No se puede modificar un usuario Customer desde este endpoint");
        }

        // Desbloquear la cuenta si estaba bloqueada por intentos fallidos
        user.setAccountNonLocked(true);
        User updatedUser = userRepository.save(user);

        String currentAdmin = getCurrentUsername();

        try {
            auditLogService.logAccountUnlock(updatedUser.getId(), updatedUser.getEmail(), currentAdmin);
        } catch (Exception auditEx) {
            logger.warn("⚠️ No se pudo registrar audit log de reset para {}: {}", updatedUser.getEmail(),
                    auditEx.getMessage());
        }

        logger.info("🔓 Intentos fallidos reseteados para: {} (ID: {}) por {}", updatedUser.getEmail(),
                updatedUser.getId(), currentAdmin);
        return convertToResponseDTO(updatedUser);
    }

    /**
     * Exportar lista de staff a CSV (UTF-8 BOM para Excel)
     */
    @Transactional(readOnly = true)
    public byte[] exportStaffToCsv(String search, Long roleId) {
        // Obtener todos sin paginación para el CSV
        Page<AdminUserListDTO> allStaff;
        if (search != null && !search.trim().isEmpty()) {
            allStaff = searchStaff(search, null, null, 0, Integer.MAX_VALUE);
        } else {
            allStaff = getAllStaff(0, Integer.MAX_VALUE, "createdAt", "desc");
        }

        StringBuilder csv = new StringBuilder();
        // BOM para que Excel abra correctamente UTF-8
        csv.append('\uFEFF');
        csv.append("ID,Nombre,Apellido,Email,Teléfono,Estado,Cuenta,Roles,Creado\n");

        for (AdminUserListDTO user : allStaff.getContent()) {
            csv.append(user.getId()).append(",");
            csv.append(escapeCsv(user.getFirstName())).append(",");
            csv.append(escapeCsv(user.getLastName())).append(",");
            csv.append(escapeCsv(user.getEmail())).append(",");
            csv.append(escapeCsv(user.getPhone() != null ? user.getPhone() : "")).append(",");
            csv.append(Boolean.TRUE.equals(user.getEnabled()) ? "Activo" : "Inactivo").append(",");
            csv.append(Boolean.TRUE.equals(user.getAccountNonLocked()) ? "Desbloqueada" : "Bloqueada").append(",");
            csv.append(escapeCsv(user.getRoles() != null ? user.getRoles() : "")).append(",");
            csv.append(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "").append("\n");
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escapeCsv(String value) {
        if (value == null)
            return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ==================== Métodos Helper ====================

    /**
     * Convertir User a AdminUserListDTO (para listados paginados)
     */
    private AdminUserListDTO convertToListDTO(User user) {
        AdminUserListDTO dto = new AdminUserListDTO();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setEnabled(user.getEnabled());
        dto.setAccountNonLocked(user.getAccountNonLocked());
        dto.setCreatedAt(user.getCreatedAt());

        // Concatenar roles
        String rolesStr = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.joining(", "));
        dto.setRoles(rolesStr);

        return dto;
    }

    /**
     * Convertir User a AdminUserResponseDTO (para detalles completos)
     */
    private AdminUserResponseDTO convertToResponseDTO(User user) {
        AdminUserResponseDTO dto = new AdminUserResponseDTO();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setEnabled(user.getEnabled());
        dto.setAccountNonLocked(user.getAccountNonLocked());
        dto.setAccountNonExpired(user.getAccountNonExpired());
        dto.setCredentialsNonExpired(user.getCredentialsNonExpired());
        dto.setTwoFactorEnabled(user.getTwoFactorEnabled());
        dto.setGoogleAuthEnabled(user.getGoogleAuthEnabled());
        dto.setEmailEnabled(user.getEmailEnabled());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());

        // Roles simples
        Set<String> rolesSet = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toSet());
        dto.setRoles(rolesSet);

        // Roles detallados con permisos
        Set<AdminUserResponseDTO.RoleDTO> rolesDetail = user.getRoles().stream()
                .map(role -> {
                    Set<String> permissions = role.getPermissions().stream()
                            .map(p -> p.getName())
                            .collect(Collectors.toSet());
                    return new AdminUserResponseDTO.RoleDTO(role.getId(), role.getName(), permissions);
                })
                .collect(Collectors.toSet());
        dto.setRolesDetail(rolesDetail);

        return dto;
    }

    /**
     * Obtener el username del usuario autenticado actual
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    /**
     * Obtener el ID del usuario autenticado actual
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
            String email = auth.getName();
            return userRepository.findByEmail(email)
                    .map(User::getId)
                    .orElse(null);
        }
        return null;
    }

    /**
     * Verifica si el usuario autenticado actual tiene el rol indicado
     */
    private boolean currentUserHasRole(String roleName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null)
            return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> roleName.equals(a.getAuthority()));
    }
}
