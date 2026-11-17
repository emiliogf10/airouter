package com.tuproyecto.airouter.models.dto;

/**
 * DTO de entrada para solicitar la generación de una nueva clave API gestionada por el Gateway.
 *
 * @param userId  identificador del usuario propietario de la clave (obligatorio, máximo 120 caracteres)
 * @param keyName nombre descriptivo asignado a la clave (obligatorio, máximo 100 caracteres)
 */
public record CreateApiKeyRequest(
        String userId,
        String keyName
) {
}
