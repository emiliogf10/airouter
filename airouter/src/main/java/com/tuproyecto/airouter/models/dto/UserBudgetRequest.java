package com.tuproyecto.airouter.models.dto;

import java.math.BigDecimal;

/**
 * DTO de entrada para configurar o actualizar el límite presupuestario (FinOps) de un usuario.
 *
 * <p>Si {@code maxBudgetUsd} es {@code null} o menor o igual a cero ({@code <= 0}), el sistema
 * interpreta que el usuario desea desactivar el cortafuegos presupuestario y operar en modo
 * ilimitado ({@code UNLIMITED}).</p>
 *
 * @param maxBudgetUsd         presupuesto máximo permitido en dólares estadounidenses (USD), o {@code null}/{@code <= 0} para ilimitado
 * @param autoDowngradeEnabled indica si debe forzarse el modelo económico ({@code gemini-2.5-flash}) al alcanzar el 80% del presupuesto (por defecto {@code true} si es {@code null})
 */
public record UserBudgetRequest(
        BigDecimal maxBudgetUsd,
        Boolean autoDowngradeEnabled
) {
}
