package com.tuproyecto.airouter.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tuproyecto.airouter.clients.GeminiRestClient;
import com.tuproyecto.airouter.exceptions.BudgetExceededException;
import com.tuproyecto.airouter.exceptions.InvalidPromptException;
import com.tuproyecto.airouter.models.dto.PromptRequest;
import com.tuproyecto.airouter.models.dto.RouterResponse;
import com.tuproyecto.airouter.models.dto.RouterStatsResponse;
import com.tuproyecto.airouter.models.dto.UserBudgetRequest;
import com.tuproyecto.airouter.models.dto.UserBudgetResponse;
import com.tuproyecto.airouter.models.entities.RequestLog;
import com.tuproyecto.airouter.models.entities.UserBudget;
import com.tuproyecto.airouter.repositories.RequestLogRepository;
import com.tuproyecto.airouter.repositories.UserBudgetRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pruebas unitarias exhaustivas para {@link RouterService} verificando la lógica de estimación
 * de tokens, casos borde de umbrales de enrutamiento, palabras clave complejas, validaciones
 * de dominio, los 3 niveles del modo híbrido (BYOK, clave de servidor y fallback a simulación),
 * cálculo de estadísticas de ahorro y persistencia en el repositorio.
 */
@ExtendWith(MockitoExtension.class)
class RouterServiceTest {

    /**
     * Mock del repositorio JPA de logs de peticiones para aislar las pruebas de la base de datos.
     */
    @Mock
    private RequestLogRepository requestLogRepository;

    /**
     * Instancia del servicio bajo prueba con sus dependencias simuladas inyectadas.
     */
    @InjectMocks
    private RouterService routerService;

