package com.security.repository;

import com.security.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

        // Métodos para autenticación (CORREGIDOS)
        Optional<User> findByEmail(String email);

        // Verificar existencia
        boolean existsByEmail(String email);

        boolean existsByUsername(String username);

        // Búsqueda por username
        Optional<User> findByUsername(String username);

        // Métodos para estado del usuario
        List<User> findByEnabled(boolean enabled);

        List<User> findByAccountNonLocked(boolean accountNonLocked);

        // Métodos para autenticación de dos factores
        Optional<User> findByTwoFactorSecret(String twoFactorSecret);

        List<User> findByTwoFactorEnabledTrue();

        // Búsquedas personalizadas
        @Query("SELECT u FROM User u WHERE u.email LIKE %:searchTerm% OR u.firstName LIKE %:searchTerm% OR u.lastName LIKE %:searchTerm%")
        List<User> findByEmailOrNameContaining(@Param("searchTerm") String searchTerm);

        @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName")
        List<User> findByRoleName(@Param("roleName") String roleName);

        // Métodos para gestión de cuentas
        @Query("SELECT u FROM User u WHERE u.createdAt BETWEEN :startDate AND :endDate")
        List<User> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        // Contadores para administración
        long countByEnabled(boolean enabled);

        long countByTwoFactorEnabled(boolean twoFactorEnabled);

        // Métodos específicos para contadores de administración
        long countByEnabledTrue();

        long countByTwoFactorEnabledTrue();

        // Métodos para gestión de clientes (usuarios que han comprado)
        long countByIsCustomerTrueAndEnabledTrue();

        List<User> findByIsCustomerTrue();

        @Query("SELECT u FROM User u WHERE u.isCustomer = true AND u.enabled = true ORDER BY u.totalSpent DESC")
        List<User> findTopCustomersBySpending(org.springframework.data.domain.Pageable pageable);

        // ==================== Metodos para gestion de Staff (is_customer = false)
        // ====================

        /**
         * Buscar usuarios Staff paginados
         */
        @Query("SELECT u FROM User u WHERE u.isCustomer = false ORDER BY u.createdAt DESC")
        org.springframework.data.domain.Page<User> findAllStaff(org.springframework.data.domain.Pageable pageable);

        /**
         * Buscar usuarios Staff con filtros
         */
        @Query("SELECT u FROM User u WHERE u.isCustomer = false AND " +
                        "(:enabled IS NULL OR u.enabled = :enabled) AND " +
                        "(:accountNonLocked IS NULL OR u.accountNonLocked = :accountNonLocked) AND " +
                        "(LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                        "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                        "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
                        "ORDER BY u.createdAt DESC")
        org.springframework.data.domain.Page<User> findStaffWithFilters(
                        @Param("searchTerm") String searchTerm,
                        @Param("enabled") Boolean enabled,
                        @Param("accountNonLocked") Boolean accountNonLocked,
                        org.springframework.data.domain.Pageable pageable);

        /**
         * Contar usuarios Staff activos
         */
        @Query("SELECT COUNT(u) FROM User u WHERE u.isCustomer = false AND u.enabled = true")
        Long countStaffEnabled();

        /**
         * Verificar si un usuario tiene un rol especifico (por nombre de enum)
         */
        @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END " +
                        "FROM User u JOIN u.roles r WHERE u.id = :userId AND r.name = :roleName")
        boolean hasRole(@Param("userId") Long userId, @Param("roleName") String roleName);

        // ==================== Métodos para gestión de Clientes (is_customer = true)
        // ====================

        /**
         * Listar todos los clientes (is_customer = true) paginados, ordenados por
         * fecha de registro descendente.
         */
        @Query("SELECT u FROM User u WHERE u.isCustomer = true ORDER BY u.createdAt DESC")
        org.springframework.data.domain.Page<User> findAllCustomers(org.springframework.data.domain.Pageable pageable);

        /**
         * Buscar clientes con filtros de búsqueda, estado habilitado y estado de
         * bloqueo. Los parámetros opcionales se ignoran cuando son NULL.
         */
        @Query("SELECT u FROM User u WHERE u.isCustomer = true AND " +
                        "(:enabled IS NULL OR u.enabled = :enabled) AND " +
                        "(:accountNonLocked IS NULL OR u.accountNonLocked = :accountNonLocked) AND " +
                        "(LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                        "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                        "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                        "LOWER(u.phone) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
                        "ORDER BY u.createdAt DESC")
        org.springframework.data.domain.Page<User> findCustomersWithFilters(
                        @Param("searchTerm") String searchTerm,
                        @Param("enabled") Boolean enabled,
                        @Param("accountNonLocked") Boolean accountNonLocked,
                        org.springframework.data.domain.Pageable pageable);

        /**
         * Contar clientes activos (habilitados)
         */
        @Query("SELECT COUNT(u) FROM User u WHERE u.isCustomer = true AND u.enabled = true")
        Long countCustomersEnabled();

        /**
         * Contar total de clientes
         */
        @Query("SELECT COUNT(u) FROM User u WHERE u.isCustomer = true")
        Long countAllCustomers();

        /**
         * Listar todos los usuarios (staff y clientes) que tienen el rol indicado,
         * paginados.
         * Usado por el endpoint de auditoría GET /api/admin/roles/{id}/users.
         */
        @Query("SELECT u FROM User u JOIN u.roles r WHERE r.id = :roleId ORDER BY u.createdAt DESC")
        org.springframework.data.domain.Page<User> findByRolesId(
                        @Param("roleId") Long roleId,
                        org.springframework.data.domain.Pageable pageable);
}
