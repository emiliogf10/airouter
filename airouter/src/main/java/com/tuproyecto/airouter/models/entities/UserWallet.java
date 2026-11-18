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
 * Entidad JPA que representa la Billetera Prepago ({@code Token Credits Wallet})
 * asociada a un usuario del AI Cost Router.
 *
 * <p>Mapea la tabla {@code user_wallets} gestionada mediante la migración {@code V5} de Flyway.
 * Permite almacenar el saldo prepago disponible en USD con precisión de 6 decimales, el total
 * histórico recargado a través de pasarelas de pago ({@code STRIPE_CARD}, {@code PAYPAL}) y si
 * el modo prepago estricto se encuentra activo ({@code walletEnforced}).</p>
 *
 * <p>Siguiendo las reglas estrictas de arquitectura de datos del proyecto, prescinde de
 * {@code @Data}, {@code @ToString} y {@code @EqualsAndHashCode} de Lombok e implementa
 * {@code equals} y {@code hashCode} manualmente basándose exclusivamente en su clave primaria ({@code id}).</p>
 */
@Entity
@Table(name = "user_wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWallet {

    /**
     * Identificador único universal (UUID) del registro de billetera.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Identificador único del usuario titular de la billetera prepago.
     */
    @Column(name = "user_id", nullable = false, unique = true, length = 120)
    private String userId;

    /**
     * Saldo prepago disponible actualmente en dólares estadounidenses (USD) con escala de 6 decimales.
     */
    @Builder.Default
    @Column(name = "balance_usd", nullable = false, precision = 19, scale = 6)
    private BigDecimal balanceUsd = BigDecimal.ZERO.setScale(6);

    /**
     * Importe total acumulado recargado históricamente por el usuario en USD con escala de 6 decimales.
     */
    @Builder.Default
    @Column(name = "total_topped_up_usd", nullable = false, precision = 19, scale = 6)
    private BigDecimal totalToppedUpUsd = BigDecimal.ZERO.setScale(6);

    /**
     * Indica si el usuario opera en modo prepago estricto ({@code true} tras realizar recargas)
     * o en modo pospago libre ({@code false} por defecto).
     */
    @Builder.Default
    @Column(name = "wallet_enforced", nullable = false)
    private Boolean walletEnforced = Boolean.FALSE;

    /**
     * Identificador de la última pasarela o método de pago utilizado (ej. {@code STRIPE_CARD} o {@code PAYPAL}).
     */
    @Column(name = "last_payment_method", length = 40)
    private String lastPaymentMethod;

    /**
     * Fecha y hora exacta de la última recarga de créditos o deducción de saldo por consumo.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Inicializa o actualiza automáticamente los valores por defecto de saldo, estado prepago
     * y marca temporal antes de insertar o actualizar la entidad en la base de datos.
     */
    @PrePersist
    @PreUpdate
    protected void onSaveOrUpdate() {
        if (this.balanceUsd == null) {
            this.balanceUsd = BigDecimal.ZERO.setScale(6);
        }
        if (this.totalToppedUpUsd == null) {
            this.totalToppedUpUsd = BigDecimal.ZERO.setScale(6);
        }
        if (this.walletEnforced == null) {
            this.walletEnforced = Boolean.FALSE;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Compara la igualdad entre dos instancias de {@link UserWallet} basándose
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
        UserWallet that = (UserWallet) o;
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