    /**
     * Verifica que un prompt corto y sin palabras clave complejas se enrute al modelo económico
     * ({@code gemini-2.5-flash}), se calcule su estimación de tokens y se persista en el repositorio.
     */
    @Test
    @DisplayName("Debe enrutar al modelo económico (gemini-2.5-flash) cuando el prompt es corto y sencillo")
    void shouldRouteToFastModelWhenPromptIsShortAndSimple() {
        PromptRequest request = new PromptRequest("user-123", "Hola, ¿qué día es hoy?");
        UUID generatedId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(generatedId);
            arg.setCreatedAt(now);
            return arg;
        });

        RouterResponse response = routerService.routePrompt(request);

        assertNotNull(response);
        assertEquals(generatedId, response.id());
        assertEquals("user-123", response.userId());
        assertEquals("Hola, ¿qué día es hoy?", response.promptContent());
        assertEquals(RouterService.MODEL_FAST, response.modelRouted());
        assertEquals(6, response.tokensEstimated());
        assertTrue(response.simulatedResponse().contains(RouterService.MODEL_FAST));

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).save(captor.capture());
        assertEquals(RouterService.MODEL_FAST, captor.getValue().getModelRouted());
    }

    /**
     * Comprueba el comportamiento exacto en la frontera del umbral de tokens ({@code 50} tokens = 200 caracteres
     * frente a {@code 51} tokens = 201 caracteres).
     */
    @Test
    @DisplayName("Debe enrutar a gemini-2.5-flash en el límite exacto de 50 tokens (200 caracteres) y a gemini-2.5-pro con 51 tokens (201 caracteres)")
    void shouldRespectExactTokenThresholdBoundary() {
        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });

        // Exactamente 200 caracteres -> 50 tokens -> gemini-2.5-flash
        String exactThresholdPrompt = "a".repeat(200);
        RouterResponse fastResponse = routerService.routePrompt(new PromptRequest("user-boundary", exactThresholdPrompt));
        assertEquals(50, fastResponse.tokensEstimated());
        assertEquals(RouterService.MODEL_FAST, fastResponse.modelRouted());

        // Exactamente 201 caracteres -> ceil(201 / 4.0) = 51 tokens -> gemini-2.5-pro
        String overThresholdPrompt = "a".repeat(201);
        RouterResponse proResponse = routerService.routePrompt(new PromptRequest("user-boundary", overThresholdPrompt));
        assertEquals(51, proResponse.tokensEstimated());
        assertEquals(RouterService.MODEL_PRO, proResponse.modelRouted());
    }

    /**
     * Verifica que los espacios y tabulaciones iniciales o finales se ignoren mediante {@code trim()}
     * antes de calcular la estimación de tokens en el límite del umbral.
     */
    @Test
    @DisplayName("Debe aplicar trim() al estimar tokens en prompts con espacios iniciales y finales en el límite exacto")
    void shouldTrimPromptWhenEstimatingTokensAtBoundary() {
        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });

        // 200 caracteres rodeados de espacios -> tras trim() son 200 caracteres = 50 tokens -> gemini-2.5-flash
        String padded200Chars = "   \t\n " + "x".repeat(200) + " \n\t   ";
        RouterResponse response50 = routerService.routePrompt(new PromptRequest("user-trim", padded200Chars));
        assertEquals(50, response50.tokensEstimated());
        assertEquals(RouterService.MODEL_FAST, response50.modelRouted());

        // 201 caracteres rodeados de espacios -> tras trim() son 201 caracteres = 51 tokens -> gemini-2.5-pro
        String padded201Chars = "   " + "x".repeat(201) + "   ";
        RouterResponse response51 = routerService.routePrompt(new PromptRequest("user-trim", padded201Chars));
        assertEquals(51, response51.tokensEstimated());
        assertEquals(RouterService.MODEL_PRO, response51.modelRouted());
    }

    /**
     * Verifica de forma parametrizada que cualquier palabra o frase clave de alta complejidad
     * fuerce el enrutamiento hacia el modelo avanzado ({@code gemini-2.5-pro}), sin importar
     * el uso de mayúsculas o minúsculas.
     *
     * @param complexPrompt texto de prueba que incluye un término complejo
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "Necesito revisar la arquitectura del sistema",
        "Propón una ARQUITECTURA limpia",
        "Ayúdame a refactorizar este método",
        "Quiero ReFaCtOrIzAr la clase de servicio",
        "Cómo optimizar una consulta SQL",
        "Necesito OPTIMIZAR el rendimiento",
        "Por favor, vamos a analizar en profundidad el problema",
        "Debemos AnAlIzAr En PrOfUnDiDaD estos logs"
    })
    @DisplayName("Debe enrutar a gemini-2.5-pro para todas las palabras clave complejas sin importar mayúsculas o minúsculas")
    void shouldRouteToProModelForAllComplexKeywordsCaseInsensitive(String complexPrompt) {
        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });

        RouterResponse response = routerService.routePrompt(new PromptRequest("architect-01", complexPrompt));

        assertTrue(response.tokensEstimated() <= RouterService.TOKEN_THRESHOLD,
                "El prompt de prueba debe tener <= 50 tokens para aislar el disparo por palabra clave");
        assertEquals(RouterService.MODEL_PRO, response.modelRouted());
    }

    /**
     * Comprueba la validación de longitud máxima del {@code userId} en su frontera exacta
     * (255 caracteres permitidos frente a 256 caracteres rechazados).
     */
    @Test
    @DisplayName("Debe aceptar un userId en el límite exacto de 255 caracteres y rechazar uno de 256 caracteres")
    void shouldValidateExactUserIdLengthBoundary() {
        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });

        // 255 caracteres exactos -> válido
        String validUserId255 = "u".repeat(255);
        RouterResponse response = routerService.routePrompt(new PromptRequest(validUserId255, "Hola mundo"));
        assertNotNull(response);
        assertEquals(validUserId255, response.userId());
        verify(requestLogRepository, times(1)).save(any(RequestLog.class));

        // 256 caracteres exactos -> lanza InvalidPromptException
        String invalidUserId256 = "u".repeat(256);
        assertThrows(InvalidPromptException.class,
                () -> routerService.routePrompt(new PromptRequest(invalidUserId256, "Hola mundo")));
        verify(requestLogRepository, times(1)).save(any(RequestLog.class));
    }

    /**
     * Verifica la fórmula determinista de estimación de tokens redondeando hacia arriba cada bloque de 4 caracteres.
     */
    @Test
    @DisplayName("Debe calcular los tokens estimados de forma determinista redondeando hacia arriba")
    void shouldEstimateTokensCorrectly() {
        assertEquals(1, routerService.estimateTokens("a"));
        assertEquals(1, routerService.estimateTokens("abcd"));
        assertEquals(2, routerService.estimateTokens("abcde"));
        assertEquals(2, routerService.estimateTokens("abcdefgh"));
        assertEquals(3, routerService.estimateTokens("abcdefghi"));
        assertEquals(50, routerService.estimateTokens("a".repeat(200)));
        assertEquals(51, routerService.estimateTokens("a".repeat(201)));
    }

    /**
     * Verifica que {@link RouterService#estimateTokens(String)} lance {@link InvalidPromptException}
     * si recibe valores nulos, vacíos o compuestos únicamente por espacios en blanco.
     */
    @Test
    @DisplayName("Debe lanzar InvalidPromptException al llamar directamente a estimateTokens con null o cadena en blanco")
    void shouldThrowInvalidPromptExceptionWhenEstimateTokensReceivesNullOrBlank() {
        assertThrows(InvalidPromptException.class, () -> routerService.estimateTokens(null));
        assertThrows(InvalidPromptException.class, () -> routerService.estimateTokens(""));
        assertThrows(InvalidPromptException.class, () -> routerService.estimateTokens("   "));
        assertThrows(InvalidPromptException.class, () -> routerService.estimateTokens("\t\n\r "));
    }

    /**
     * Verifica que {@link RouterService#routePrompt(PromptRequest)} rechace peticiones nulas o con campos
     * inválidos sin llegar a invocar la persistencia en el repositorio.
     */
    @Test
    @DisplayName("Debe lanzar InvalidPromptException cuando los datos de entrada de routePrompt son inválidos")
    void shouldThrowInvalidPromptExceptionWhenInputIsInvalid() {
        assertThrows(InvalidPromptException.class, () -> routerService.routePrompt(null));
        assertThrows(InvalidPromptException.class, () -> routerService.routePrompt(new PromptRequest(null, "Hola")));
        assertThrows(InvalidPromptException.class, () -> routerService.routePrompt(new PromptRequest("", "Hola")));
        assertThrows(InvalidPromptException.class, () -> routerService.routePrompt(new PromptRequest("   ", "Hola")));
        assertThrows(InvalidPromptException.class,
                () -> routerService.routePrompt(new PromptRequest("u".repeat(256), "Hola")));
        assertThrows(InvalidPromptException.class, () -> routerService.routePrompt(new PromptRequest("user-1", null)));
        assertThrows(InvalidPromptException.class, () -> routerService.routePrompt(new PromptRequest("user-1", "")));
        assertThrows(InvalidPromptException.class, () -> routerService.routePrompt(new PromptRequest("user-1", "  ")));

        verify(requestLogRepository, never()).save(any());
    }

    /**
     * Comprueba que {@link RouterService#getLogs(String)} recupere todos los registros cuando
     * {@code userId} es nulo o en blanco, y filtre por usuario aplicando {@code trim()} cuando se especifica.
     */
    @Test
    @DisplayName("Debe consultar todos los logs cuando userId es nulo o en blanco, y filtrar cuando tiene valor")
    void shouldGetLogsAllOrFilteredByUserId() {
        RequestLog sampleLog = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("user-1")
                .promptContent("Test prompt")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(3)
                .createdAt(LocalDateTime.now())
                .build();

        when(requestLogRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(sampleLog));
        when(requestLogRepository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(sampleLog));

        List<RouterResponse> allLogsNull = routerService.getLogs(null);
        assertEquals(1, allLogsNull.size());
        assertEquals("user-1", allLogsNull.getFirst().userId());

        List<RouterResponse> allLogsBlank = routerService.getLogs("   ");
        assertEquals(1, allLogsBlank.size());
        verify(requestLogRepository, times(2)).findAllByOrderByCreatedAtDesc();

        List<RouterResponse> userLogs = routerService.getLogs("  user-1  ");
        assertEquals(1, userLogs.size());
        assertEquals("user-1", userLogs.getFirst().userId());
        verify(requestLogRepository).findByUserIdOrderByCreatedAtDesc("user-1");
    }

    /**
     * Verifica el cálculo de estadísticas financieras de ahorro ({@link RouterService#getStats(String)})
     * en un escenario con tráfico mixto al 50% entre el modelo rápido y el avanzado.
     */
    @Test
    @DisplayName("Debe calcular correctamente las métricas de tokens, costes en USD y porcentaje de ahorro en tráfico mixto")
    void shouldCalculateStatsAndCostSavingsCorrectly() {
        RequestLog fastLog = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("user-stats")
                .promptContent("Prompt rápido")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(500_000)
                .createdAt(LocalDateTime.now())
                .build();

        RequestLog proLog = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("user-stats")
                .promptContent("Prompt pro")
                .modelRouted(RouterService.MODEL_PRO)
                .tokensEstimated(500_000)
                .createdAt(LocalDateTime.now())
                .build();

        when(requestLogRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(fastLog, proLog));

        RouterStatsResponse stats = routerService.getStats(null);

        assertEquals(2L, stats.totalRequests());
        assertEquals(1_000_000L, stats.totalTokensEstimated());
        assertEquals(1L, stats.fastModelRequests());
        assertEquals(1L, stats.proModelRequests());
        assertEquals(500_000L, stats.fastModelTokens());
        assertEquals(500_000L, stats.proModelTokens());
        // Coste real: (500_000 * 0.15 / 1_000_000) + (500_000 * 1.25 / 1_000_000) = 0.075 + 0.625 = 0.700000
        assertEquals(new BigDecimal("0.700000"), stats.estimatedActualCostUsd());
        // Coste base Pro: 1_000_000 * 1.25 / 1_000_000 = 1.250000
        assertEquals(new BigDecimal("1.250000"), stats.estimatedBaselineProCostUsd());
        // Ahorro: 1.250000 - 0.700000 = 0.550000
        assertEquals(new BigDecimal("0.550000"), stats.estimatedSavingsUsd());
        // Porcentaje de ahorro: (0.55 / 1.25) * 100 = 44.0%
        assertEquals(44.0, stats.savingsPercentage());
    }

    /**
     * Verifica que cuando no existen peticiones registradas en la base de datos (estado vacío),
     * {@link RouterService#getStats(String)} devuelva todos los contadores en cero, importes
     * en {@code 0.000000} y porcentaje de ahorro en {@code 0.0} sin provocar división por cero.
     */
    @Test
    @DisplayName("Debe devolver todos los contadores y costes a cero sin ArithmeticException cuando no hay peticiones")
    void shouldReturnZeroStatsWhenNoLogsExist() {
        when(requestLogRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());

        RouterStatsResponse emptyStats = routerService.getStats("   ");

        assertEquals(0L, emptyStats.totalRequests());
        assertEquals(0L, emptyStats.totalTokensEstimated());
        assertEquals(0L, emptyStats.fastModelRequests());
        assertEquals(0L, emptyStats.proModelRequests());
        assertEquals(0L, emptyStats.fastModelTokens());
        assertEquals(0L, emptyStats.proModelTokens());
        assertEquals(new BigDecimal("0.000000"), emptyStats.estimatedActualCostUsd());
        assertEquals(new BigDecimal("0.000000"), emptyStats.estimatedBaselineProCostUsd());
        assertEquals(new BigDecimal("0.000000"), emptyStats.estimatedSavingsUsd());
        assertEquals(0.0, emptyStats.savingsPercentage());
    }

    /**
     * Verifica que cuando el 100% del tráfico se enruta al modelo económico ({@code gemini-2.5-flash}),
     * el porcentaje de ahorro sea exactamente del {@code 88.0%}, incluso para una única petición
     * corta de pocos tokens (1 token o 5 tokens).
     */
    @Test
    @DisplayName("Debe calcular exactamente un 88.0% de ahorro cuando el 100% del tráfico va a gemini-2.5-flash")
    void shouldReturn88PercentSavingsWhenAllTrafficIsRoutedToFastModel() {
        RequestLog tinyFastLog = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("user-flash")
                .promptContent("Hola")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(1)
                .createdAt(LocalDateTime.now())
                .build();

        when(requestLogRepository.findByUserIdOrderByCreatedAtDesc("user-flash")).thenReturn(List.of(tinyFastLog));

        RouterStatsResponse stats = routerService.getStats("  user-flash  ");

        assertEquals(1L, stats.totalRequests());
        assertEquals(1L, stats.totalTokensEstimated());
        assertEquals(1L, stats.fastModelRequests());
        assertEquals(0L, stats.proModelRequests());
        assertEquals(1L, stats.fastModelTokens());
        assertEquals(0L, stats.proModelTokens());
        // 1 token en flash: 0.00000015 -> redondeado a 6 decimales = 0.000000
        assertEquals(new BigDecimal("0.000000"), stats.estimatedActualCostUsd());
        // 1 token en pro: 0.00000125 -> redondeado a 6 decimales = 0.000001
        assertEquals(new BigDecimal("0.000001"), stats.estimatedBaselineProCostUsd());
        assertEquals(new BigDecimal("0.000001"), stats.estimatedSavingsUsd());
        // Ahorro porcentual exacto: (1.25 - 0.15) / 1.25 * 100 = 88.0%
        assertEquals(88.0, stats.savingsPercentage());
        verify(requestLogRepository).findByUserIdOrderByCreatedAtDesc("user-flash");
    }

    /**
     * Verifica que cuando el 100% del tráfico se enruta al modelo avanzado ({@code gemini-2.5-pro}),
     * el ahorro económico sea {@code 0.000000} USD y el porcentaje de ahorro sea {@code 0.0%}.
     */
    @Test
    @DisplayName("Debe devolver 0.000000 USD y 0.0% de ahorro cuando el 100% del tráfico va a gemini-2.5-pro")
    void shouldReturnZeroSavingsWhenAllTrafficIsRoutedToProModel() {
        RequestLog proLog = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("user-pro-only")
                .promptContent("Analizar en profundidad la arquitectura")
                .modelRouted(RouterService.MODEL_PRO)
                .tokensEstimated(100_000)
                .createdAt(LocalDateTime.now())
                .build();

        when(requestLogRepository.findByUserIdOrderByCreatedAtDesc("user-pro-only")).thenReturn(List.of(proLog));

        RouterStatsResponse stats = routerService.getStats("user-pro-only");

        assertEquals(1L, stats.totalRequests());
        assertEquals(100_000L, stats.totalTokensEstimated());
        assertEquals(0L, stats.fastModelRequests());
        assertEquals(1L, stats.proModelRequests());
        assertEquals(0L, stats.fastModelTokens());
        assertEquals(100_000L, stats.proModelTokens());
        assertEquals(new BigDecimal("0.125000"), stats.estimatedActualCostUsd());
        assertEquals(new BigDecimal("0.125000"), stats.estimatedBaselineProCostUsd());
        assertEquals(new BigDecimal("0.000000"), stats.estimatedSavingsUsd());
        assertEquals(0.0, stats.savingsPercentage());
    }

    /**
     * Verifica el Nivel 1 del Modo Híbrido (BYOK): cuando el cliente proporciona su propia clave
     * {@code clientApiKey}, {@link RouterService} la prioriza sobre la clave del servidor e invoca
     * {@link GeminiRestClient#generateContent(String, String, String)} devolviendo el texto real de Gemini.
     */
    @Test
    @DisplayName("Modo Híbrido Nivel 1 (BYOK): Debe invocar GeminiRestClient con la clave del usuario y devolver su respuesta real")
    void shouldUseClientByokKeyAndReturnRealGeminiResponse() {
        GeminiRestClient mockGeminiClient = mock(GeminiRestClient.class);
        RouterService hybridService = new RouterService(
                requestLogRepository,
                mockGeminiClient,
                "AIzaSyServerGlobalKey"
        );

        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            arg.setCreatedAt(LocalDateTime.now());
            return arg;
        });

        when(mockGeminiClient.generateContent(
                RouterService.MODEL_FAST,
                "Hola Gemini",
                "AIzaSyUserByokKey123"))
                .thenReturn(Optional.of("Respuesta en vivo desde Google Gemini con clave BYOK."));

        RouterResponse response = hybridService.routePrompt(
                new PromptRequest("byok-user", "  Hola Gemini  "),
                "  AIzaSyUserByokKey123  "
        );

        assertEquals(RouterService.MODEL_FAST, response.modelRouted());
        assertEquals("Respuesta en vivo desde Google Gemini con clave BYOK.", response.simulatedResponse());
        verify(mockGeminiClient, times(1)).generateContent(
                RouterService.MODEL_FAST,
                "Hola Gemini",
                "AIzaSyUserByokKey123"
        );
    }

    /**
     * Verifica el Nivel 2 del Modo Híbrido (Clave de Servidor): cuando el cliente no envía clave
     * ({@code null} o en blanco), pero existe {@code airouter.gemini.api-key} configurada en el servidor,
     * {@link RouterService} invoca {@link GeminiRestClient} con la clave global del servidor.
     */
    @Test
    @DisplayName("Modo Híbrido Nivel 2 (Servidor): Debe usar la clave del servidor cuando clientApiKey es nula o en blanco")
    void shouldUseServerApiKeyWhenClientApiKeyIsBlank() {
        GeminiRestClient mockGeminiClient = mock(GeminiRestClient.class);
        RouterService hybridService = new RouterService(
                requestLogRepository,
                mockGeminiClient,
                "AIzaSyServerGlobalKey999"
        );

        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            arg.setCreatedAt(LocalDateTime.now());
            return arg;
        });

        when(mockGeminiClient.generateContent(
                RouterService.MODEL_PRO,
                "Necesito optimizar la arquitectura",
                "AIzaSyServerGlobalKey999"))
                .thenReturn(Optional.of("Análisis de arquitectura generado con clave de servidor."));

        RouterResponse response = hybridService.routePrompt(
                new PromptRequest("server-key-user", "Necesito optimizar la arquitectura"),
                "   "
        );

        assertEquals(RouterService.MODEL_PRO, response.modelRouted());
        assertEquals("Análisis de arquitectura generado con clave de servidor.", response.simulatedResponse());
        verify(mockGeminiClient, times(1)).generateContent(
                RouterService.MODEL_PRO,
                "Necesito optimizar la arquitectura",
                "AIzaSyServerGlobalKey999"
        );
    }

    /**
     * Verifica el Nivel 3 del Modo Híbrido (Fallback Resiliente a Simulación): cuando no hay ninguna
     * clave configurada o cuando {@link GeminiRestClient#generateContent(String, String, String)} devuelve
     * {@link Optional#empty()} por error de red/autenticación, el servicio retorna la respuesta simulada
     * {@code [Simulación AI Cost Router]...} sin interrumpir el flujo ni lanzar excepciones.
     */
    @Test
    @DisplayName("Modo Híbrido Nivel 3 (Fallback): Debe volver a simulación cuando no hay clave o cuando GeminiRestClient devuelve Optional.empty()")
    void shouldFallbackToSimulationWhenNoKeyOrWhenGeminiClientFails() {
        GeminiRestClient mockGeminiClient = mock(GeminiRestClient.class);
        RouterService hybridServiceNoKeys = new RouterService(
                requestLogRepository,
                mockGeminiClient,
                "   "
        );

        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            arg.setCreatedAt(LocalDateTime.now());
            return arg;
        });

        // Subcaso A: Sin clave de cliente ni clave de servidor -> no llama a GeminiRestClient y simula
        RouterResponse simulatedNoKey = hybridServiceNoKeys.routePrompt(
                new PromptRequest("fallback-user", "Consulta sencilla"),
                null
        );
        assertTrue(simulatedNoKey.simulatedResponse().startsWith("[Simulación AI Cost Router]"));
        verify(mockGeminiClient, never()).generateContent(anyString(), anyString(), anyString());

        // Subcaso B: Con clave BYOK pero GeminiRestClient falla (devuelve Optional.empty()) -> fallback a simulación
        when(mockGeminiClient.generateContent(
                RouterService.MODEL_FAST,
                "Consulta sencilla",
                "AIzaSyExpiredKey"))
                .thenReturn(Optional.empty());

        RouterResponse simulatedOnFailure = hybridServiceNoKeys.routePrompt(
                new PromptRequest("fallback-user", "Consulta sencilla"),
                "AIzaSyExpiredKey"
        );
        assertTrue(simulatedOnFailure.simulatedResponse().startsWith("[Simulación AI Cost Router]"));
        verify(mockGeminiClient, times(1)).generateContent(
                RouterService.MODEL_FAST,
                "Consulta sencilla",
                "AIzaSyExpiredKey"
        );
    }

    /**
     * Verifica la gestión del ciclo de vida del presupuesto FinOps ({@link RouterService#getUserBudget(String)}
     * y {@link RouterService#updateUserBudget(String, UserBudgetRequest)}):
     * un usuario sin presupuesto devuelve {@code UNLIMITED}, al fijar un importe positivo pasa a {@code OK},
     * y al enviar {@code null} o {@code 0} se elimina el límite volviendo a {@code UNLIMITED}.
     */
    @Test
    @DisplayName("FinOps Presupuesto: Debe devolver UNLIMITED sin límite, pasar a OK al configurar y volver a UNLIMITED al limpiar")
    void shouldManageUserBudgetLifecycleFromUnlimitedToOkAndBackToUnlimited() {
        UserBudgetRepository mockBudgetRepo = mock(UserBudgetRepository.class);
        RouterService finopsService = new RouterService(requestLogRepository, mockBudgetRepo);

        when(requestLogRepository.findByUserIdOrderByCreatedAtDesc("finops-user"))
                .thenReturn(Collections.emptyList());
        when(mockBudgetRepo.findByUserId("finops-user")).thenReturn(Optional.empty());

        // 1. Usuario sin presupuesto -> UNLIMITED
        UserBudgetResponse initialBudget = finopsService.getUserBudget("  finops-user  ");
        assertEquals("finops-user", initialBudget.userId());
        assertEquals(RouterService.BUDGET_STATUS_UNLIMITED, initialBudget.status());
        assertEquals(null, initialBudget.maxBudgetUsd());
        assertEquals(new BigDecimal("0.000000"), initialBudget.currentSpendUsd());

        // 2. Actualizar presupuesto con importe positivo -> OK
        when(mockBudgetRepo.save(any(UserBudget.class))).thenAnswer(invocation -> {
            UserBudget saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        UserBudgetResponse updatedOk = finopsService.updateUserBudget(
                "finops-user",
                new UserBudgetRequest(new BigDecimal("0.010000"), true)
        );
        assertEquals(RouterService.BUDGET_STATUS_OK, updatedOk.status());
        assertEquals(new BigDecimal("0.010000"), updatedOk.maxBudgetUsd());
        assertEquals(new BigDecimal("0.010000"), updatedOk.remainingBudgetUsd());
        assertEquals(0.0, updatedOk.usagePercentage());
        assertTrue(updatedOk.autoDowngradeEnabled());

        // 3. Limpiar presupuesto enviando 0 -> elimina el registro y vuelve a UNLIMITED
        UserBudget existingEntity = UserBudget.builder()
                .id(UUID.randomUUID())
                .userId("finops-user")
                .maxBudgetUsd(new BigDecimal("0.010000"))
                .autoDowngradeEnabled(true)
                .build();
        when(mockBudgetRepo.findByUserId("finops-user")).thenReturn(Optional.of(existingEntity));

        UserBudgetResponse clearedBudget = finopsService.updateUserBudget(
                "finops-user",
                new UserBudgetRequest(BigDecimal.ZERO, true)
        );
        assertEquals(RouterService.BUDGET_STATUS_UNLIMITED, clearedBudget.status());
        assertEquals(null, clearedBudget.maxBudgetUsd());
        verify(mockBudgetRepo, times(1)).delete(existingEntity);
    }

    /**
     * Verifica el umbral del 80% ({@code WARNING_80_DOWNGRADE} — Auto-Downgrade a Flash):
     * cuando el gasto acumulado del usuario se encuentra entre el {@code 80%} y el {@code < 100%}
     * de su {@code maxBudgetUsd} y {@code autoDowngradeEnabled == true}, un prompt complejo
     * con la palabra clave {@code "arquitectura"} (que normalmente iría a {@code gemini-2.5-pro})
     * es degradado automáticamente a {@code gemini-2.5-flash}.
     */
    @Test
    @DisplayName("FinOps Umbral 80% (Auto-Downgrade): Debe degradar automáticamente un prompt complejo de Pro a Flash cuando el consumo es >= 80%")
    void shouldAutoDowngradeComplexPromptToFastModelWhenBudgetUsageReaches80Percent() {
        UserBudgetRepository mockBudgetRepo = mock(UserBudgetRepository.class);
        RouterService finopsService = new RouterService(requestLogRepository, mockBudgetRepo);

        // 700 tokens en gemini-2.5-pro cuestan $0.000875 USD (87.5% de un presupuesto máximo de $0.001000 USD)
        RequestLog previousLog = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("downgrade-user")
                .promptContent("Petición previa")
                .modelRouted(RouterService.MODEL_PRO)
                .tokensEstimated(700)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();

        UserBudget activeBudget = UserBudget.builder()
                .id(UUID.randomUUID())
                .userId("downgrade-user")
                .maxBudgetUsd(new BigDecimal("0.001000"))
                .autoDowngradeEnabled(true)
                .updatedAt(LocalDateTime.now())
                .build();

        when(requestLogRepository.findByUserIdOrderByCreatedAtDesc("downgrade-user"))
                .thenReturn(List.of(previousLog));
        when(mockBudgetRepo.findByUserId("downgrade-user"))
                .thenReturn(Optional.of(activeBudget));
        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            arg.setCreatedAt(LocalDateTime.now());
            return arg;
        });

        UserBudgetResponse budgetState = finopsService.getUserBudget("downgrade-user");
        assertEquals(RouterService.BUDGET_STATUS_WARNING_80, budgetState.status());
        assertEquals(87.5, budgetState.usagePercentage());

        // Prompt complejo con "arquitectura" que iría a gemini-2.5-pro, pero se degrada a gemini-2.5-flash
        RouterResponse routed = finopsService.routePrompt(
                new PromptRequest("downgrade-user", "Necesito diseñar una arquitectura de microservicios")
        );

        assertEquals(RouterService.MODEL_FAST, routed.modelRouted());
    }

    /**
     * Verifica el umbral del 100% ({@code EXCEEDED_100_BLOCKED} — Cortafuegos Duro):
     * cuando el gasto acumulado del usuario es {@code >= 100%} de su {@code maxBudgetUsd},
     * {@link RouterService#routePrompt(PromptRequest)} lanza {@link BudgetExceededException}
     * sin persistir ningún nuevo registro en {@link RequestLogRepository}.
     */
    @Test
    @DisplayName("FinOps Umbral 100% (Hard Cap): Debe lanzar BudgetExceededException y no guardar en BD cuando el presupuesto se ha agotado")
    void shouldThrowBudgetExceededExceptionAndNotSaveLogWhenBudgetIs100PercentExhausted() {
        UserBudgetRepository mockBudgetRepo = mock(UserBudgetRepository.class);
        RouterService finopsService = new RouterService(requestLogRepository, mockBudgetRepo);

        // 800 tokens en gemini-2.5-pro cuestan $0.001000 USD (100% de $0.001000 USD)
        RequestLog exhaustedLog = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("blocked-user")
                .promptContent("Petición que agotó el saldo")
                .modelRouted(RouterService.MODEL_PRO)
                .tokensEstimated(800)
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .build();

        UserBudget exhaustedBudget = UserBudget.builder()
                .id(UUID.randomUUID())
                .userId("blocked-user")
                .maxBudgetUsd(new BigDecimal("0.001000"))
                .autoDowngradeEnabled(true)
                .updatedAt(LocalDateTime.now())
                .build();

        when(requestLogRepository.findByUserIdOrderByCreatedAtDesc("blocked-user"))
                .thenReturn(List.of(exhaustedLog));
        when(mockBudgetRepo.findByUserId("blocked-user"))
                .thenReturn(Optional.of(exhaustedBudget));

        UserBudgetResponse budgetState = finopsService.getUserBudget("blocked-user");
        assertEquals(RouterService.BUDGET_STATUS_EXCEEDED_100, budgetState.status());
        assertEquals(100.0, budgetState.usagePercentage());

        BudgetExceededException exception = assertThrows(
                BudgetExceededException.class,
                () -> finopsService.routePrompt(new PromptRequest("blocked-user", "Hola mundo"))
        );

        assertEquals("blocked-user", exception.getUserId());
        assertEquals(new BigDecimal("0.001000"), exception.getCurrentSpendUsd());
        assertEquals(new BigDecimal("0.001000"), exception.getMaxBudgetUsd());
        verify(requestLogRepository, never()).save(any(RequestLog.class));
    }
}
