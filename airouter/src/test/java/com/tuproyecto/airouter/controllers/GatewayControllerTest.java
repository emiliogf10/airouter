package com.tuproyecto.airouter.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tuproyecto.airouter.models.dto.PromptRequest;
import com.tuproyecto.airouter.models.dto.RouterResponse;
import com.tuproyecto.airouter.repositories.RequestLogRepository;
import com.tuproyecto.airouter.repositories.UserBudgetRepository;
import com.tuproyecto.airouter.services.RouterService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Pruebas de integración extremo a extremo para {@link GatewayController} y {@link com.tuproyecto.airouter.exceptions.GlobalExceptionHandler}.
 *
 * <p>Verifica el comportamiento HTTP de los endpoints {@code POST /api/v1/router/prompt},
 * {@code GET /api/v1/router/logs} y {@code GET /api/v1/router/stats}, la propagación de la cabecera
 * {@code X-API-Key} (modo BYOK), el formato de errores {@code ErrorResponse} ante entradas inválidas
 * y la persistencia real en la base de datos H2 gestionada por Flyway.</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "airouter.gemini.base-url=http://127.0.0.1:1",
        "airouter.gemini.timeout-seconds=1"
})
class GatewayControllerTest {

    /**
     * Contexto web de Spring utilizado para construir la instancia de {@link MockMvc}.
     */
    @Autowired
    private WebApplicationContext webApplicationContext;

    /**
     * Repositorio JPA de logs utilizado para limpiar el estado antes de cada prueba.
     */
    @Autowired
    private RequestLogRepository requestLogRepository;

    /**
     * Repositorio JPA de presupuestos por usuario utilizado para limpiar el estado antes de cada prueba.
     */
    @Autowired
    private UserBudgetRepository userBudgetRepository;

    /**
     * Cliente de pruebas MVC para simular peticiones HTTP contra el controlador.
     */
    private MockMvc mockMvc;

