package com.security.repository;

import com.security.entity.Role;
import com.security.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    // Búsqueda por enum (CORREGIDO)
    Optional<Role> findByName(RoleName name);

    boolean existsByName(RoleName name);

    // Métodos para gestión de roles de usuario
    @Query("SELECT r FROM Role r JOIN r.users u WHERE u.id = :userId")
    Set<Role> findByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM Role r JOIN r.users u WHERE u.email = :email")
    Set<Role> findByUserEmail(@Param("email") String email);

    // Búsquedas por nombres de roles
    List<Role> findByNameIn(List<RoleName> names);

    // Contadores
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.id = :roleId")
    long countUsersByRoleId(@Param("roleId") Long roleId);

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :roleName")
    long countUsersByRoleName(@Param("roleName") RoleName roleName);

    // Roles sin usuarios asignados
    @Query("SELECT r FROM Role r WHERE r.users IS EMPTY")
    List<Role> findRolesWithoutUsers();
}