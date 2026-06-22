package com.security.repository;

import com.security.entity.MaintenanceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository JPA para {@link MaintenanceConfig}.
 *
 * <p>
 * Solo existe un registro con {@code id = 1}.
 * Usar {@code findById(1L)} para leerlo y {@code save()} para actualizarlo.
 * </p>
 */
@Repository
public interface MaintenanceConfigRepository extends JpaRepository<MaintenanceConfig, Long> {
    // findById(1L) y save() de JpaRepository son suficientes
}
