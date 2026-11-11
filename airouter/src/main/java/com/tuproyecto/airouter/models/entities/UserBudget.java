package com.tuproyecto.airouter.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad JPA que representa el límite presupuestario (Hard Budget Cap / FinOps)
 * configurado para un usuario específico en el AI Cost Router.
 *
 * <p>Mapea la tabla {@code user_budgets} evolucionada mediante la migración {@code V2} de Flyway.
 * Cumpliendo con las reglas estrictas de arquitectura de datos del proyecto, prescinde de
 * {@code @Data}, {@code @ToString} y {@code @EqualsAndHashCode} de Lombok e implementa
 * {@code equals} y {@code hashCode} manualmente basándose únicamente en su clave primaria ({@code id}).</p>
 */
@Entity
@Table(name = "user_budgets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBudget {

    /**
     * Identificador único universal (UUID) del registro de presupuesto.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Identificador único del usuario al que pertenece este límite presupuestario.
     */
    @Column(name = "user_id", nullable = false, unique = true, length = 120)
    private String userId;

    /**
     * Presupuesto máximo permitido para el usuario expresado en USD con precisión de 6 decimales.
     */
    @Column(name = "max_budget_usd", nullable = false, precision = 19, scale = 6)
    private BigDecimal maxBudgetUsd;

    /**
     * Indica si al superar el umbral del 80% de consumo se fuerza automáticamente el enrutamiento
     * hacia el modelo económico ({@code gemini-2.5-flash}).
     */
    @Column(name = "auto_downgrade_enabled", nullable = false)
    private Boolean autoDowngradeEnabled;

    /**
     * Fecha y hora de la creación o última modificación del presupuesto del usuario.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Inicializa o actualiza automáticamente los valores por defecto de auditoría y configuración
     * antes de insertar o actualizar la entidad en la base de datos.
     */
    @PrePersist
    @PreUpdate
    protected void onSaveOrUpdate() {
        if (this.autoDowngradeEnabled == null) {
            this.autoDowngradeEnabled = Boolean.TRUE;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Compara la igualdad entre dos instancias de {@link UserBudget} basándose
     * exclusivamente en su clave primaria ({@code id}).
     *
     * @param o objeto con el que comparar esta entidad
     * @return {@code true} si ambos objetos son de la misma clase y tienen el mismo {@code id} no nulo
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserBudget that = (UserBudget) o;
        return id != null && Objects.equals(id, that.id);
    }

    /**
     * Calcula el código hash de la entidad basándose en la clave primaria ({@code id}).
     *
     * @return valor hash consistente para la entidad
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
