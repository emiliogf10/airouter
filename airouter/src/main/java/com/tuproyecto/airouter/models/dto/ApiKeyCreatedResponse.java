package com.tuproyecto.airouter.models.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida devuelto exclusivamente en el momento de creación de una nueva clave API.
 *
 * <p>Incluye por única vez el valor en texto plano ({@code rawApiKey}) para que el usuario pueda
 * copiarlo antes de que deje de estar disponible, ya que en base de datos solo se almacena su
 * hash SHA-256.</p>
 *
 * @param id        identificador único universal (UUID) de la clave API creada
 * @param userId    identificador del usuario propietario
 * @param keyName   nombre descriptivo asignado a la clave
 * @param keyPrefix prefijo enmascarado visible (ej. {@code air_live_9f8e...})
 * @param rawApiKey secreto completo en texto plano ({@code air_live_<32_hex>}), mostrado una sola vez
 * @param active    {@code true} indicando que la clave nace activa
 * @param createdAt fecha y hora exacta de emisión
 */
public record ApiKeyCreatedResponse(
        UUID id,
        String userId,
        String keyName,
        String keyPrefix,
        String rawApiKey,
        boolean active,
        LocalDateTime createdAt
) {
}
