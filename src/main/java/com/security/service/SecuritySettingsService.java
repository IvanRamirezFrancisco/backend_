package com.security.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.security.repository.SecuritySettingsRepository;
import com.security.entity.SecuritySetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servicio para manejar configuraciones de seguridad desde la base de datos
 */
@Service
public class SecuritySettingsService {
    private static final Logger logger = LoggerFactory.getLogger(SecuritySettingsService.class);
    
    @Autowired
    private SecuritySettingsRepository securitySettingsRepository;
    
    /**
     * Obtener valor como String
     */
    public String getStringValue(String key, String defaultValue) {
        try {
            Optional<SecuritySetting> setting = securitySettingsRepository.findBySettingKey(key);
            return setting.map(SecuritySetting::getSettingValue).orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Obtener valor como Integer
     */
    public int getIntValue(String key, int defaultValue) {
        try {
            String value = getStringValue(key, String.valueOf(defaultValue));
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Obtener valor como Boolean
     */
    public boolean getBooleanValue(String key, boolean defaultValue) {
        try {
            String value = getStringValue(key, String.valueOf(defaultValue));
            return "true".equalsIgnoreCase(value) || "1".equals(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Obtener valor como Long
     */
    public long getLongValue(String key, long defaultValue) {
        try {
            String value = getStringValue(key, String.valueOf(defaultValue));
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Actualizar o crear una configuración
     */
    public void setSetting(String key, String value, String description, String category) {
        try {
            Optional<SecuritySetting> existingSetting = securitySettingsRepository.findBySettingKey(key);
            
            SecuritySetting setting;
            if (existingSetting.isPresent()) {
                setting = existingSetting.get();
                setting.setSettingValue(value);
            } else {
                setting = new SecuritySetting();
                setting.setSettingKey(key);
                setting.setSettingValue(value);
                setting.setDescription(description);
                setting.setCategory(category);
                setting.setDataType("STRING");
                setting.setIsPublic(false);
            }
            
            securitySettingsRepository.save(setting);
        } catch (Exception e) {
            // Log error pero no fallar
            logger.error("Error guardando configuración " + key + ": " + e.getMessage());
        }
    }
}