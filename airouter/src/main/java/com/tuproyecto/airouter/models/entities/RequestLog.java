package com.tuproyecto.airouter.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad JPA que representa el registro histórico de una petición procesada
 * por el enrutador de costes de IA (AI Cost Router).
 *
 * <p>Mapea la tabla {@code request_logs} gestionada mediante Flyway.
 * Por razones de rendimiento y estabilidad en JPA, no utiliza {@code @Data},
 * {@code @ToString} ni {@code @EqualsAndHashCode} de Lombok, implementando
 * {@code equals} y {@code hashCode} manualmente basándose únicamente en el identificador.</p>
 */
@Entity
@Table(name = "request_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestLog {

    /**
     * Identificador único universal (UUID) del registro de la petición.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Identificador del usuario que originó la petición.
     */
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    /**
     * Contenido de texto completo del prompt enviado para su evaluación y enrutamiento.
     */
    @Column(name = "prompt_content", nullable = false, columnDefinition = "TEXT")
    private String promptContent;

    /**
     * Nombre o identificador del modelo de lenguaje (LLM) al que fue enrutada la petición.
     * Puede ser nulo en fases iniciales previas a la decisión de enrutamiento.
     */
    @Column(name = "model_routed", length = 255)
    private String modelRouted;

    /**
     * Cantidad estimada de tokens calculada para el prompt de la petición.
     */
    @Column(name = "tokens_estimated", nullable = false)
    private Integer tokensEstimated;

    /**
     * Fecha y hora en la que se registró la petición en el sistema.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Indica si la petición fue resuelta mediante un acierto en la Caché Semántica
     * (Zero-Cost Cache) sin invocar al proveedor LLM externo y a coste cero.
     */
    @Builder.Default
    @Column(name = "cache_hit", nullable = false)
    private Boolean cacheHit = Boolean.FALSE;

    /**
     * Constructor de compatibilidad sin el indicador explícito de acierto de caché,
     * inicializando {@code cacheHit} en {@code false}.
     *
     * @param id identificador único del registro
     * @param userId identificador del usuario
     * @param promptContent contenido íntegro del prompt
     * @param modelRouted modelo al que se enrutó la petición
     * @param tokensEstimated cantidad estimada de tokens
     * @param createdAt fecha y hora de creación
     */
    public RequestLog(
            UUID id,
            String userId,
            String promptContent,
            String modelRouted,
            Integer tokensEstimated,
            LocalDateTime createdAt) {
        this(id, userId, promptContent, modelRouted, tokensEstimated, createdAt, Boolean.FALSE);
    }

    /**
     * Asigna automáticamente la fecha y hora de creación y el valor por defecto de
     * {@code cacheHit} antes de insertar el registro en la base de datos si no se
     * han especificado previamente.
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.cacheHit == null) {
            this.cacheHit = Boolean.FALSE;
        }
    }

    /**
     * Compara la igualdad entre dos instancias de {@link RequestLog} basándose
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
        RequestLog that = (RequestLog) o;
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
