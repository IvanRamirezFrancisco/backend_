package com.security.repository;

import com.security.entity.MaintenanceLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository JPA para {@link MaintenanceLog}.
 *
 * <p>
 * Provee consultas para el historial del módulo de mantenimiento
 * y para el filtro de índices reconstruidos recientemente.
 * </p>
 */
@Repository
public interface MaintenanceLogRepository extends JpaRepository<MaintenanceLog, Long> {

    /**
     * Últimos 20 registros de mantenimiento ordenados por fecha descendente.
     * Usado por el panel de historial del módulo.
     */
    List<MaintenanceLog> findTop20ByOrderByExecutedAtDesc();

    /**
     * Registros donde el nombre del objetivo, la operación y la fecha de
     * ejecución coinciden y son posteriores a {@code after}.
     *
     * <p>
     * Usado para verificar si un índice fue reconstruido exitosamente
     * en las últimas 24 h antes de mostrarlo como problemático.
     * </p>
     *
     * @param targetName nombre del índice o tabla
     * @param operation  operación ejecutada (ej: "REINDEX")
     * @param after      límite de tiempo mínimo
     */
    List<MaintenanceLog> findByTargetNameAndOperationAndExecutedAtAfter(
            String targetName,
            String operation,
            LocalDateTime after);

    /**
     * Registros filtrados por tipo de operación, paginados, ordenados por
     * fecha descendente.
     *
     * @param operation tipo de operación (VACUUM_ANALYZE, REINDEX, ANALYZE)
     * @param pageable  parámetros de paginación
     */
    List<MaintenanceLog> findByOperationOrderByExecutedAtDesc(
            String operation,
            Pageable pageable);
}
