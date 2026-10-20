package com.tuproyecto.airouter.exceptions;

import com.tuproyecto.airouter.models.dto.ErrorResponse;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejador global de excepciones para los controladores REST de la aplicación.
 *
 * <p>Traduce las excepciones de dominio y de lectura de peticiones HTTP en respuestas JSON
 * estandarizadas mediante {@link ErrorResponse}, devolviendo el DTO directamente con
 * {@link ResponseStatus} sin emplear {@code ResponseEntity}.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Captura las excepciones de validación de prompts inválidos y devuelve un estado HTTP 400 (Bad Request).
     *
     * @param ex excepción de dominio con el detalle del error de validación
     * @return DTO {@link ErrorResponse} con el mensaje descriptivo y la marca temporal actual
     */
    @ExceptionHandler(InvalidPromptException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidPromptException(InvalidPromptException ex) {
        return new ErrorResponse(ex.getMessage(), LocalDateTime.now());
    }

    /**
     * Captura errores de deserialización o cuerpo de petición ausente/malformado y devuelve HTTP 400 (Bad Request).
     *
     * @param ex excepción producida al leer el cuerpo de la petición HTTP
     * @return DTO {@link ErrorResponse} indicando que el cuerpo de la petición es inválido
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        return new ErrorResponse("El cuerpo de la petición JSON es inválido o está ausente.", LocalDateTime.now());
    }
}
