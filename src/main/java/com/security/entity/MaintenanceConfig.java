package com.security.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad JPA que mapea la tabla {@code maintenance_config}.
 *
 * <p>
 * Almacena la configuración del programador de mantenimiento automático.
 * Solo existe un registro (id = 1); se inicializa en el primer arranque.
 * </p>
 */
@Entity
@Table(name = "maintenance_config", schema = "ops")
public class MaintenanceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Indica si el programador automático está activo.
     * Cuando es {@code false}, {@code checkAndRunMaintenance()} retorna sin acción.
     */
    @Column(nullable = false)
    private boolean enabled = false;

    /**
     * Intervalo en horas entre ejecuciones automáticas. Rango válido: 1–24.
     */
    @Column(name = "frequency_hours", nullable = false)
    private Integer frequencyHours = 6;

    /**
     * Hora preferida del día (0-23) para ejecutar el mantenimiento.
     * Actualmente informativo; la lógica del scheduler usa
     * {@link #nextScheduledExecution} para decidir.
     */
    @Column(name = "preferred_hour", nullable = false)
    private Integer preferredHour = 2;

    /**
     * Mínimo de dead tuples para que se ejecute VACUUM automáticamente.
     * La condición es: dead_tuples {@code >=} threshold.
     */
    @Column(name = "vacuum_threshold_dead_tuples", nullable = false)
    private Integer vacuumThresholdDeadTuples = 20;

    /**
     * Porcentaje mínimo de espacio desperdiciado (bloat) para que
     * se ejecute VACUUM. Ambas condiciones (dead tuples y bloat)
     * deben cumplirse simultáneamente.
     */
    @Column(name = "vacuum_threshold_bloat_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal vacuumThresholdBloatPct = new BigDecimal("30.00");

    /** Última vez que el programador ejecutó mantenimiento automático. */
    @Column(name = "last_auto_execution")
    private LocalDateTime lastAutoExecution;

    /** Próxima ejecución programada calculada tras la última. */
    @Column(name = "next_scheduled_execution")
    private LocalDateTime nextScheduledExecution;

    /** Marca de auditoría — creación del registro. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Marca de auditoría — última modificación. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ── Constructores ─────────────────────────────────────────────────────────

    public MaintenanceConfig() {
    }

    // ── Getters y Setters ─────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getFrequencyHours() {
        return frequencyHours;
    }

    public void setFrequencyHours(Integer frequencyHours) {
        this.frequencyHours = frequencyHours;
    }

    public Integer getPreferredHour() {
        return preferredHour;
    }

    public void setPreferredHour(Integer preferredHour) {
        this.preferredHour = preferredHour;
    }

    public Integer getVacuumThresholdDeadTuples() {
        return vacuumThresholdDeadTuples;
    }

    public void setVacuumThresholdDeadTuples(Integer v) {
        this.vacuumThresholdDeadTuples = v;
    }

    public BigDecimal getVacuumThresholdBloatPct() {
        return vacuumThresholdBloatPct;
    }

    public void setVacuumThresholdBloatPct(BigDecimal v) {
        this.vacuumThresholdBloatPct = v;
    }

    public LocalDateTime getLastAutoExecution() {
        return lastAutoExecution;
    }

    public void setLastAutoExecution(LocalDateTime lastAutoExecution) {
        this.lastAutoExecution = lastAutoExecution;
    }

    public LocalDateTime getNextScheduledExecution() {
        return nextScheduledExecution;
    }

    public void setNextScheduledExecution(LocalDateTime nextScheduledExecution) {
        this.nextScheduledExecution = nextScheduledExecution;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
