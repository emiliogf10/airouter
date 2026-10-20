package com.tuproyecto.airouter.models.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida que encapsula el resultado del enrutamiento de un prompt y los metadatos
 * registrados en el historial de peticiones.
 *
 * @param id                identificador único universal (UUID) del registro de la petición
 * @param userId            identificador del usuario que originó la petición
 * @param promptContent     contenido íntegro del prompt evaluado
 * @param modelRouted       nombre del modelo LLM seleccionado por el enrutador de costes
 * @param tokensEstimated   cantidad estimada de tokens calculada para el prompt
 * @param simulatedResponse respuesta simulada generada por el motor de enrutamiento
 * @param createdAt         fecha y hora en la que se registró la petición
 */
public record RouterResponse(
        UUID id,
        String userId,
        String promptContent,
        String modelRouted,
        Integer tokensEstimated,
        String simulatedResponse,
        LocalDateTime createdAt
) {
}
