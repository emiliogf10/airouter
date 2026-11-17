package com.tuproyecto.airouter.models.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida seguro para listar las claves API de un usuario en el Dashboard.
 *
 * <p>Por razones de seguridad ({@code security-audit}), jamás incluye la clave en texto plano
 * ({@code rawApiKey}) ni su hash completo ({@code keyHash}), exponiendo únicamente el prefijo
 * enmascarado ({@code keyPrefix}).</p>
 *
 * @param id        identificador único universal (UUID) de la clave API
 * @param userId    identificador del usuario propietario
 * @param keyName   nombre descriptivo de la clave
 * @param keyPrefix prefijo enmascarado visible (ej. {@code air_live_9f8e...})
 * @param active    {@code true} si la clave está activa; {@code false} si ha sido revocada
 * @param createdAt fecha y hora de creación de la clave
 */
public record ApiKeyResponse(
        UUID id,
        String userId,
        String keyName,
        String keyPrefix,
        boolean active,
        LocalDateTime createdAt
) {
}
