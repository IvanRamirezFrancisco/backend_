package com.security.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad JPA que mapea la tabla {@code maintenance_logs}.
 *
 * <p>
 * Registra cada operación de VACUUM ANALYZE o REINDEX ejecutada manualmente
 * desde el módulo de mantenimiento, junto con métricas de duración y resultado.
 * </p>
 *
 * <p>
 * La columna {@code rows_affected} es generada por PostgreSQL mediante
 * un trigger o expresión de tabla — se declara como no insertable/actualizable.
 * </p>
 */
@Entity
@Table(name = "maintenance_logs", indexes = {
        @Index(name = "idx_ml_executed_at", columnList = "executed_at"),
        @Index(name = "idx_ml_target_name", columnList = "target_name"),
        @Index(name = "idx_ml_operation", columnList = "operation")
})
public class MaintenanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tipo de operación: VACUUM_ANALYZE, REINDEX, ANALYZE */
    @Column(nullable = false, length = 50)
    private String operation;

    /** Nombre de la tabla o índice sobre el que se operó */
    @Column(name = "target_name", nullable = false, length = 100)
    private String targetName;

    /** Tipo de objeto: TABLE o INDEX */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    /** Nombre del administrador que ejecutó la operación */
    @Column(name = "executed_by", length = 255)
    private String executedBy;

    /** Momento de inicio de la operación */
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    /**
     * Tuplas muertas (dead tuples) antes de la operación.
     * Null para operaciones REINDEX.
     */
    @Column(name = "rows_before")
    private Integer rowsBefore;

    /**
     * Tuplas muertas después de la operación.
     * Null para operaciones REINDEX.
     */
    @Column(name = "rows_after")
    private Integer rowsAfter;

    /**
     * Diferencia calculada por PostgreSQL (rows_before - rows_after).
     * No insertable ni actualizable — generado por la BD.
     */
    @Column(name = "rows_affected", insertable = false, updatable = false)
    private Integer rowsAffected;

    /** Duración de la operación en milisegundos */
    @Column(name = "duration_ms")
    private Integer durationMs;

    /**
     * Estado de la operación: IN_PROGRESS, SUCCESS, ERROR
     */
    @Column(nullable = false, length = 20)
    private String status;

    /** Mensaje de error si status = ERROR, null en otro caso */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Notas adicionales opcionales */
    @Column(columnDefinition = "TEXT")
    private String notes;

    // ── Constructores ─────────────────────────────────────────────────────────

    public MaintenanceLog() {
    }

    // ── Getters y Setters ─────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String op) {
        this.operation = op;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String n) {
        this.targetName = n;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String t) {
        this.targetType = t;
    }

    public String getExecutedBy() {
        return executedBy;
    }

    public void setExecutedBy(String u) {
        this.executedBy = u;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime t) {
        this.executedAt = t;
    }

    public Integer getRowsBefore() {
        return rowsBefore;
    }

    public void setRowsBefore(Integer v) {
        this.rowsBefore = v;
    }

    public Integer getRowsAfter() {
        return rowsAfter;
    }

    public void setRowsAfter(Integer v) {
        this.rowsAfter = v;
    }

    public Integer getRowsAffected() {
        return rowsAffected;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer ms) {
        this.durationMs = ms;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String s) {
        this.status = s;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String m) {
        this.errorMessage = m;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String n) {
        this.notes = n;
    }
}
