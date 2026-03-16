package com.security.repository;

import com.security.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    // Busqueda por nombre (String)
    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    // Metodos para gestion de roles de usuario
    @Query("SELECT r FROM Role r JOIN r.users u WHERE u.id = :userId")
    Set<Role> findByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM Role r JOIN r.users u WHERE u.email = :email")
    Set<Role> findByUserEmail(@Param("email") String email);

    // Busquedas por nombres de roles
    List<Role> findByNameIn(List<String> names);

    // Contadores
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.id = :roleId")
    long countUsersByRoleId(@Param("roleId") Long roleId);

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :roleName")
    long countUsersByRoleName(@Param("roleName") String roleName);

    // Roles sin usuarios asignados
    @Query("SELECT r FROM Role r WHERE r.users IS EMPTY")
    List<Role> findRolesWithoutUsers();
}