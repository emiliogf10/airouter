package com.tuproyecto.airouter.models.dto;

import java.math.BigDecimal;

/**
 * DTO de salida que representa el estado actual del cortafuegos presupuestario (FinOps) de un usuario,
 * incluyendo su gasto acumulado, saldo restante, porcentaje de consumo y nivel de alerta activo.
 *
 * @param userId               identificador del usuario consultado
 * @param maxBudgetUsd         límite máximo de presupuesto en USD (escalado a 6 decimales), o {@code null} si opera en modo {@code UNLIMITED}
 * @param currentSpendUsd      gasto real acumulado por el usuario en USD (escalado a 6 decimales)
 * @param remainingBudgetUsd   presupuesto disponible restante en USD ({@code max(0, maxBudgetUsd - currentSpendUsd)}), o {@code null} si es {@code UNLIMITED}
 * @param usagePercentage      porcentaje de presupuesto consumido redondeado a 2 decimales ({@code 0.0} si es {@code UNLIMITED})
 * @param autoDowngradeEnabled indica si la degradación automática al modelo rápido al superar el 80% está habilitada
 * @param status               estado operativo del cortafuegos ({@code "UNLIMITED"}, {@code "OK"}, {@code "WARNING_80_DOWNGRADE"} o {@code "EXCEEDED_100_BLOCKED"})
 */
public record UserBudgetResponse(
        String userId,
        BigDecimal maxBudgetUsd,
        BigDecimal currentSpendUsd,
        BigDecimal remainingBudgetUsd,
        double usagePercentage,
        boolean autoDowngradeEnabled,
        String status
) {
}
