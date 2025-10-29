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

    // Contadores
    long countByEnabled(boolean enabled);

    long countByTwoFactorEnabled(boolean twoFactorEnabled);
}