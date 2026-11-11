package com.tuproyecto.airouter.exceptions;

import java.math.BigDecimal;
import lombok.Getter;

/**
 * Excepción de dominio FinOps lanzada cuando un usuario alcanza o supera el 100% de su presupuesto
 * máximo configurado ({@code maxBudgetUsd}), activando el bloqueo duro (Hard Cap) del cortafuegos.
 *
 * <p>Es capturada por {@link GlobalExceptionHandler} y traducida en una respuesta HTTP
 * {@code 402 Payment Required}.</p>
 */
@Getter
public class BudgetExceededException extends RuntimeException {

    /**
     * Identificador del usuario que ha agotado su presupuesto.
     */
    private final String userId;

    /**
     * Gasto acumulado actual del usuario en dólares estadounidenses (USD).
     */
    private final BigDecimal currentSpendUsd;

    /**
     * Límite presupuestario máximo configurado para el usuario en dólares estadounidenses (USD).
     */
    private final BigDecimal maxBudgetUsd;

    /**
     * Construye una excepción de presupuesto agotado generando automáticamente el mensaje descriptivo en español.
     *
     * @param userId          identificador del usuario bloqueado
     * @param currentSpendUsd gasto acumulado actual en USD
     * @param maxBudgetUsd    presupuesto máximo permitido en USD
     */
    public BudgetExceededException(String userId, BigDecimal currentSpendUsd, BigDecimal maxBudgetUsd) {
        this(
                String.format(
                        "Cortafuegos presupuestario activado: el usuario '%s' ha alcanzado el 100%% de su presupuesto máximo ($%s USD consumidos de $%s USD).",
                        userId,
                        currentSpendUsd != null ? currentSpendUsd.toPlainString() : "0.000000",
                        maxBudgetUsd != null ? maxBudgetUsd.toPlainString() : "0.000000"
                ),
                userId,
                currentSpendUsd,
                maxBudgetUsd
        );
    }

    /**
     * Construye una excepción de presupuesto agotado con un mensaje personalizado y los metadatos contables.
     *
     * @param message         mensaje descriptivo en español
     * @param userId          identificador del usuario bloqueado
     * @param currentSpendUsd gasto acumulado actual en USD
     * @param maxBudgetUsd    presupuesto máximo permitido en USD
     */
    public BudgetExceededException(
            String message,
            String userId,
            BigDecimal currentSpendUsd,
            BigDecimal maxBudgetUsd) {
        super(message);
        this.userId = userId;
        this.currentSpendUsd = currentSpendUsd;
        this.maxBudgetUsd = maxBudgetUsd;
    }
}
