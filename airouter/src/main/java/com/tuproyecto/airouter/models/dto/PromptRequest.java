package com.tuproyecto.airouter.models.dto;

/**
 * DTO de entrada que representa una solicitud de evaluación y enrutamiento de un prompt.
 *
 * @param userId identificador del usuario que realiza la petición (obligatorio, máximo 255 caracteres)
 * @param prompt contenido textual del prompt que se desea enviar al LLM (obligatorio, no vacío)
 */
public record PromptRequest(
        String userId,
        String prompt
) {
}
