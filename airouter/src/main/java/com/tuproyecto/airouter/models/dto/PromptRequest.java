package com.tuproyecto.airouter.models.dto;

/**
 * DTO de entrada que representa una solicitud de evaluación y enrutamiento de un prompt.
 *
 * @param userId   identificador del usuario que realiza la petición (obligatorio, máximo 255 caracteres)
 * @param prompt   contenido textual del prompt que se desea enviar al LLM (obligatorio, no vacío)
 * @param provider proveedor de IA solicitado opcionalmente ({@code AUTO}, {@code GOOGLE_GEMINI}, {@code OPENAI} o {@code ANTHROPIC})
 */
public record PromptRequest(
        String userId,
        String prompt,
        String provider
) {

    /**
     * Constructor de compatibilidad de 2 argumentos que delega en el constructor canónico con {@code provider = null}
     * (equivalente a selección automática {@code AUTO} / {@code GOOGLE_GEMINI}).
     *
     * @param userId identificador del usuario que realiza la petición
     * @param prompt contenido textual del prompt que se desea enviar al LLM
     */
    public PromptRequest(String userId, String prompt) {
        this(userId, prompt, null);
    }
}
