package com.tuproyecto.airouter.models.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida que encapsula el resultado del enrutamiento de un prompt y los metadatos
 * registrados en el historial de peticiones, incluyendo si la respuesta provino de la
 * Caché Semántica de Coste Cero ({@code cacheHit}).
 *
 * @param id                identificador único universal (UUID) del registro de la petición
 * @param userId            identificador del usuario que originó la petición
 * @param promptContent     contenido íntegro del prompt evaluado
 * @param modelRouted       nombre del modelo LLM seleccionado por el enrutador de costes
 * @param tokensEstimated   cantidad estimada de tokens calculada para el prompt
 * @param simulatedResponse respuesta real o simulada generada por el motor de enrutamiento
 * @param createdAt         fecha y hora en la que se registró la petición
 * @param cacheHit          {@code true} si la respuesta se sirvió desde la Caché Semántica a coste cero; {@code false} en caso contrario
 */
public record RouterResponse(
        UUID id,
        String userId,
        String promptContent,
        String modelRouted,
        Integer tokensEstimated,
        String simulatedResponse,
        LocalDateTime createdAt,
        boolean cacheHit
) {

    /**
     * Constructor de compatibilidad hacia atrás que inicializa {@code cacheHit} en {@code false}.
     *
     * @param id                identificador único universal (UUID) del registro de la petición
     * @param userId            identificador del usuario que originó la petición
     * @param promptContent     contenido íntegro del prompt evaluado
     * @param modelRouted       nombre del modelo LLM seleccionado por el enrutador de costes
     * @param tokensEstimated   cantidad estimada de tokens calculada para el prompt
     * @param simulatedResponse respuesta real o simulada generada por el motor de enrutamiento
     * @param createdAt         fecha y hora en la que se registró la petición
     */
    public RouterResponse(
            UUID id,
            String userId,
            String promptContent,
            String modelRouted,
            Integer tokensEstimated,
            String simulatedResponse,
            LocalDateTime createdAt) {
        this(id, userId, promptContent, modelRouted, tokensEstimated, simulatedResponse, createdAt, false);
    }
}
