package com.tuproyecto.airouter.controllers;

import com.tuproyecto.airouter.models.dto.PromptRequest;
import com.tuproyecto.airouter.models.dto.RouterResponse;
import com.tuproyecto.airouter.services.RouterService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST principal que actúa como pasarela (Gateway) para el enrutamiento
 * inteligente de prompts y la consulta del historial de peticiones evaluadas.
 *
 * <p>Expone los endpoints bajo la ruta base {@code /api/v1/router} y devuelve los DTOs
 * directamente junto con {@link ResponseStatus}, prescindiendo del uso de {@code ResponseEntity}
 * conforme a las convenciones arquitectónicas del proyecto.</p>
 */
@RestController
@RequestMapping("/api/v1/router")
@RequiredArgsConstructor
public class GatewayController {

    /**
     * Servicio de negocio encargado de la evaluación de complejidad, estimación de tokens y enrutamiento.
     */
    private final RouterService routerService;

    /**
     * Evalúa un prompt entrante, estima sus tokens, selecciona el modelo LLM óptimo en coste
     * y registra la petición en la base de datos.
     *
     * @param request DTO con el identificador del usuario y el prompt a evaluar
     * @return DTO {@link RouterResponse} con el modelo seleccionado, tokens estimados y respuesta simulada
     */
    @PostMapping("/prompt")
    @ResponseStatus(HttpStatus.OK)
    public RouterResponse routePrompt(@RequestBody PromptRequest request) {
        return routerService.routePrompt(request);
    }

    /**
     * Recupera el historial de peticiones procesadas, ordenado por fecha de creación descendente.
     *
     * <p>Si se proporciona el parámetro opcional {@code userId}, filtra los resultados para dicho
     * usuario; de lo contrario, devuelve todas las peticiones registradas.</p>
     *
     * @param userId identificador opcional del usuario para filtrar el historial
     * @return lista de DTOs {@link RouterResponse} con los registros históricos
     */
    @GetMapping("/logs")
    @ResponseStatus(HttpStatus.OK)
    public List<RouterResponse> getLogs(@RequestParam(required = false) String userId) {
        return routerService.getLogs(userId);
    }
}
