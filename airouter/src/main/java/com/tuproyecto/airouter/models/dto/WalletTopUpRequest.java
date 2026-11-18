package com.tuproyecto.airouter.models.dto;

import java.math.BigDecimal;

/**
 * DTO de entrada para realizar una recarga de créditos prepago en la billetera de un usuario
 * a través de la pasarela de pagos ({@code Billing}).
 *
 * @param amountUsd     importe en dólares estadounidenses (USD) a abonar en la billetera (debe ser {@code > 0})
 * @param paymentMethod identificador de la pasarela de pago utilizada (ej. {@code "STRIPE_CARD"} o {@code "PAYPAL"}; por defecto {@code "STRIPE_CARD"} si es nulo o en blanco)
 */
public record WalletTopUpRequest(
        BigDecimal amountUsd,
        String paymentMethod
) {
}
