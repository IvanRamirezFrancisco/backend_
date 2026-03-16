package com.security.repository;

import com.security.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    /**
     * Carga múltiples categorías por nombre en una sola query — anti N+1 para CSV
     * import
     */
    List<Category> findByNameIn(List<String> names);

    List<Category> findByActiveTrue();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.products WHERE c.id = :id")
    Optional<Category> findByIdWithProducts(Long id);

    /**
     * Obtener todas las categorías con sus subcategorías cargadas (EAGER)
     * Nota: No cargamos products aquí para evitar MultipleBagFetchException
     * Los productos se cuentan con una query nativa más eficiente
     */
    @Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.subcategories")
    List<Category> findAllWithSubcategories();

    boolean existsByName(String name);

    /**
     * Contar cuántas subcategorías tiene una categoría padre
     * Usado para validar que una categoría esté vacía antes de eliminarla
     */
    @Query("SELECT COUNT(c) FROM Category c WHERE c.parent.id = :parentId")
    long countByParentId(Long parentId);
}
