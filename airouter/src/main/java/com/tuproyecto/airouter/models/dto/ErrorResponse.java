package com.tuproyecto.airouter.models.dto;

import java.time.LocalDateTime;

/**
 * DTO de respuesta estandarizado para representar errores de validación o de negocio en la API REST.
 *
 * @param message   descripción legible en español del error ocurrido
 * @param timestamp fecha y hora exacta en la que se generó la respuesta de error
 */
public record ErrorResponse(
        String message,
        LocalDateTime timestamp
) {
}
