package com.security.controller.admin;

import com.security.dto.BrandDTO;
import com.security.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller para administración de marcas
 * Solo accesible por usuarios con rol ADMIN
 * CORS se maneja globalmente en SecurityConfig
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/brands")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    /**
     * Obtener todas las marcas con paginación
     * GET /api/admin/brands?page=0&size=20&sortBy=name&sortDir=asc
     */
    @GetMapping
    public ResponseEntity<BrandDTO.BrandListResponse> getAllBrands(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        log.info("Obteniendo marcas - página: {}, tamaño: {}", page, size);
        BrandDTO.BrandListResponse response = brandService.getAllBrands(page, size, sortBy, sortDir);
        return ResponseEntity.ok(response);
    }

    /**
     * Buscar marcas con filtros
     * GET /api/admin/brands/search?name=fender&active=true&page=0&size=20
     */
    @GetMapping("/search")
    public ResponseEntity<BrandDTO.BrandListResponse> searchBrands(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String countryOrigin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Buscando marcas con filtros - name: {}, active: {}", name, active);
        BrandDTO.BrandListResponse response = brandService.searchBrands(name, active, countryOrigin, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtener marcas activas (para select en formulario de productos)
     * GET /api/admin/brands/active
     */
    @GetMapping("/active")
    public ResponseEntity<List<BrandDTO.BrandBasicInfo>> getActiveBrands() {
        log.info("Obteniendo marcas activas");
        List<BrandDTO.BrandBasicInfo> brands = brandService.getActiveBrands();
        return ResponseEntity.ok(brands);
    }

    /**
     * Obtener marca por ID
     * GET /api/admin/brands/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<BrandDTO.BrandResponse> getBrandById(@PathVariable Long id) {
        log.info("Obteniendo marca con ID: {}", id);
        BrandDTO.BrandResponse brand = brandService.getBrandById(id);
        return ResponseEntity.ok(brand);
    }

    /**
     * Crear nueva marca
     * POST /api/admin/brands
     */
    @PostMapping
    public ResponseEntity<BrandDTO.BrandResponse> createBrand(@Valid @RequestBody BrandDTO.BrandRequest request) {
        log.info("Creando nueva marca: {}", request.getName());
        BrandDTO.BrandResponse brand = brandService.createBrand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(brand);
    }

    /**
     * Actualizar marca existente
     * PUT /api/admin/brands/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<BrandDTO.BrandResponse> updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody BrandDTO.BrandRequest request) {

        log.info("Actualizando marca con ID: {}", id);
        BrandDTO.BrandResponse brand = brandService.updateBrand(id, request);
        return ResponseEntity.ok(brand);
    }

    /**
     * Eliminar marca
     * DELETE /api/admin/brands/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteBrand(@PathVariable Long id) {
        log.info("Eliminando marca con ID: {}", id);
        brandService.deleteBrand(id);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Marca eliminada exitosamente");
        return ResponseEntity.ok(response);
    }

    /**
     * Activar/Desactivar marca
     * PATCH /api/admin/brands/{id}/toggle-status
     */
    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<BrandDTO.BrandResponse> toggleBrandStatus(@PathVariable Long id) {
        log.info("Cambiando estado de marca con ID: {}", id);
        BrandDTO.BrandResponse brand = brandService.toggleBrandStatus(id);
        return ResponseEntity.ok(brand);
    }

    /**
     * Actualizar contador de productos de una marca
     * PATCH /api/admin/brands/{id}/update-count
     */
    @PatchMapping("/{id}/update-count")
    public ResponseEntity<Map<String, String>> updateProductCount(@PathVariable Long id) {
        log.info("Actualizando contador de productos para marca ID: {}", id);
        brandService.updateProductCount(id);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Contador actualizado exitosamente");
        return ResponseEntity.ok(response);
    }
}
