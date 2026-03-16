package com.security.service;

import com.security.entity.Category;
import com.security.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio para gestión de categorías de productos
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * Obtener todas las categorías activas
     */
    @Transactional(readOnly = true)
    public List<Category> getAllActiveCategories() {
        return categoryRepository.findByActiveTrue();
    }

    /**
     * Obtener todas las categorías (activas e inactivas)
     * Carga las subcategorías de forma EAGER para evitar
     * LazyInitializationException
     * Los productos se cuentan de forma lazy-safe en el DTO
     */
    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAllWithSubcategories();
    }

    /**
     * Obtener categoría por ID
     */
    @Transactional(readOnly = true)
    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }

    /**
     * Obtener categoría por nombre
     */
    @Transactional(readOnly = true)
    public Optional<Category> getCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }

    /**
     * Crear nueva categoría
     * Si tiene parentId, establece la relación con la categoría padre
     */
    @Transactional
    public Category createCategory(Category category, Long parentId) {
        // Si tiene ID de padre, buscar y setear la relación
        if (parentId != null) {
            Category parent = categoryRepository.findById(parentId)
                    .orElseThrow(() -> new RuntimeException("Categoría padre no encontrada con ID: " + parentId));
            category.setParent(parent);
        }
        return categoryRepository.save(category);
    }

    /**
     * Actualizar categoría existente
     * Incluye manejo de cambio de categoría padre
     */
    @Transactional
    public Category updateCategory(Long id, Category categoryDetails, Long newParentId) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada con ID: " + id));

        category.setName(categoryDetails.getName());
        category.setDescription(categoryDetails.getDescription());
        category.setImageUrl(categoryDetails.getImageUrl());
        category.setActive(categoryDetails.getActive());

        // Actualizar relación de categoría padre
        if (newParentId != null) {
            Category newParent = categoryRepository.findById(newParentId)
                    .orElseThrow(() -> new RuntimeException("Categoría padre no encontrada con ID: " + newParentId));
            category.setParent(newParent);
        } else {
            category.setParent(null);
        }

        return categoryRepository.save(category);
    }

    /**
     * ELIMINAR PERMANENTEMENTE una categoría (HARD DELETE)
     * ⚠️ VALIDACIÓN PROFESIONAL: Solo permite borrar si está completamente vacía
     * 
     * Reglas de negocio:
     * 1. Si tiene subcategorías → ERROR (debe moverlas o eliminarlas primero)
     * 2. Si tiene productos → ERROR (debe moverlos o eliminarlos primero)
     * 3. Si está vacía → ÉXITO (borrado físico de la base de datos)
     */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada con ID: " + id));

        // VALIDACIÓN 1: ¿Tiene subcategorías?
        long subcategoryCount = categoryRepository.countByParentId(id);
        if (subcategoryCount > 0) {
            throw new RuntimeException(
                    "❌ No se puede eliminar esta categoría porque tiene " + subcategoryCount +
                            " subcategoría(s) asociada(s). Muévelas o elimínalas primero.");
        }

        // VALIDACIÓN 2: ¿Tiene productos?
        if (category.getProducts() != null && !category.getProducts().isEmpty()) {
            int productCount = category.getProducts().size();
            throw new RuntimeException(
                    "❌ No se puede eliminar esta categoría porque tiene " + productCount +
                            " producto(s) asociado(s). Muévelos o elimínalos primero.");
        }

        // ✅ CATEGORÍA VACÍA: Borrado físico permitido
        log.info("🗑️ HARD DELETE: Eliminando permanentemente categoría '{}' (ID: {})",
                category.getName(), id);
        categoryRepository.deleteById(id);
        log.info("✅ Categoría eliminada permanentemente de la base de datos");
    }
}
