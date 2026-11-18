package com.tuproyecto.airouter.controllers;

import com.tuproyecto.airouter.models.dto.ApiKeyCreatedResponse;
import com.tuproyecto.airouter.models.dto.ApiKeyResponse;
import com.tuproyecto.airouter.models.dto.CreateApiKeyRequest;
import com.tuproyecto.airouter.models.dto.PromptRequest;
import com.tuproyecto.airouter.models.dto.RouterResponse;
import com.tuproyecto.airouter.models.dto.RouterStatsResponse;
import com.tuproyecto.airouter.models.dto.UserBudgetRequest;
import com.tuproyecto.airouter.models.dto.UserBudgetResponse;
import com.tuproyecto.airouter.models.dto.UserWalletResponse;
import com.tuproyecto.airouter.models.dto.WalletTopUpRequest;
import com.tuproyecto.airouter.services.RouterService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST principal que actúa como pasarela (Gateway) para el enrutamiento
 * inteligente de prompts, la consulta del historial de peticiones evaluadas y el cálculo
 * de métricas de ahorro de costes.
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
     * Nombre de la cabecera HTTP opcional utilizada para enviar la API Key del cliente (modo BYOK)
     * e identificar cuotas de límite de tasa (Rate Limiting).
     */
    public static final String API_KEY_HEADER = "X-API-Key";

    /**
     * Servicio de negocio encargado de la evaluación de complejidad, estimación de tokens y enrutamiento.
     */
    private final RouterService routerService;

    /**
     * Evalúa un prompt entrante sin cabecera de clave de API explícita, delegando en el servicio de enrutamiento.
     *
     * @param request DTO con el identificador del usuario y el prompt a evaluar
     * @return DTO {@link RouterResponse} con el modelo seleccionado, tokens estimados y respuesta
     */
    public RouterResponse routePrompt(PromptRequest request) {
        return routePrompt(request, null);
    }

    /**
     * Evalúa un prompt entrante, estima sus tokens, selecciona el modelo LLM óptimo en coste,
     * invoca la inferencia en modo híbrido (BYOK si se envía {@code X-API-Key}, clave de servidor
     * o fallback a simulación) y registra la petición en la base de datos.
     *
     * @param request DTO con el identificador del usuario y el prompt a evaluar
     * @param apiKey  clave de API opcional proporcionada por el usuario mediante la cabecera {@code X-API-Key}
     * @return DTO {@link RouterResponse} con el modelo seleccionado, tokens estimados y respuesta real o simulada
     */
    @PostMapping("/prompt")
    @ResponseStatus(HttpStatus.OK)
    public RouterResponse routePrompt(
            @RequestBody PromptRequest request,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return routerService.routePrompt(request);
        }
        return routerService.routePrompt(request, apiKey);
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

    /**
     * Recupera las métricas agregadas de tráfico, consumo de tokens y ahorro económico estimado en USD.
     *
     * <p>Si se proporciona el parámetro opcional {@code userId}, calcula las estadísticas únicamente
     * para las peticiones de dicho usuario; en caso contrario, calcula las métricas globales del sistema.</p>
     *
     * @param userId identificador opcional del usuario para filtrar las estadísticas
     * @return DTO {@link RouterStatsResponse} con el desglose de peticiones, tokens, costes y porcentaje de ahorro
     */
    @GetMapping("/stats")
    @ResponseStatus(HttpStatus.OK)
    public RouterStatsResponse getStats(@RequestParam(required = false) String userId) {
        return routerService.getStats(userId);
    }

    /**
     * Consulta el estado actual del cortafuegos presupuestario (FinOps / Hard Budget Cap) de un usuario.
     *
     * @param userId identificador del usuario cuyo presupuesto se desea consultar
     * @return DTO {@link UserBudgetResponse} con el límite configurado, gasto acumulado, saldo y estado
     */
    @GetMapping("/budget/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public UserBudgetResponse getUserBudget(@PathVariable String userId) {
        return routerService.getUserBudget(userId);
    }

    /**
     * Crea, actualiza o desactiva el límite presupuestario máximo (en USD) de un usuario sin cabecera explícita.
     *
     * @param userId  identificador del usuario cuyo presupuesto se desea configurar
     * @param request DTO con el presupuesto máximo en USD y la preferencia de auto-downgrade al 80%
     * @return DTO {@link UserBudgetResponse} con el estado presupuestario actualizado
     */
    public UserBudgetResponse updateUserBudget(String userId, UserBudgetRequest request) {
        return updateUserBudget(userId, request, null);
    }

    /**
     * Crea, actualiza o desactiva el límite presupuestario máximo (en USD) de un usuario.
     *
     * <p>Si el cuerpo es nulo o {@code maxBudgetUsd <= 0}, se elimina el límite dejando al usuario
     * en estado {@code UNLIMITED}. Si se incluye la cabecera {@code X-API-Key} con un token del Gateway,
     * verifica que pertenezca al usuario indicado ({@code [MEDIUM-01]}).</p>
     *
     * @param userId  identificador del usuario cuyo presupuesto se desea configurar
     * @param request DTO con el presupuesto máximo en USD y la preferencia de auto-downgrade al 80%
     * @param apiKey  clave API opcional del llamante enviada en {@code X-API-Key}
     * @return DTO {@link UserBudgetResponse} con el estado presupuestario actualizado
     */
    @PutMapping("/budget/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public UserBudgetResponse updateUserBudget(
            @PathVariable String userId,
            @RequestBody(required = false) UserBudgetRequest request,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return routerService.updateUserBudget(userId, request);
        }
        return routerService.updateUserBudget(userId, request, apiKey);
    }

    /**
     * Recupera el listado de claves API emitidas para un usuario ordenadas por fecha descendente,
     * ocultando el secreto en texto plano y mostrando únicamente el prefijo enmascarado.
     *
     * @param userId identificador del usuario propietario de las claves
     * @return lista de DTOs {@link ApiKeyResponse} del usuario
     */
    @GetMapping("/keys/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public List<ApiKeyResponse> getUserApiKeys(@PathVariable String userId) {
        return routerService.getUserApiKeys(userId);
    }

    /**
     * Genera una nueva clave API para un usuario sin cabecera de autenticación explícita.
     *
     * @param request DTO con el identificador de usuario y el nombre descriptivo de la clave
     * @return DTO {@link ApiKeyCreatedResponse} incluyendo {@code rawApiKey}
     */
    public ApiKeyCreatedResponse createApiKey(CreateApiKeyRequest request) {
        return createApiKey(request, null);
    }

    /**
     * Genera una nueva clave API para un usuario ({@code air_live_<32_hex>}), persistiendo en base de datos
     * únicamente su hash SHA-256 y devolviendo por única vez el secreto en texto plano.
     *
     * @param request DTO con el identificador de usuario y el nombre descriptivo de la clave
     * @param apiKey  clave API opcional del llamante enviada en {@code X-API-Key}
     * @return DTO {@link ApiKeyCreatedResponse} incluyendo {@code rawApiKey} con estado HTTP 201 Created
     */
    @PostMapping("/keys")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyCreatedResponse createApiKey(
            @RequestBody(required = false) CreateApiKeyRequest request,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return routerService.createApiKey(request);
        }
        return routerService.createApiKey(request, apiKey);
    }

    /**
     * Revoca (desactiva) una clave API existente sin cabecera de autenticación explícita.
     *
     * @param keyId identificador único universal de la clave API a revocar
     */
    public void revokeApiKey(UUID keyId) {
        revokeApiKey(keyId, null);
    }

    /**
     * Revoca (desactiva) una clave API existente a partir de su identificador único (UUID),
     * verificando la titularidad si el llamante presenta una clave {@code air_live_...} ({@code [MEDIUM-01]}).
     *
     * @param keyId  identificador único universal de la clave API a revocar
     * @param apiKey clave API opcional del llamante enviada en {@code X-API-Key}
     */
    @DeleteMapping("/keys/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeApiKey(
            @PathVariable UUID keyId,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            routerService.revokeApiKey(keyId);
            return;
        }
        routerService.revokeApiKey(keyId, apiKey);
    }

    /**
     * Consulta el estado financiero actual de la Billetera Prepago ({@code Token Credits Wallet}) de un usuario.
     *
     * @param userId identificador del usuario titular de la billetera
     * @return DTO {@link UserWalletResponse} con el saldo disponible, total recargado y estado operativo
     */
    @GetMapping("/wallet/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public UserWalletResponse getUserWallet(@PathVariable String userId) {
        return routerService.getUserWallet(userId);
    }

    /**
     * Procesa una recarga de créditos prepago en la billetera de un usuario sin cabecera explícita.
     *
     * @param userId  identificador del usuario titular de la billetera
     * @param request DTO con el importe en USD a abonar y el método de pago
     * @return DTO {@link UserWalletResponse} con el saldo actualizado
     */
    public UserWalletResponse topUpUserWallet(String userId, WalletTopUpRequest request) {
        return topUpUserWallet(userId, request, null);
    }

    /**
     * Procesa una recarga de créditos prepago en la billetera de un usuario y activa el control
     * estricto de saldo ({@code walletEnforced = true}), verificando la titularidad si se presenta
     * una clave {@code air_live_...} ({@code [MEDIUM-01]}).
     *
     * @param userId  identificador del usuario titular de la billetera
     * @param request DTO con el importe en USD a abonar y el método de pago ({@code STRIPE_CARD} o {@code PAYPAL})
     * @param apiKey  clave API opcional del llamante enviada en {@code X-API-Key}
     * @return DTO {@link UserWalletResponse} con el saldo actualizado
     */
    @PostMapping("/wallet/{userId}/topup")
    @ResponseStatus(HttpStatus.OK)
    public UserWalletResponse topUpUserWallet(
            @PathVariable String userId,
            @RequestBody(required = false) WalletTopUpRequest request,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return routerService.topUpUserWallet(userId, request);
        }
        return routerService.topUpUserWallet(userId, request, apiKey);
    }
}
