package com.security.repository;

import com.security.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de <strong>solo-lectura</strong> para la API pública del
 * Storefront.
 *
 * <p>
 * <strong>Regla de Oro de Seguridad:</strong> En <em>todas</em> las consultas
 * de este repositorio, {@code p.active = true} está hardcodeado en JPQL.
 * Nunca se retorna un borrador o producto inactivo bajo ninguna circunstancia,
 * independientemente de los parámetros que lleguen desde el controlador.
 * </p>
 *
 * <p>
 * Se usa <strong>JPQL con parámetros nombrados</strong> (no SQL nativo) para
 * prevención automática de inyección SQL por parte de JPA/Hibernate.
 * </p>
 */
@Repository
public interface PublicProductRepository extends JpaRepository<Product, Long> {

    // ── 1. Últimos N productos activos ────────────────────────────────────────

    /**
     * Devuelve los últimos productos agregados que estén activos,
     * ordenados por fecha de creación descendente.
     *
     * <p>
     * El límite de 8 se controla con el {@link Pageable} que pasa el servicio
     * ({@code PageRequest.of(0, 8)}). El filtro {@code p.active = true} es
     * inmutable en la query.
     * </p>
     */
    @Query("""
            SELECT p FROM Product p
            LEFT JOIN FETCH p.category
            LEFT JOIN FETCH p.brand
            WHERE p.active = true
            ORDER BY p.createdAt DESC
            """)
    java.util.List<Product> findLatestActive(Pageable pageable);

    // ── 2. Productos destacados activos ───────────────────────────────────────

    /**
     * Devuelve productos marcados como featured AND active.
     * Paginado para soportar catálogos grandes sin riesgo DoS.
     */
    @Query("""
            SELECT p FROM Product p
            LEFT JOIN FETCH p.category
            LEFT JOIN FETCH p.brand
            WHERE p.active = true
              AND p.featured = true
            ORDER BY p.createdAt DESC
            """)
    Page<Product> findFeaturedActive(Pageable pageable);

    // ── 3. Catálogo con filtros opcionales ────────────────────────────────────

    /**
     * Endpoint de catálogo público con búsqueda y filtros opcionales.
     *
     * <p>
     * <strong>Seguridad contra inyección:</strong> Todos los parámetros son
     * enlazados por nombre ({@code :keyword}, {@code :categoryId},
     * {@code :brandId}). JPQL nunca concatena strings en la query.
     * </p>
     *
     * <p>
     * <strong>Seguridad lógica:</strong> {@code p.active = true} es la primera
     * condición y no puede ser sobreescrita por ningún parámetro del cliente.
     * </p>
     *
     * @param keyword    substring insensible a mayúsculas (null = sin filtro)
     * @param categoryId ID de categoría (null = sin filtro)
     * @param brandId    ID de marca (null = sin filtro)
     * @param pageable   paginación (el servicio limita a máx. 50 por página)
     */
    @Query("""
            SELECT DISTINCT p FROM Product p
            LEFT JOIN FETCH p.category cat
            LEFT JOIN FETCH p.brand br
            WHERE p.active = true
              AND (CAST(:keyword AS string) IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                   OR LOWER(p.sku)  LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
              AND (:categoryId IS NULL OR cat.id = :categoryId)
              AND (:brandId    IS NULL OR br.id  = :brandId)
            ORDER BY p.createdAt DESC
            """)
    Page<Product> findCatalog(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("brandId") Long brandId,
            Pageable pageable);

    /**
     * Query de conteo separada para la paginación del catálogo (evita DISTINCT
     * en la query de conteo, que puede ser costoso).
     */
    @Query("""
            SELECT COUNT(DISTINCT p.id) FROM Product p
            LEFT JOIN p.category cat
            LEFT JOIN p.brand br
            WHERE p.active = true
              AND (CAST(:keyword AS string) IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                   OR LOWER(p.sku)  LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
              AND (:categoryId IS NULL OR cat.id = :categoryId)
              AND (:brandId    IS NULL OR br.id  = :brandId)
            """)
    long countCatalog(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("brandId") Long brandId);

    // ── 4. Detalle de producto activo ─────────────────────────────────────────

    /**
     * Busca un producto por ID <strong>solo si está activo</strong>.
     *
     * <p>
     * Si el producto existe pero {@code active = false}, retorna
     * {@link Optional#empty()} — el servicio lanzará un 404, nunca expondrá un
     * borrador al público.
     * </p>
     */
    @Query("""
            SELECT p FROM Product p
            LEFT JOIN FETCH p.category
            LEFT JOIN FETCH p.brand
            LEFT JOIN FETCH p.images
            LEFT JOIN FETCH p.customAttributes
            WHERE p.id = :id
              AND p.active = true
            """)
    Optional<Product> findActiveById(@Param("id") Long id);
}
