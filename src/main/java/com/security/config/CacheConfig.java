package com.security.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuración de caché en memoria con Caffeine.
 *
 * <p>
 * Se utiliza principalmente para proteger las consultas de monitoreo de base de
 * datos
 * ({@code DatabaseMonitoringService#getMetrics()}) que ejecutan ~12 queries en
 * cada llamada.
 * Con un TTL de 15 segundos, el dashboard puede hacer polling cada 30s sin
 * saturar PostgreSQL.
 * </p>
 *
 * <p>
 * Cachés registrados:
 * </p>
 * <ul>
 * <li>{@code dbMetrics} — métricas completas del monitoreo (TTL 15s)</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("dbMetrics");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.SECONDS)
                .maximumSize(10)
                .recordStats());
        return manager;
    }
}
