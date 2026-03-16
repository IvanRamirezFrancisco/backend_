package com.security.service;

import com.security.dto.public_api.PublicCategoryDTO;
import com.security.repository.PublicCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de la API pública para categorías del Storefront.
 *
 * <p>
 * Solo expone categorías activas. El conteo de productos activos
 * se obtiene mediante la {@code @Formula} de Hibernate en la entidad
 * {@code Category} sin generar un N+1.
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class PublicCategoryService {

    private final PublicCategoryRepository publicCategoryRepository;

    public PublicCategoryService(PublicCategoryRepository publicCategoryRepository) {
        this.publicCategoryRepository = publicCategoryRepository;
    }

    /**
     * Retorna todas las categorías activas (con o sin productos).
     * Útil para menús de navegación completos.
     */
    public List<PublicCategoryDTO> getActiveCategories() {
        return publicCategoryRepository.findAllActive()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retorna solo las categorías activas que tengan al menos 1 producto activo.
     * Útil para el sidebar del catálogo (evita mostrar categorías vacías).
     */
    public List<PublicCategoryDTO> getActiveCategoriesWithProducts() {
        return publicCategoryRepository.findActiveWithProducts()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ── Mapeo ─────────────────────────────────────────────────────────────────

    private PublicCategoryDTO toDTO(com.security.entity.Category c) {
        return new PublicCategoryDTO(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.getImageUrl(),
                c.getParentId(),
                c.getParentName(),
                c.getProductCount() // calculado por @Formula (subquery SQL, sin N+1)
        );
    }
}
