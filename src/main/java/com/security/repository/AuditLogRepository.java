package com.security.repository;

import com.security.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository para gestión de logs de auditoría
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Buscar logs por tipo de acción
     */
    Page<AuditLog> findByAction(String action, Pageable pageable);

    /**
     * Buscar logs por tipo de entidad
     */
    Page<AuditLog> findByEntityType(String entityType, Pageable pageable);

    /**
     * Buscar logs de una entidad específica
     */
    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.createdAt DESC")
    List<AuditLog> findByEntityTypeAndEntityId(@Param("entityType") String entityType,
            @Param("entityId") Long entityId);

    /**
     * Buscar logs realizados por un usuario específico
     */
    Page<AuditLog> findByPerformedBy(String performedBy, Pageable pageable);

    /**
     * Buscar logs por ID de usuario que realizó la acción
     */
    Page<AuditLog> findByPerformedByUserId(Long userId, Pageable pageable);

    /**
     * Buscar logs en un rango de fechas
     */
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    Page<AuditLog> findByDateRange(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Buscar logs fallidos
     */
    Page<AuditLog> findByIsSuccessFalse(Pageable pageable);

    /**
     * Buscar logs con filtros múltiples
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
            "(:action IS NULL OR a.action = :action) AND " +
            "(:entityType IS NULL OR a.entityType = :entityType) AND " +
            "(:performedBy IS NULL OR a.performedBy = :performedBy) AND " +
            "(:isSuccess IS NULL OR a.isSuccess = :isSuccess) AND " +
            "a.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> findWithFilters(
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("performedBy") String performedBy,
            @Param("isSuccess") Boolean isSuccess,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Contar acciones por usuario en un período de tiempo
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.performedBy = :performedBy AND a.createdAt >= :since")
    Long countByPerformedBySince(@Param("performedBy") String performedBy, @Param("since") LocalDateTime since);

    /**
     * Obtener estadísticas de acciones más comunes
     */
    @Query("SELECT a.action, COUNT(a) as count FROM AuditLog a " +
            "WHERE a.createdAt >= :since " +
            "GROUP BY a.action ORDER BY count DESC")
    List<Object[]> getActionStatistics(@Param("since") LocalDateTime since);

    /**
     * Eliminar logs antiguos (para mantenimiento de base de datos)
     */
    void deleteByCreatedAtBefore(LocalDateTime date);
}
