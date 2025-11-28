package com.security.repository;

import com.security.entity.SecuritySetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecuritySettingsRepository extends JpaRepository<SecuritySetting, Long> {
    
    /**
     * Buscar configuración por clave
     */
    Optional<SecuritySetting> findBySettingKey(String settingKey);
    
    /**
     * Buscar todas las configuraciones por categoría
     */
    List<SecuritySetting> findByCategory(String category);
    
    /**
     * Buscar configuraciones públicas (que pueden ser leídas por el frontend)
     */
    List<SecuritySetting> findByIsPublicTrue();
    
    /**
     * Buscar configuraciones por categoría y públicas
     */
    @Query("SELECT s FROM SecuritySetting s WHERE s.category = :category AND s.isPublic = true")
    List<SecuritySetting> findByCategoryAndPublic(@Param("category") String category);
    
    /**
     * Verificar si existe una configuración
     */
    boolean existsBySettingKey(String settingKey);
}