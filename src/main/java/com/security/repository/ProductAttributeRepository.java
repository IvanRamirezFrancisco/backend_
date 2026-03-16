package com.security.repository;

import com.security.entity.ProductAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository para la gestión de atributos dinámicos de productos
 */
@Repository
public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {

    /**
     * Obtiene todos los atributos de un producto ordenados por displayOrder
     */
    List<ProductAttribute> findByProductIdOrderByDisplayOrderAsc(Long productId);

    /**
     * Elimina todos los atributos de un producto específico
     */
    @Modifying
    @Query("DELETE FROM ProductAttribute pa WHERE pa.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    /**
     * Cuenta cuántos atributos tiene un producto
     */
    long countByProductId(Long productId);

    /**
     * Busca atributos por nombre (key) en un producto específico
     */
    List<ProductAttribute> findByProductIdAndAttributeName(Long productId, String attributeName);
}
