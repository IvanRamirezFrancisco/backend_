package com.security.repository;

import com.security.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository para gestión de permisos del sistema
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /**
     * Buscar permiso por su nombre único
     */
    Optional<Permission> findByName(String name);

    /**
     * Verificar si existe un permiso con ese nombre
     */
    boolean existsByName(String name);

    /**
     * Buscar permisos por categoría
     */
    List<Permission> findByCategory(String category);

    /**
     * Buscar permisos por múltiples IDs
     */
    @Query("SELECT p FROM Permission p WHERE p.id IN :ids")
    Set<Permission> findByIdIn(@Param("ids") Set<Long> ids);

    /**
     * Obtener todos los permisos ordenados por categoría y nombre
     */
    @Query("SELECT p FROM Permission p ORDER BY p.category, p.name")
    List<Permission> findAllOrderedByCategoryAndName();

    /**
     * Buscar permisos que contengan cierto texto en nombre o descripción
     */
    @Query("SELECT p FROM Permission p WHERE " +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Permission> searchByNameOrDescription(@Param("searchTerm") String searchTerm);

    /**
     * Obtener todas las categorías únicas de permisos
     */
    @Query("SELECT DISTINCT p.category FROM Permission p WHERE p.category IS NOT NULL ORDER BY p.category")
    List<String> findAllCategories();

    /**
     * Contar permisos por categoría
     */
    @Query("SELECT COUNT(p) FROM Permission p WHERE p.category = :category")
    Long countByCategory(@Param("category") String category);
}