    /**
     * Inicializa {@link MockMvc} y vacía las tablas {@code request_logs} y {@code user_budgets} antes de cada test.
     */
    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
        this.requestLogRepository.deleteAll();
        this.userBudgetRepository.deleteAll();
    }

    /**
     * Verifica que {@code POST /api/v1/router/prompt} procese un prompt sencillo, lo enrute a
     * {@code gemini-2.5-flash}, lo guarde en H2 y devuelva HTTP 200 OK con el DTO completo.
     *
     * @throws Exception si ocurre un error durante la petición simulada
     */
    @Test
    @DisplayName("POST /api/v1/router/prompt debe procesar un prompt sencillo, persistirlo en H2 y devolver 200 OK")
    void shouldRouteSimplePromptAndPersistInDatabase() throws Exception {
        String payload = """
                {
                    "userId": "qa-user-1",
                    "prompt": "Hola, ¿cómo estás?"
                }
                """;

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.userId", is("qa-user-1")))
                .andExpect(jsonPath("$.promptContent", is("Hola, ¿cómo estás?")))
                .andExpect(jsonPath("$.modelRouted", is(RouterService.MODEL_FAST)))
                .andExpect(jsonPath("$.tokensEstimated", is(5)))
                .andExpect(jsonPath("$.simulatedResponse", containsString(RouterService.MODEL_FAST)))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    /**
     * Verifica que {@code POST /api/v1/router/prompt} seleccione el modelo avanzado
     * ({@code gemini-2.5-pro}) cuando el prompt contiene palabras clave de alta complejidad.
     *
     * @throws Exception si ocurre un error durante la petición simulada
     */
    @Test
    @DisplayName("POST /api/v1/router/prompt debe enrutar a gemini-2.5-pro cuando contiene palabra clave compleja")
    void shouldRouteComplexPromptToProModel() throws Exception {
        String payload = """
                {
                    "userId": "qa-architect",
                    "prompt": "Necesito refactorizar y optimizar la arquitectura del microservicio"
                }
                """;

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("qa-architect")))
                .andExpect(jsonPath("$.modelRouted", is(RouterService.MODEL_PRO)))
                .andExpect(jsonPath("$.simulatedResponse", containsString(RouterService.MODEL_PRO)));
    }

    /**
     * Verifica que cuando el cliente envía la cabecera {@code X-API-Key} en {@code POST /api/v1/router/prompt},
     * {@link GatewayController} delegue en {@link RouterService#routePrompt(PromptRequest, String)} pasando
     * la clave recibida para activar el modo híbrido BYOK.
     *
     * @throws Exception si ocurre un error durante la petición simulada
     */
    @Test
    @DisplayName("POST /api/v1/router/prompt con cabecera X-API-Key debe delegar en routerService.routePrompt(request, apiKey)")
    void shouldDelegateToRouterServiceWithApiKeyWhenHeaderIsPresent() throws Exception {
        RouterService mockRouterService = mock(RouterService.class);
        MockMvc standaloneMvc = MockMvcBuilders.standaloneSetup(new GatewayController(mockRouterService)).build();

        PromptRequest expectedRequest = new PromptRequest("byok-client", "Hola Gemini real");
        RouterResponse expectedResponse = new RouterResponse(
                UUID.randomUUID(),
                "byok-client",
                "Hola Gemini real",
                RouterService.MODEL_FAST,
                4,
                "Respuesta directa de Google Gemini API",
                LocalDateTime.now()
        );

        when(mockRouterService.routePrompt(expectedRequest, "AIzaSyClientHeaderKey"))
                .thenReturn(expectedResponse);

        String payload = """
                {
                    "userId": "byok-client",
                    "prompt": "Hola Gemini real"
                }
                """;

        standaloneMvc.perform(post("/api/v1/router/prompt")
                        .header(GatewayController.API_KEY_HEADER, "AIzaSyClientHeaderKey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("byok-client")))
                .andExpect(jsonPath("$.modelRouted", is(RouterService.MODEL_FAST)))
                .andExpect(jsonPath("$.simulatedResponse", is("Respuesta directa de Google Gemini API")));

        verify(mockRouterService, times(1)).routePrompt(expectedRequest, "AIzaSyClientHeaderKey");
    }

    /**
     * Verifica que {@code GET /api/v1/router/logs} devuelva el historial en orden cronológico
     * descendente tanto de forma global como filtrando por el parámetro opcional {@code userId}.
     *
     * @throws Exception si ocurre un error durante la petición simulada
     */
    @Test
    @DisplayName("GET /api/v1/router/logs debe devolver el historial ordenado descendentemente y permitir filtrar por userId")
    void shouldPersistAndReturnLogsOrderedAndFilteredByUserId() throws Exception {
        String firstPayload = """
                {
                    "userId": "alice",
                    "prompt": "Primer prompt de Alice"
                }
                """;
        String secondPayload = """
                {
                    "userId": "bob",
                    "prompt": "Quiero analizar en profundidad este algoritmo"
                }
                """;
        String thirdPayload = """
                {
                    "userId": "alice",
                    "prompt": "Segundo prompt de Alice"
                }
                """;

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPayload))
                .andExpect(status().isOk());

        Thread.sleep(15);

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondPayload))
                .andExpect(status().isOk());

        Thread.sleep(15);

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(thirdPayload))
                .andExpect(status().isOk());

        // Consulta global sin userId -> 3 registros en orden descendente (más reciente primero)
        mockMvc.perform(get("/api/v1/router/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].promptContent", is("Segundo prompt de Alice")))
                .andExpect(jsonPath("$[1].promptContent", is("Quiero analizar en profundidad este algoritmo")))
                .andExpect(jsonPath("$[2].promptContent", is("Primer prompt de Alice")));

        // Consulta con userId en blanco -> equivale a consulta global (3 registros)
        mockMvc.perform(get("/api/v1/router/logs").param("userId", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // Consulta filtrada por usuario "alice" -> 2 registros ordenados del más reciente al más antiguo
        mockMvc.perform(get("/api/v1/router/logs").param("userId", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].promptContent", is("Segundo prompt de Alice")))
                .andExpect(jsonPath("$[1].promptContent", is("Primer prompt de Alice")));
    }

    /**
     * Verifica de extremo a extremo el endpoint {@code GET /api/v1/router/stats} con H2:
     * primero comprueba que en estado vacío devuelve todos los contadores a 0, y luego tras
     * procesar peticiones para distintos usuarios verifica las métricas globales y filtradas por {@code userId}.
     *
     * @throws Exception si ocurre un error durante la petición simulada
     */
    @Test
    @DisplayName("GET /api/v1/router/stats debe devolver métricas a cero en estado vacío y calcular ahorros globales y por usuario tras enrutar")
    void shouldReturnEmptyStatsInitiallyAndAccurateStatsAfterRouting() throws Exception {
        // 1. Base de datos vacía -> todos los contadores a 0
        mockMvc.perform(get("/api/v1/router/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests", is(0)))
                .andExpect(jsonPath("$.totalTokensEstimated", is(0)))
                .andExpect(jsonPath("$.fastModelRequests", is(0)))
                .andExpect(jsonPath("$.proModelRequests", is(0)))
                .andExpect(jsonPath("$.fastModelTokens", is(0)))
                .andExpect(jsonPath("$.proModelTokens", is(0)))
                .andExpect(jsonPath("$.estimatedActualCostUsd", is(0.0)))
                .andExpect(jsonPath("$.estimatedBaselineProCostUsd", is(0.0)))
                .andExpect(jsonPath("$.estimatedSavingsUsd", is(0.0)))
                .andExpect(jsonPath("$.savingsPercentage", is(0.0)));

        // 2. Enviar petición corta de "alice" (16 caracteres = 4 tokens -> gemini-2.5-flash)
        String aliceFastPayload = """
                {
                    "userId": "alice",
                    "prompt": "1234567890123456"
                }
                """;
        // 3. Enviar petición compleja de "bob" (16 caracteres con "optimizar" = 4 tokens -> gemini-2.5-pro)
        String bobProPayload = """
                {
                    "userId": "bob",
                    "prompt": "Quiero optimizar"
                }
                """;

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aliceFastPayload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bobProPayload))
                .andExpect(status().isOk());

        // 4. Estadísticas globales: 2 peticiones, 8 tokens (4 flash + 4 pro) -> 44.0% de ahorro
        mockMvc.perform(get("/api/v1/router/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests", is(2)))
                .andExpect(jsonPath("$.totalTokensEstimated", is(8)))
                .andExpect(jsonPath("$.fastModelRequests", is(1)))
                .andExpect(jsonPath("$.proModelRequests", is(1)))
                .andExpect(jsonPath("$.fastModelTokens", is(4)))
                .andExpect(jsonPath("$.proModelTokens", is(4)))
                .andExpect(jsonPath("$.estimatedActualCostUsd", is(0.000006)))
                .andExpect(jsonPath("$.estimatedBaselineProCostUsd", is(0.00001)))
                .andExpect(jsonPath("$.estimatedSavingsUsd", is(0.000004)))
                .andExpect(jsonPath("$.savingsPercentage", is(44.0)));

        // 5. Estadísticas filtradas para "alice": 1 petición flash (4 tokens) -> 88.0% de ahorro
        mockMvc.perform(get("/api/v1/router/stats").param("userId", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests", is(1)))
                .andExpect(jsonPath("$.totalTokensEstimated", is(4)))
                .andExpect(jsonPath("$.fastModelRequests", is(1)))
                .andExpect(jsonPath("$.proModelRequests", is(0)))
                .andExpect(jsonPath("$.fastModelTokens", is(4)))
                .andExpect(jsonPath("$.proModelTokens", is(0)))
                .andExpect(jsonPath("$.savingsPercentage", is(88.0)));
    }

    /**
     * Comprueba que {@code POST /api/v1/router/prompt} devuelva HTTP 400 Bad Request y cuerpo
     * {@code ErrorResponse} cuando {@code userId} o {@code prompt} están en blanco.
     *
     * @throws Exception si ocurre un error durante la petición simulada
     */
    @Test
    @DisplayName("POST /api/v1/router/prompt debe devolver 400 Bad Request y ErrorResponse cuando userId o prompt están vacíos")
    void shouldReturnBadRequestWhenPayloadFieldsAreBlank() throws Exception {
        String blankUserPayload = """
                {
                    "userId": "   ",
                    "prompt": "Hola"
                }
                """;

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankUserPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("userId")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        String blankPromptPayload = """
                {
                    "userId": "user-1",
                    "prompt": "   "
                }
                """;

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankPromptPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("prompt")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Comprueba que {@code POST /api/v1/router/prompt} devuelva HTTP 400 Bad Request cuando
     * el cuerpo JSON está malformado o vacío.
     *
     * @throws Exception si ocurre un error durante la petición simulada
     */
    @Test
    @DisplayName("POST /api/v1/router/prompt debe devolver 400 Bad Request y ErrorResponse cuando el JSON está malformado o vacío")
    void shouldReturnBadRequestWhenJsonIsMalformedOrEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ json_invalido: "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Verifica de extremo a extremo los endpoints FinOps {@code GET /api/v1/router/budget/{userId}} y
     * {@code PUT /api/v1/router/budget/{userId}}, así como el bloqueo HTTP {@code 402 Payment Required}
     * en {@code POST /api/v1/router/prompt} cuando el usuario alcanza el 100% de su presupuesto.
     *
     * @throws Exception si ocurre un error durante las peticiones simuladas
     */
    @Test
    @DisplayName("GET/PUT /api/v1/router/budget/{userId} y bloqueo HTTP 402 Payment Required al superar el 100% del presupuesto")
    void shouldManageBudgetEndpointsAndReturn402PaymentRequiredWhenBudgetIsExhausted() throws Exception {
        // 1. Consulta inicial sin presupuesto configurado -> 200 OK con status UNLIMITED
        mockMvc.perform(get("/api/v1/router/budget/finops-qa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("finops-qa")))
                .andExpect(jsonPath("$.status", is(RouterService.BUDGET_STATUS_UNLIMITED)));

        // 2. Configurar presupuesto micro de $0.000001 USD mediante PUT -> 200 OK con status OK
        String budgetPayload = """
                {
                    "maxBudgetUsd": 0.000001,
                    "autoDowngradeEnabled": true
                }
                """;

        mockMvc.perform(put("/api/v1/router/budget/finops-qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("finops-qa")))
                .andExpect(jsonPath("$.maxBudgetUsd", is(0.000001)))
                .andExpect(jsonPath("$.status", is(RouterService.BUDGET_STATUS_OK)));

        // 3. Primera petición de prompt consume >= $0.000001 USD (ej. 16 caracteres con "arquitectura" = 4 tokens en Pro = $0.000005 USD)
        String firstPromptPayload = """
                {
                    "userId": "finops-qa",
                    "prompt": "Optimizar arquitectura"
                }
                """;

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPromptPayload))
                .andExpect(status().isOk());

        // 4. Segunda petición de prompt con el presupuesto ya agotado al 100% -> 402 Payment Required + ErrorResponse
        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPromptPayload))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message", containsString("Cortafuegos presupuestario activado")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Verifica de extremo a extremo la Caché Semántica de Prompts en {@code POST /api/v1/router/prompt}
     * y su impacto en {@code GET /api/v1/router/stats}: al enviar dos veces el mismo prompt con distinta
     * capitalización y espaciado, la primera petición responde con {@code cacheHit: false}, la segunda
     * responde con {@code cacheHit: true}, y {@code /stats} refleja {@code cacheHits: 1}.
     *
     * @throws Exception si ocurre un error durante las peticiones simuladas
     */
    @Test
    @DisplayName("POST /api/v1/router/prompt debe devolver cacheHit=false en la 1ª petición y cacheHit=true en la 2ª equivalente, reflejando cacheHits=1 en /stats")
    void shouldReturnCacheHitTrueOnRepeatedNormalizedPromptAndIncrementCacheHitsInStats() throws Exception {
        String firstPayload = """
                {
                    "userId": "cache-e2e-user",
                    "prompt": "  ¿Qué día   es HOY? "
                }
                """;
        String secondEquivalentPayload = """
                {
                    "userId": "cache-e2e-user",
                    "prompt": "¿qué día es hoy?"
                }
                """;

        // 1ª petición -> Cache Miss (cacheHit: false)
        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cacheHit", is(false)))
                .andExpect(jsonPath("$.modelRouted", is(RouterService.MODEL_FAST)));

        // 2ª petición equivalente -> Cache Hit (cacheHit: true)
        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondEquivalentPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cacheHit", is(true)))
                .andExpect(jsonPath("$.modelRouted", is(RouterService.MODEL_FAST)));

        // Verificar en /stats que totalRequests = 2 y cacheHits = 1
        mockMvc.perform(get("/api/v1/router/stats").param("userId", "cache-e2e-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests", is(2)))
                .andExpect(jsonPath("$.cacheHits", is(1)));
    }
}
