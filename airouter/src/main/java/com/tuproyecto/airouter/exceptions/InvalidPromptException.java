package com.tuproyecto.airouter.exceptions;

/**
 * Excepción de dominio lanzada cuando los datos de una petición de enrutamiento de prompt
 * son nulos, están en blanco o incumplen las restricciones de longitud establecidas.
 */
public class InvalidPromptException extends RuntimeException {

    /**
     * Construye una nueva excepción de validación de prompt con el mensaje descriptivo indicado.
     *
     * @param message explicación detallada en español del motivo de invalidez
     */
    public InvalidPromptException(String message) {
        super(message);
    }
}
