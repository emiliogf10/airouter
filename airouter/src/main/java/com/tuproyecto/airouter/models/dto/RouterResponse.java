package com.tuproyecto.airouter.models.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida que encapsula el resultado del enrutamiento de un prompt y los metadatos
 * registrados en el historial de peticiones, incluyendo si la respuesta provino de la
 * Caché Semántica de Coste Cero ({@code cacheHit}), la latencia extremo a extremo ({@code latencyMs}),
 * la cantidad de elementos sensibles anonimizados por el Escudo DLP ({@code redactedItemsCount})
 * y los tokens ahorrados por el Compresor Automático de Prompts ({@code tokensSavedCompression}).
 *
 * @param id                     identificador único universal (UUID) del registro de la petición
 * @param userId                 identificador del usuario que originó la petición
 * @param promptContent          contenido del prompt evaluado (sanitizado por DLP y optimizado por el compresor)
 * @param modelRouted            nombre del modelo LLM seleccionado por el enrutador de costes
 * @param tokensEstimated        cantidad estimada de tokens calculada para el prompt optimizado
 * @param simulatedResponse      respuesta real o simulada generada por el motor de enrutamiento
 * @param createdAt              fecha y hora en la que se registró la petición
 * @param cacheHit               {@code true} si la respuesta se sirvió desde la Caché Semántica a coste cero; {@code false} en caso contrario
 * @param latencyMs              tiempo de respuesta extremo a extremo en milisegundos
 * @param redactedItemsCount     cantidad de elementos sensibles (PII o secretos) anonimizados por el Escudo DLP
 * @param tokensSavedCompression cantidad de tokens ahorrados gracias a la compresión y limpieza automática del prompt
 */
public record RouterResponse(
        UUID id,
        String userId,
        String promptContent,
        String modelRouted,
        Integer tokensEstimated,
        String simulatedResponse,
        LocalDateTime createdAt,
        boolean cacheHit,
        long latencyMs,
        int redactedItemsCount,
        int tokensSavedCompression
) {

    /**
     * Constructor de compatibilidad de 7 argumentos que inicializa {@code cacheHit} en {@code false},
     * {@code latencyMs} en {@code 0L}, {@code redactedItemsCount} en {@code 0} y {@code tokensSavedCompression} en {@code 0}.
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
        this(id, userId, promptContent, modelRouted, tokensEstimated, simulatedResponse, createdAt, false, 0L, 0, 0);
    }

    /**
     * Constructor de compatibilidad de 8 argumentos que inicializa {@code latencyMs} en {@code 0L},
     * {@code redactedItemsCount} en {@code 0} y {@code tokensSavedCompression} en {@code 0}.
     *
     * @param id                identificador único universal (UUID) del registro de la petición
     * @param userId            identificador del usuario que originó la petición
     * @param promptContent     contenido íntegro del prompt evaluado
     * @param modelRouted       nombre del modelo LLM seleccionado por el enrutador de costes
     * @param tokensEstimated   cantidad estimada de tokens calculada para el prompt
     * @param simulatedResponse respuesta real o simulada generada por el motor de enrutamiento
     * @param createdAt         fecha y hora en la que se registró la petición
     * @param cacheHit          indicador de acierto en la Caché Semántica
     */
    public RouterResponse(
            UUID id,
            String userId,
            String promptContent,
            String modelRouted,
            Integer tokensEstimated,
            String simulatedResponse,
            LocalDateTime createdAt,
            boolean cacheHit) {
        this(id, userId, promptContent, modelRouted, tokensEstimated, simulatedResponse, createdAt, cacheHit, 0L, 0, 0);
    }

    /**
     * Constructor de compatibilidad de 10 argumentos que inicializa {@code tokensSavedCompression} en {@code 0}.
     *
     * @param id                 identificador único universal (UUID) del registro de la petición
     * @param userId             identificador del usuario que originó la petición
     * @param promptContent      contenido íntegro del prompt evaluado
     * @param modelRouted        nombre del modelo LLM seleccionado por el enrutador de costes
     * @param tokensEstimated    cantidad estimada de tokens calculada para el prompt
     * @param simulatedResponse  respuesta real o simulada generada por el motor de enrutamiento
     * @param createdAt          fecha y hora en la que se registró la petición
     * @param cacheHit           indicador de acierto en la Caché Semántica
     * @param latencyMs          tiempo de respuesta extremo a extremo en milisegundos
     * @param redactedItemsCount cantidad de elementos sensibles anonimizados por el Escudo DLP
     */
    public RouterResponse(
            UUID id,
            String userId,
            String promptContent,
            String modelRouted,
            Integer tokensEstimated,
            String simulatedResponse,
            LocalDateTime createdAt,
            boolean cacheHit,
            long latencyMs,
            int redactedItemsCount) {
        this(id, userId, promptContent, modelRouted, tokensEstimated, simulatedResponse, createdAt, cacheHit, latencyMs, redactedItemsCount, 0);
    }
}
