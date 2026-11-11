package com.tuproyecto.airouter.exceptions;

/**
 * Excepción de dominio lanzada cuando los datos de una petición de enrutamiento de prompt
 * o un identificador de usuario son nulos, están en blanco o incumplen las restricciones de longitud.
 */
public class InvalidPromptException extends IllegalArgumentException {

    /**
     * Construye una nueva excepción de validación de prompt con el mensaje descriptivo indicado.
     *
     * @param message explicación detallada en español del motivo de invalidez
     */
    public InvalidPromptException(String message) {
        super(message);
    }
}
