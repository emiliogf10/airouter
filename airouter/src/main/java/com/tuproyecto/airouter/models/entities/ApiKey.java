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
 * Entidad JPA que representa una clave API generada para un usuario del AI Cost Router.
 *
 * <p>Mapea la tabla {@code api_keys} gestionada mediante la migración {@code V4} de Flyway.
 * Por diseño de seguridad ({@code security-audit}), jamás almacena la clave en texto plano,
 * persistiendo únicamente un prefijo enmascarado ({@code keyPrefix}) y el hash criptográfico
 * SHA-256 ({@code keyHash}) de 64 caracteres hexadecimales.</p>
 *
 * <p>Siguiendo las reglas estrictas de arquitectura de datos del proyecto, prescinde de
 * {@code @Data}, {@code @ToString} y {@code @EqualsAndHashCode} de Lombok e implementa
 * {@code equals} y {@code hashCode} manualmente basándose exclusivamente en su clave primaria ({@code id}).</p>
 */
@Entity
@Table(name = "api_keys")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    /**
     * Identificador único universal (UUID) del registro de la clave API.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Identificador del usuario propietario de la clave API.
     */
    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    /**
     * Nombre descriptivo asignado por el usuario para identificar el propósito de la clave.
     */
    @Column(name = "key_name", nullable = false, length = 100)
    private String keyName;

    /**
     * Prefijo visible y enmascarado de la clave (ej. {@code air_live_9f8e...}) mostrado en el Dashboard.
     */
    @Column(name = "key_prefix", nullable = false, length = 32)
    private String keyPrefix;

    /**
     * Hash criptográfico SHA-256 en formato hexadecimal (64 caracteres) de la clave API completa.
     */
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    /**
     * Indica si la clave API se encuentra activa ({@code true}) o ha sido revocada ({@code false}).
     */
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = Boolean.TRUE;

    /**
     * Fecha y hora exacta en la que se emitió la clave API.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Inicializa automáticamente la fecha de creación y el estado activo por defecto
     * antes de insertar el registro en la base de datos si no se han especificado previamente.
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.active == null) {
            this.active = Boolean.TRUE;
        }
    }

    /**
     * Compara la igualdad entre dos instancias de {@link ApiKey} basándose
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
        ApiKey that = (ApiKey) o;
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
