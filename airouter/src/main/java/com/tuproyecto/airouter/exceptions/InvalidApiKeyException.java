package com.tuproyecto.airouter.exceptions;

/**
 * Excepción de seguridad lanzada cuando un cliente presenta una clave API gestionada por el Gateway
 * ({@code air_live_...}) que no existe en el registro criptográfico SHA-256 o que ha sido revocada.
 *
 * <p>Es capturada por {@link GlobalExceptionHandler} y traducida en un estado HTTP
 * {@code 401 Unauthorized}.</p>
 */
public class InvalidApiKeyException extends RuntimeException {

    /**
     * Construye una nueva excepción de clave API inválida o revocada con el mensaje indicado.
     *
     * @param message explicación en español del motivo del rechazo de autenticación
     */
    public InvalidApiKeyException(String message) {
        super(message);
    }
}
