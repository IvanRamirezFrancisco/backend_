package com.security.repository;

import com.security.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de <strong>solo-lectura</strong> para categorías en la API
 * pública.
 *
 * <p>
 * Todas las consultas filtran por {@code c.active = true} de manera
 * hardcodeada en JPQL. El campo {@code @Formula productCount} de la entidad
 * ya calcula el total de productos, pero aquí se agrega una variante que
 * filtra solo categorías con al menos 1 producto <em>activo</em>.
 * </p>
 */
@Repository
public interface PublicCategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Devuelve todas las categorías activas, ordenadas por nombre.
     * Incluye categorías sin productos (el frontend puede ocultarlas si quiere).
     */
    @Query("""
            SELECT c FROM Category c
            LEFT JOIN FETCH c.parent
            WHERE c.active = true
            ORDER BY c.name ASC
            """)
    List<Category> findAllActive();

    /**
     * Devuelve solo las categorías activas que tengan al menos un producto activo.
     * Útil para no mostrar categorías vacías en la navegación del storefront.
     */
    @Query("""
            SELECT DISTINCT c FROM Category c
            LEFT JOIN FETCH c.parent
            WHERE c.active = true
              AND EXISTS (
                  SELECT 1 FROM Product p
                  WHERE p.category = c
                    AND p.active = true
              )
            ORDER BY c.name ASC
            """)
    List<Category> findActiveWithProducts();
}
