package com.tuproyecto.airouter.models.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de salida que representa el estado financiero actual de la Billetera Prepago
 * ({@code Token Credits Wallet}) de un usuario.
 *
 * @param userId            identificador único del usuario titular
 * @param balanceUsd        saldo disponible actual en dólares estadounidenses (USD, escala de 6 decimales)
 * @param totalToppedUpUsd  importe total recargado históricamente por el usuario en USD (escala de 6 decimales)
 * @param walletEnforced    {@code true} si la cuenta opera en modo prepago estricto; {@code false} si opera en modo pospago libre
 * @param lastPaymentMethod última pasarela de pago empleada ({@code "STRIPE_CARD"}, {@code "PAYPAL"} o {@code null})
 * @param updatedAt         fecha y hora de la última recarga o deducción de saldo ({@code null} si nunca ha recargado)
 * @param status            estado operativo de la billetera ({@code "POSTPAID_UNLIMITED"}, {@code "PREPAID_ACTIVE"} o {@code "PREPAID_DEPLETED"})
 */
public record UserWalletResponse(
        String userId,
        BigDecimal balanceUsd,
        BigDecimal totalToppedUpUsd,
        boolean walletEnforced,
        String lastPaymentMethod,
        LocalDateTime updatedAt,
        String status
) {
}
