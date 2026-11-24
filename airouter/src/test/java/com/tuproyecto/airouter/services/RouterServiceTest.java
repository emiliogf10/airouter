package com.tuproyecto.airouter.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tuproyecto.airouter.clients.GeminiRestClient;
import com.tuproyecto.airouter.exceptions.BudgetExceededException;
import com.tuproyecto.airouter.exceptions.InvalidApiKeyException;
import com.tuproyecto.airouter.exceptions.InvalidPromptException;
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
import com.tuproyecto.airouter.models.entities.ApiKey;
import com.tuproyecto.airouter.models.entities.RequestLog;
import com.tuproyecto.airouter.models.entities.UserBudget;
import com.tuproyecto.airouter.models.entities.UserWallet;
import com.tuproyecto.airouter.repositories.ApiKeyRepository;
import com.tuproyecto.airouter.repositories.RequestLogRepository;
import com.tuproyecto.airouter.repositories.UserBudgetRepository;
import com.tuproyecto.airouter.repositories.UserWalletRepository;
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

        // Subcaso B: Con clave BYOK en un prompt nuevo pero GeminiRestClient falla (devuelve Optional.empty()) -> fallback a simulación
        when(mockGeminiClient.generateContent(
                RouterService.MODEL_FAST,
                "Otra consulta sencilla",
                "AIzaSyExpiredKey"))
                .thenReturn(Optional.empty());

        RouterResponse simulatedOnFailure = hybridServiceNoKeys.routePrompt(
                new PromptRequest("fallback-user", "Otra consulta sencilla"),
                "AIzaSyExpiredKey"
        );
        assertTrue(simulatedOnFailure.simulatedResponse().startsWith("[Simulación AI Cost Router]"));
        verify(mockGeminiClient, times(1)).generateContent(
                RouterService.MODEL_FAST,
                "Otra consulta sencilla",
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

    /**
     * Verifica la normalización semántica y detección de *Cache Hit* en {@link RouterService#routePrompt(PromptRequest, String)}:
     * cuando ya existe un registro previo con {@code "  ¿Qué día   es HOY? "} y entra un nuevo prompt
     * {@code "¿qué día es hoy?"}, el enrutador detecta la equivalencia semántica ({@code cacheHit = true}),
     * reutiliza la respuesta previa sin invocar a {@link GeminiRestClient} y persiste el nuevo
     * {@link RequestLog} con {@code cacheHit = true}.
     */
    @Test
    @DisplayName("Caché Semántica (Cache Hit): Debe detectar prompts equivalentes normalizados, omitir llamada a GeminiRestClient y guardar cacheHit=true")
    void shouldDetectSemanticCacheHitSkipGeminiClientAndPersistCacheHitTrue() {
        GeminiRestClient mockGeminiClient = mock(GeminiRestClient.class);
        RouterService cacheAwareService = new RouterService(
                requestLogRepository,
                mockGeminiClient,
                "AIzaSyServerKeyForCacheTest"
        );

        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            arg.setCreatedAt(LocalDateTime.now());
            return arg;
        });

        // 1. Primera petición con espacios extra y mayúsculas -> Cache Miss (invoca GeminiRestClient)
        when(requestLogRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());
        when(mockGeminiClient.generateContent(
                RouterService.MODEL_FAST,
                "¿Qué día   es HOY?",
                "AIzaSyServerKeyForCacheTest"))
                .thenReturn(Optional.of("Hoy es miércoles."));

        RouterResponse firstResponse = cacheAwareService.routePrompt(
                new PromptRequest("user-cache-1", "  ¿Qué día   es HOY? ")
        );

        assertFalse(firstResponse.cacheHit());
        assertEquals("Hoy es miércoles.", firstResponse.simulatedResponse());
        verify(mockGeminiClient, times(1)).generateContent(
                RouterService.MODEL_FAST,
                "¿Qué día   es HOY?",
                "AIzaSyServerKeyForCacheTest"
        );

        // 2. Segunda petición con distinta capitalización y espacios simples -> Cache Hit (0 llamadas a GeminiRestClient)
        clearInvocations(mockGeminiClient);
        RequestLog persistedFirstLog = RequestLog.builder()
                .id(firstResponse.id())
                .userId("user-cache-1")
                .promptContent("  ¿Qué día   es HOY? ")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(firstResponse.tokensEstimated())
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .cacheHit(false)
                .build();
        when(requestLogRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(persistedFirstLog));

        ArgumentCaptor<RequestLog> logCaptor = ArgumentCaptor.forClass(RequestLog.class);
        RouterResponse secondResponse = cacheAwareService.routePrompt(
                new PromptRequest("user-cache-1", "¿qué día es hoy?")
        );

        assertTrue(secondResponse.cacheHit(), "La segunda petición equivalente debe marcarse como cacheHit = true");
        assertEquals("Hoy es miércoles.", secondResponse.simulatedResponse());
        assertEquals(RouterService.MODEL_FAST, secondResponse.modelRouted());
        verifyNoInteractions(mockGeminiClient);

        verify(requestLogRepository, times(2)).save(logCaptor.capture());
        RequestLog secondSavedEntity = logCaptor.getAllValues().get(1);
        assertTrue(secondSavedEntity.getCacheHit(), "La entidad guardada en BD debe tener cacheHit = true");
    }

    /**
     * Verifica el cálculo financiero de la Caché Semántica de Coste Cero en {@link RouterService#getStats(String)}:
     * cuando existe 1 petición normal ({@code cacheHit = false}) y 1 petición servida desde caché
     * ({@code cacheHit = true}), {@code getStats} contabiliza {@code cacheHits == 1}, suma ambas en
     * {@code totalRequests} y {@code estimatedBaselineProCostUsd}, pero en {@code estimatedActualCostUsd}
     * solo factura la primera petición (coste {@code $0.000000 USD} para la segunda), elevando el ahorro.
     */
    @Test
    @DisplayName("Caché Semántica FinOps (Coste Cero): getStats debe facturar $0.000000 USD por los Cache Hits y sumar el 100% al ahorro")
    void shouldCalculateZeroActualCostAndFullSavingsForCacheHitsInGetStats() {
        // Petición 1 (Cache Miss en Pro): 100.000 tokens -> Coste real = $0.125000 USD, Base Pro = $0.125000 USD
        RequestLog firstMissLog = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("finops-cache-user")
                .promptContent("Analizar en profundidad la arquitectura del sistema")
                .modelRouted(RouterService.MODEL_PRO)
                .tokensEstimated(100_000)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .cacheHit(false)
                .build();

        // Petición 2 (Cache Hit en Pro): 100.000 tokens -> Coste real = $0.000000 USD, Base Pro = $0.125000 USD
        RequestLog secondHitLog = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("finops-cache-user")
                .promptContent("  analizar en profundidad   la ARQUITECTURA del sistema ")
                .modelRouted(RouterService.MODEL_PRO)
                .tokensEstimated(100_000)
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .cacheHit(true)
                .build();

        when(requestLogRepository.findByUserIdOrderByCreatedAtDesc("finops-cache-user"))
                .thenReturn(List.of(secondHitLog, firstMissLog));

        RouterStatsResponse stats = routerService.getStats("finops-cache-user");

        assertEquals(2L, stats.totalRequests());
        assertEquals(200_000L, stats.totalTokensEstimated());
        assertEquals(0L, stats.fastModelRequests());
        assertEquals(2L, stats.proModelRequests());
        assertEquals(1L, stats.cacheHits());
        // Coste real: solo cobra los primeros 100.000 tokens ($0.125000 USD); el Cache Hit cuesta $0.000000 USD
        assertEquals(new BigDecimal("0.125000"), stats.estimatedActualCostUsd());
        // Coste base sin caché: 200.000 tokens en Pro = $0.250000 USD
        assertEquals(new BigDecimal("0.250000"), stats.estimatedBaselineProCostUsd());
        // Ahorro neto gracias a la caché: $0.125000 USD (50.0% de ahorro total sobre las 2 peticiones Pro)
        assertEquals(new BigDecimal("0.125000"), stats.estimatedSavingsUsd());
        assertEquals(50.0, stats.savingsPercentage());
    }

    /**
     * Verifica la generación criptográfica y almacenamiento seguro SHA-256 de claves API ({@link RouterService#createApiKey(CreateApiKeyRequest)}
     * y {@link RouterService#getUserApiKeys(String)}):
     * comprueba que {@code rawApiKey} empieza por {@code "air_live_"} con 41 caracteres totales,
     * que el {@code keyHash} persistido en {@link ApiKeyRepository} tiene 64 caracteres hexadecimales
     * y nunca coincide con el secreto en texto plano, y que {@code getUserApiKeys} devuelve únicamente
     * el {@code keyPrefix} enmascarado.
     */
    @Test
    @DisplayName("Seguridad Claves API (SHA-256): createApiKey debe generar air_live_ de 41 chars, guardar solo hash SHA-256 de 64 chars y enmascarar el prefijo")
    void shouldGenerateManagedApiKeyPersistOnlySha256HashAndReturnMaskedPrefixInList() {
        ApiKeyRepository mockApiKeyRepo = mock(ApiKeyRepository.class);
        routerService.setApiKeyRepository(mockApiKeyRepo);

        ArgumentCaptor<ApiKey> apiKeyCaptor = ArgumentCaptor.forClass(ApiKey.class);
        when(mockApiKeyRepo.save(apiKeyCaptor.capture())).thenAnswer(invocation -> {
            ApiKey arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });

        ApiKeyCreatedResponse created = routerService.createApiKey(
                new CreateApiKeyRequest("  dev-user-01  ", "  Microservicio Pagos  ")
        );

        assertNotNull(created.id());
        assertEquals("dev-user-01", created.userId());
        assertEquals("Microservicio Pagos", created.keyName());
        assertTrue(created.rawApiKey().startsWith("air_live_"));
        assertEquals(41, created.rawApiKey().length());
        assertTrue(created.keyPrefix().startsWith("air_live_"));
        assertTrue(created.keyPrefix().endsWith("..."));
        assertTrue(created.active());

        ApiKey persistedEntity = apiKeyCaptor.getValue();
        assertNotNull(persistedEntity.getKeyHash());
        assertEquals(64, persistedEntity.getKeyHash().length(), "El hash SHA-256 debe tener exactamente 64 caracteres hexadecimales");
        assertNotEquals(created.rawApiKey(), persistedEntity.getKeyHash(), "El hash guardado jamás debe ser igual a la clave en texto plano");
        assertEquals(routerService.hashApiKeySha256(created.rawApiKey()), persistedEntity.getKeyHash());

        when(mockApiKeyRepo.findByUserIdOrderByCreatedAtDesc("dev-user-01"))
                .thenReturn(List.of(persistedEntity));

        List<ApiKeyResponse> listedKeys = routerService.getUserApiKeys("dev-user-01");
        assertEquals(1, listedKeys.size());
        assertEquals(created.keyPrefix(), listedKeys.get(0).keyPrefix());
        assertTrue(listedKeys.get(0).active());
    }

    /**
     * Verifica la autenticación de claves API gestionadas ({@code air_live_...}) en {@link RouterService#routePrompt(PromptRequest, String)}:
     * - Si la clave existe por su hash SHA-256 y está activa ({@code active == true}), procesa la petición con éxito.
     * - Si la clave no existe o ha sido revocada ({@code active == false}), lanza {@link InvalidApiKeyException}
     *   sin persistir ningún registro en {@link RequestLogRepository}.
     */
    @Test
    @DisplayName("Seguridad Claves API (Autenticación): routePrompt debe aceptar claves air_live_ activas y rechazar claves inexistentes o revocadas con InvalidApiKeyException")
    void shouldAuthenticateActiveManagedApiKeyAndRejectRevokedOrUnknownKeyWithInvalidApiKeyException() {
        ApiKeyRepository mockApiKeyRepo = mock(ApiKeyRepository.class);
        routerService.setApiKeyRepository(mockApiKeyRepo);

        String validRawKey = "air_live_0123456789abcdef0123456789abcdef";
        String validHash = routerService.hashApiKeySha256(validRawKey);

        ApiKey activeKeyEntity = ApiKey.builder()
                .id(UUID.randomUUID())
                .userId("auth-user")
                .keyName("Clave Activa")
                .keyPrefix("air_live_0123...")
                .keyHash(validHash)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(mockApiKeyRepo.findByKeyHash(validHash)).thenReturn(Optional.of(activeKeyEntity));
        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            arg.setCreatedAt(LocalDateTime.now());
            return arg;
        });

        // 1. Clave activa -> procesa la petición exitosamente
        RouterResponse okResponse = routerService.routePrompt(
                new PromptRequest("auth-user", "Hola con clave válida"),
                validRawKey
        );
        assertEquals(RouterService.MODEL_FAST, okResponse.modelRouted());
        verify(requestLogRepository, times(1)).save(any(RequestLog.class));

        // 2. Clave revocada (active = false) -> lanza InvalidApiKeyException y no guarda nuevo log
        activeKeyEntity.setActive(false);
        assertThrows(
                InvalidApiKeyException.class,
                () -> routerService.routePrompt(
                        new PromptRequest("auth-user", "Hola con clave revocada"),
                        validRawKey
                )
        );
        verify(requestLogRepository, times(1)).save(any(RequestLog.class));

        // 3. Clave inexistente -> lanza InvalidApiKeyException
        String unknownRawKey = "air_live_ffffffffffffffffffffffffffffffff";
        String unknownHash = routerService.hashApiKeySha256(unknownRawKey);
        when(mockApiKeyRepo.findByKeyHash(unknownHash)).thenReturn(Optional.empty());

        assertThrows(
                InvalidApiKeyException.class,
                () -> routerService.routePrompt(
                        new PromptRequest("auth-user", "Hola con clave desconocida"),
                        unknownRawKey
                )
        );
        verify(requestLogRepository, times(1)).save(any(RequestLog.class));
    }

    /**
     * Verifica la recarga de créditos y el descuento automático de micro-céntimos en tiempo real
     * en la Billetera Prepago ({@link RouterService#getUserWallet(String)},
     * {@link RouterService#topUpUserWallet(String, WalletTopUpRequest)} y {@link RouterService#routePrompt(PromptRequest)}):
     * un usuario comienza en {@code POSTPAID_UNLIMITED}, al recargar {@code $0.000010 USD} con {@code "PAYPAL"}
     * pasa a {@code PREPAID_ACTIVE} ({@code walletEnforced == true}), y al enrutar un prompt simple de 4 tokens
     * en Flash ({@code $0.000001 USD} redondeado a 6 decimales), su saldo queda exactamente en {@code $0.000009 USD}.
     */
    @Test
    @DisplayName("Billetera Prepago (Recarga y Descuento): Debe pasar de POSTPAID_UNLIMITED a PREPAID_ACTIVE al recargar y descontar micro-céntimos al enrutar")
    void shouldTopUpWalletToPrepaidActiveAndDeductMicroCentsOnRoutePrompt() {
        UserWalletRepository mockWalletRepo = mock(UserWalletRepository.class);
        routerService.setUserWalletRepository(mockWalletRepo);

        when(mockWalletRepo.findByUserId("billing-user")).thenReturn(Optional.empty());

        // 1. Estado inicial sin recargas -> POSTPAID_UNLIMITED
        UserWalletResponse initialWallet = routerService.getUserWallet("  billing-user  ");
        assertEquals("billing-user", initialWallet.userId());
        assertEquals(RouterService.WALLET_STATUS_POSTPAID, initialWallet.status());
        assertFalse(initialWallet.walletEnforced());
        assertEquals(new BigDecimal("0.000000"), initialWallet.balanceUsd());

        // 2. Recargar $0.000010 USD con PAYPAL -> PREPAID_ACTIVE
        ArgumentCaptor<UserWallet> walletCaptor = ArgumentCaptor.forClass(UserWallet.class);
        when(mockWalletRepo.save(walletCaptor.capture())).thenAnswer(invocation -> {
            UserWallet saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        UserWalletResponse toppedUp = routerService.topUpUserWallet(
                "billing-user",
                new WalletTopUpRequest(new BigDecimal("0.000010"), "paypal")
        );

        assertEquals(RouterService.WALLET_STATUS_ACTIVE, toppedUp.status());
        assertTrue(toppedUp.walletEnforced());
        assertEquals("PAYPAL", toppedUp.lastPaymentMethod());
        assertEquals(new BigDecimal("0.000010"), toppedUp.balanceUsd());
        assertEquals(new BigDecimal("0.000010"), toppedUp.totalToppedUpUsd());

        // 3. Enrutar prompt sencillo de 16 caracteres (4 tokens en Flash = 4 * 0.15 / 1M = 0.0000006 -> redondeado a 6 decimales = $0.000001 USD)
        UserWallet activeWalletEntity = walletCaptor.getValue();
        when(mockWalletRepo.findByUserId("billing-user")).thenReturn(Optional.of(activeWalletEntity));
        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            arg.setCreatedAt(LocalDateTime.now());
            return arg;
        });

        RouterResponse routed = routerService.routePrompt(
                new PromptRequest("billing-user", "1234567890123456")
        );

        assertEquals(RouterService.MODEL_FAST, routed.modelRouted());
        assertEquals(4, routed.tokensEstimated());
        assertEquals(new BigDecimal("0.000009"), activeWalletEntity.getBalanceUsd());
    }

    /**
     * Verifica que cuando un usuario tiene activada la Billetera Prepago ({@code walletEnforced == true})
     * y su saldo disponible es {@code $0.000000 USD} ({@code PREPAID_DEPLETED}), al enviar un prompt nuevo
     * (no cacheado) {@link RouterService#routePrompt(PromptRequest)} lanza {@link BudgetExceededException}
     * sin persistir ningún registro en {@link RequestLogRepository}.
     */
    @Test
    @DisplayName("Billetera Prepago (Saldo Agotado): Debe lanzar BudgetExceededException y no guardar en BD cuando walletEnforced=true y balanceUsd=0")
    void shouldThrowBudgetExceededExceptionWhenPrepaidWalletBalanceIsZero() {
        UserWalletRepository mockWalletRepo = mock(UserWalletRepository.class);
        routerService.setUserWalletRepository(mockWalletRepo);

        UserWallet depletedWallet = UserWallet.builder()
                .id(UUID.randomUUID())
                .userId("depleted-user")
                .balanceUsd(new BigDecimal("0.000000"))
                .totalToppedUpUsd(new BigDecimal("0.000005"))
                .walletEnforced(true)
                .lastPaymentMethod("STRIPE_CARD")
                .updatedAt(LocalDateTime.now())
                .build();

        when(mockWalletRepo.findByUserId("depleted-user")).thenReturn(Optional.of(depletedWallet));

        UserWalletResponse walletState = routerService.getUserWallet("depleted-user");
        assertEquals(RouterService.WALLET_STATUS_DEPLETED, walletState.status());

        BudgetExceededException ex = assertThrows(
                BudgetExceededException.class,
                () -> routerService.routePrompt(new PromptRequest("depleted-user", "Prompt nuevo sin saldo prepago"))
        );

        assertTrue(ex.getMessage().contains("Saldo prepago agotado"));
        verify(requestLogRepository, never()).save(any(RequestLog.class));
    }

    /**
     * Prueba de regresión para el hallazgo de auditoría {@code HIGH-01}
     * ({@code airouter.auth.apikey-userid-unbound}):
     * verifica que si una clave API gestionada ({@code air_live_...}) pertenece a {@code "alice"},
     * intentar utilizarla en {@link RouterService#routePrompt(PromptRequest, String)} indicando
     * {@code userId = "bob"} lanza {@link InvalidApiKeyException} y no persiste ningún log para {@code "bob"}.
     */
    @Test
    @DisplayName("Regresión HIGH-01: routePrompt debe lanzar InvalidApiKeyException cuando la clave air_live_ pertenece a otro userId")
    void shouldRejectManagedApiKeyWhenOwnerUserIdDoesNotMatchPromptRequestUserId() {
        ApiKeyRepository mockApiKeyRepo = mock(ApiKeyRepository.class);
        routerService.setApiKeyRepository(mockApiKeyRepo);

        String aliceRawKey = "air_live_aaaa1111bbbb2222cccc3333dddd4444";
        String aliceHash = routerService.hashApiKeySha256(aliceRawKey);

        ApiKey aliceKeyEntity = ApiKey.builder()
                .id(UUID.randomUUID())
                .userId("alice")
                .keyName("Clave Privada de Alice")
                .keyPrefix("air_live_aaaa...")
                .keyHash(aliceHash)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(mockApiKeyRepo.findByKeyHash(aliceHash)).thenReturn(Optional.of(aliceKeyEntity));

        InvalidApiKeyException ex = assertThrows(
                InvalidApiKeyException.class,
                () -> routerService.routePrompt(
                        new PromptRequest("bob", "Intentando suplantar a Bob con la clave de Alice"),
                        aliceRawKey
                )
        );

        assertTrue(ex.getMessage().contains("no pertenece al usuario 'bob'"));
        verify(requestLogRepository, never()).save(any(RequestLog.class));
    }

    /**
     * Prueba de regresión para el hallazgo de auditoría {@code MEDIUM-01}
     * ({@code airouter.authz.unauthenticated-tenant-management-endpoints}):
     * verifica que {@link RouterService#revokeApiKey(UUID, String)} rechaza con {@link InvalidApiKeyException}
     * el intento de revocar la clave de {@code "bob"} presentando una clave {@code air_live_...} perteneciente
     * a {@code "alice"}, manteniendo activa la clave de {@code "bob"}.
     */
    @Test
    @DisplayName("Regresión MEDIUM-01: revokeApiKey debe lanzar InvalidApiKeyException si la clave air_live_ del llamante pertenece a otro usuario")
    void shouldRejectRevokeApiKeyWhenCallerManagedApiKeyBelongsToAnotherUser() {
        ApiKeyRepository mockApiKeyRepo = mock(ApiKeyRepository.class);
        routerService.setApiKeyRepository(mockApiKeyRepo);

        String aliceRawKey = "air_live_aaaa1111bbbb2222cccc3333dddd4444";
        String aliceHash = routerService.hashApiKeySha256(aliceRawKey);

        ApiKey aliceKeyEntity = ApiKey.builder()
                .id(UUID.randomUUID())
                .userId("alice")
                .keyName("Clave de Alice")
                .keyPrefix("air_live_aaaa...")
                .keyHash(aliceHash)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        UUID bobKeyId = UUID.randomUUID();
        ApiKey bobKeyEntity = ApiKey.builder()
                .id(bobKeyId)
                .userId("bob")
                .keyName("Clave de Bob")
                .keyPrefix("air_live_bbbb...")
                .keyHash("b".repeat(64))
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(mockApiKeyRepo.findById(bobKeyId)).thenReturn(Optional.of(bobKeyEntity));
        when(mockApiKeyRepo.findByKeyHash(aliceHash)).thenReturn(Optional.of(aliceKeyEntity));

        assertThrows(
                InvalidApiKeyException.class,
                () -> routerService.revokeApiKey(bobKeyId, aliceRawKey)
        );

        assertTrue(bobKeyEntity.getActive(), "La clave de Bob debe permanecer activa tras el intento no autorizado");
        verify(mockApiKeyRepo, never()).save(any(ApiKey.class));
    }

    /**
     * Prueba de regresión para el hallazgo de auditoría {@code MEDIUM-02}
     * ({@code airouter.cache.global-cross-user-prompt-cache}):
     * verifica que si un primer usuario ejecuta un prompt en modo simulación (cacheando
     * {@code [Simulación AI Cost Router]...}), y posteriormente entra una petición con el mismo prompt
     * pero adjuntando una clave externa BYOK real ({@code "AIzaSyBobKey123"}), {@link RouterService}
     * no devuelve la simulación cacheada sino que invoca a {@link GeminiRestClient} y devuelve la respuesta real
     * ({@code cacheHit == false}).
     */
    @Test
    @DisplayName("Regresión MEDIUM-02: routePrompt no debe servir una respuesta simulada en caché cuando el usuario proporciona una clave BYOK real")
    void shouldBypassSimulatedCacheEntryWhenCallerProvidesExternalByokGeminiKey() {
        GeminiRestClient mockGeminiClient = mock(GeminiRestClient.class);
        RouterService isolatedCacheService = new RouterService(
                requestLogRepository,
                mockGeminiClient,
                ""
        );

        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            arg.setCreatedAt(LocalDateTime.now());
            return arg;
        });

        // 1. Alice envía un prompt sin clave -> Modo Simulación
        when(requestLogRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());
        RouterResponse simulatedResponse = isolatedCacheService.routePrompt(
                new PromptRequest("alice", "Explica la arquitectura hexagonal"),
                null
        );
        assertFalse(simulatedResponse.cacheHit());
        assertTrue(simulatedResponse.simulatedResponse().startsWith("[Simulación AI Cost Router]"));
        verifyNoInteractions(mockGeminiClient);

        // 2. Bob envía el mismo prompt pero con su clave BYOK real de Gemini -> Debe ignorar la simulación en caché e invocar GeminiRestClient
        when(mockGeminiClient.generateContent(
                RouterService.MODEL_PRO,
                "Explica la arquitectura hexagonal",
                "AIzaSyBobKey123"))
                .thenReturn(Optional.of("Respuesta real de Google Gemini sobre arquitectura hexagonal."));

        RouterResponse realByokResponse = isolatedCacheService.routePrompt(
                new PromptRequest("bob", "Explica la arquitectura hexagonal"),
                "AIzaSyBobKey123"
        );

        assertFalse(realByokResponse.cacheHit(), "No debe hacer Cache Hit contra una entrada simulada cuando se provee BYOK real");
        assertEquals("Respuesta real de Google Gemini sobre arquitectura hexagonal.", realByokResponse.simulatedResponse());
        verify(mockGeminiClient, times(1)).generateContent(
                RouterService.MODEL_PRO,
                "Explica la arquitectura hexagonal",
                "AIzaSyBobKey123"
        );
    }

    /**
     * Verifica que {@link RouterService#sanitizePromptPiiAndSecrets(String)} detecte y anonimice
     * simultáneamente las 5 categorías del Escudo DLP de Privacidad:
     * <ol>
     *   <li>Correo electrónico ({@code [EMAIL_REDACTED]}).</li>
     *   <li>Clave secreta de API ({@code [SECRET_REDACTED]}).</li>
     *   <li>Tarjeta bancaria de 16 dígitos ({@code [CARD_REDACTED]}).</li>
     *   <li>DNI español ({@code [ID_REDACTED]}).</li>
     *   <li>Teléfono con prefijo internacional ({@code [PHONE_REDACTED]}).</li>
     * </ol>
     */
    @Test
    @DisplayName("Escudo DLP (Unitario): sanitizePromptPiiAndSecrets debe anonimizar email, secreto API, tarjeta, DNI y teléfono contabilizando 5 elementos")
    void shouldSanitizeEmailSecretCardDniAndPhoneInPrompt() {
        String rawSensitivePrompt = "Hola, mi email es cliente.vip@empresa.es, mi token es AIzaSyDlpSecretKey123456789, "
                + "mi tarjeta es 4532 0151 1283 0366, mi DNI es 12345678Z y mi móvil es +34 600 123 456.";

        RouterService.DlpSanitizationResult result = routerService.sanitizePromptPiiAndSecrets(rawSensitivePrompt);

        assertEquals(5, result.redactedItemsCount());
        assertTrue(result.sanitizedPrompt().contains(RouterService.DLP_EMAIL_REDACTED));
        assertTrue(result.sanitizedPrompt().contains(RouterService.DLP_SECRET_REDACTED));
        assertTrue(result.sanitizedPrompt().contains(RouterService.DLP_CARD_REDACTED));
        assertTrue(result.sanitizedPrompt().contains(RouterService.DLP_ID_REDACTED));
        assertTrue(result.sanitizedPrompt().contains(RouterService.DLP_PHONE_REDACTED));

        assertFalse(result.sanitizedPrompt().contains("cliente.vip@empresa.es"));
        assertFalse(result.sanitizedPrompt().contains("AIzaSyDlpSecretKey123456789"));
        assertFalse(result.sanitizedPrompt().contains("4532 0151 1283 0366"));
        assertFalse(result.sanitizedPrompt().contains("12345678Z"));
        assertFalse(result.sanitizedPrompt().contains("600 123 456"));
    }

    /**
     * Verifica que {@link RouterService#routePrompt(PromptRequest)} persista en {@link RequestLog}
     * el prompt ya sanitizado por el Escudo DLP (sin almacenar PII ni secretos en texto plano),
     * registre una latencia {@code latencyMs >= 1L} y propague {@code redactedItemsCount} en {@link RouterResponse}.
     */
    @Test
    @DisplayName("Escudo DLP y Latencia en routePrompt: Debe persistir el prompt ya sanitizado, latencyMs >= 1L y redactedItemsCount en RouterResponse")
    void shouldPersistSanitizedPromptLatencyAndRedactedCountOnRoutePrompt() {
        ArgumentCaptor<RequestLog> logCaptor = ArgumentCaptor.forClass(RequestLog.class);
        when(requestLogRepository.save(logCaptor.capture())).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            arg.setCreatedAt(LocalDateTime.now());
            return arg;
        });

        RouterResponse response = routerService.routePrompt(
                new PromptRequest("dlp-route-user", "Enviar factura a admin@corp.com usando clave sk-proj1234567890abcdef")
        );

        assertEquals(2, response.redactedItemsCount());
        assertTrue(response.latencyMs() >= 1L, "La latencia registrada debe ser al menos 1 ms");
        assertTrue(response.promptContent().contains(RouterService.DLP_EMAIL_REDACTED));
        assertTrue(response.promptContent().contains(RouterService.DLP_SECRET_REDACTED));
        assertFalse(response.promptContent().contains("admin@corp.com"));
        assertFalse(response.promptContent().contains("sk-proj1234567890abcdef"));

        RequestLog savedLog = logCaptor.getValue();
        assertEquals(2, savedLog.getRedactedItemsCount());
        assertTrue(savedLog.getLatencyMs() >= 1L);
        assertFalse(savedLog.getPromptContent().contains("admin@corp.com"));
    }

    /**
     * Verifica que {@link RouterService#getStats(String)} calcule con exactitud la latencia media
     * ({@code averageLatencyMs}) y el total acumulado de elementos sensibles protegidos por el Escudo DLP
     * ({@code totalRedactedItems}).
     */
    @Test
    @DisplayName("Telemetría en getStats: Debe calcular averageLatencyMs y totalRedactedItems a partir del historial de peticiones")
    void shouldCalculateAverageLatencyMsAndTotalRedactedItemsInGetStats() {
        RequestLog log1 = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("telemetry-user")
                .promptContent("Consulta 1 con [EMAIL_REDACTED]")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(10)
                .createdAt(LocalDateTime.now().minusMinutes(3))
                .cacheHit(false)
                .latencyMs(10L)
                .redactedItemsCount(1)
                .build();

        RequestLog log2 = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("telemetry-user")
                .promptContent("Consulta 2 con [SECRET_REDACTED] y [PHONE_REDACTED]")
                .modelRouted(RouterService.MODEL_PRO)
                .tokensEstimated(30)
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .cacheHit(false)
                .latencyMs(25L)
                .redactedItemsCount(2)
                .build();

        when(requestLogRepository.findByUserIdOrderByCreatedAtDesc("telemetry-user"))
                .thenReturn(List.of(log2, log1));

        RouterStatsResponse stats = routerService.getStats("telemetry-user");

        assertEquals(2L, stats.totalRequests());
        assertEquals(17.5, stats.averageLatencyMs());
        assertEquals(3L, stats.totalRedactedItems());
    }

    /**
     * Verifica unitariamente {@link RouterService#compressPromptAndSaveTokens(String)} con un prompt
     * que contiene espacios múltiples, tabulaciones, bloques de 4+ saltos de línea, espacios antes de
     * signos de puntuación y secuencias {@code ????}/{@code !!!!}, comprobando que {@code compressedPrompt}
     * queda limpio, {@code tokensBeforeCompression > tokensAfterCompression} y
     * {@code tokensSaved == tokensBeforeCompression - tokensAfterCompression}.
     */
    @Test
    @DisplayName("Compresor de Prompts (Unitario): compressPromptAndSaveTokens debe limpiar ruido sintáctico y calcular tokens ahorrados")
    void shouldCompressPromptRedundantWhitespaceNewlinesAndPunctuationAndCountTokensSaved() {
        String noisyPrompt = "   Hola     mundo \t\t ,   ¿cómo    estás????\n\n\n\n\nNecesito     optimizar    esto   !!!!   ";

        RouterService.PromptCompressionResult result = routerService.compressPromptAndSaveTokens(noisyPrompt);

        assertEquals("Hola mundo, ¿cómo estás?\n\nNecesito optimizar esto!", result.compressedPrompt());
        assertTrue(result.tokensBeforeCompression() > result.tokensAfterCompression());
        assertEquals(
                result.tokensBeforeCompression() - result.tokensAfterCompression(),
                result.tokensSaved()
        );
        assertTrue(result.tokensSaved() > 0);
    }

    /**
     * Verifica que {@link RouterService#routePrompt(PromptRequest)} persista en {@link RequestLog}
     * el prompt ya comprimido junto con {@code tokensSavedCompression > 0} y lo devuelva en
     * {@link RouterResponse#tokensSavedCompression()}.
     */
    @Test
    @DisplayName("Compresor de Prompts en routePrompt: Debe persistir el prompt comprimido y devolver tokensSavedCompression > 0")
    void shouldPersistCompressedPromptAndTokensSavedOnRoutePrompt() {
        ArgumentCaptor<RequestLog> logCaptor = ArgumentCaptor.forClass(RequestLog.class);
        when(requestLogRepository.save(logCaptor.capture())).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            arg.setCreatedAt(LocalDateTime.now());
            return arg;
        });

        RouterResponse response = routerService.routePrompt(
                new PromptRequest(
                        "compression-user",
                        "   Explícame      paso   a   paso   ,   por   favor   ????\n\n\n\n\nGracias   !!!!   "
                )
        );

        assertEquals("Explícame paso a paso, por favor?\n\nGracias!", response.promptContent());
        assertTrue(response.tokensSavedCompression() > 0);

        RequestLog persistedLog = logCaptor.getValue();
        assertEquals("Explícame paso a paso, por favor?\n\nGracias!", persistedLog.getPromptContent());
        assertEquals(response.tokensSavedCompression(), persistedLog.getTokensSavedCompression());
    }

    /**
     * Verifica que {@link RouterService#getStats(String)} sume correctamente {@code totalTokensSavedCompression}
     * a partir de múltiples registros {@link RequestLog}.
     */
    @Test
    @DisplayName("Compresor de Prompts en getStats: Debe agregar totalTokensSavedCompression de todos los registros")
    void shouldAggregateTotalTokensSavedCompressionInGetStats() {
        RequestLog log1 = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("comp-stats-user")
                .promptContent("Prompt comprimido 1")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(8)
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .tokensSavedCompression(6)
                .build();

        RequestLog log2 = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("comp-stats-user")
                .promptContent("Prompt comprimido 2")
                .modelRouted(RouterService.MODEL_PRO)
                .tokensEstimated(20)
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .tokensSavedCompression(11)
                .build();

        when(requestLogRepository.findByUserIdOrderByCreatedAtDesc("comp-stats-user"))
                .thenReturn(List.of(log2, log1));

        RouterStatsResponse stats = routerService.getStats("comp-stats-user");

        assertEquals(2L, stats.totalRequests());
        assertEquals(17L, stats.totalTokensSavedCompression());
    }

    /**
     * Verifica el enrutamiento multi-proveedor en {@link RouterService#routePrompt(PromptRequest)}:
     * <ul>
     *   <li>{@code OPENAI} simple -> {@code gpt-4o-mini}.</li>
     *   <li>{@code OPENAI} complejo -> {@code gpt-4o}.</li>
     *   <li>{@code ANTHROPIC} simple -> {@code claude-3-5-haiku}.</li>
     *   <li>{@code ANTHROPIC} complejo -> {@code claude-3-7-sonnet}.</li>
     * </ul>
     */
    @Test
    @DisplayName("Multi-Proveedor en routePrompt: Debe seleccionar gpt-4o-mini/gpt-4o para OPENAI y claude-3-5-haiku/claude-3-7-sonnet para ANTHROPIC")
    void shouldRouteToOpenAiAndAnthropicModelsBasedOnComplexityAndProvider() {
        when(requestLogRepository.save(any(RequestLog.class))).thenAnswer(invocation -> {
            RequestLog arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            arg.setCreatedAt(LocalDateTime.now());
            return arg;
        });

        RouterResponse openAiSimple = routerService.routePrompt(
                new PromptRequest("multi-user", "Saludo breve para OpenAI", "OPENAI")
        );
        assertEquals(RouterService.PROVIDER_OPENAI, openAiSimple.provider());
        assertEquals(RouterService.MODEL_OPENAI_FAST, openAiSimple.modelRouted());

        RouterResponse openAiComplex = routerService.routePrompt(
                new PromptRequest("multi-user", "Necesito optimizar la arquitectura con OpenAI", "openai")
        );
        assertEquals(RouterService.PROVIDER_OPENAI, openAiComplex.provider());
        assertEquals(RouterService.MODEL_OPENAI_PRO, openAiComplex.modelRouted());

        RouterResponse anthropicSimple = routerService.routePrompt(
                new PromptRequest("multi-user", "Saludo breve para Claude", "ANTHROPIC")
        );
        assertEquals(RouterService.PROVIDER_ANTHROPIC, anthropicSimple.provider());
        assertEquals(RouterService.MODEL_ANTHROPIC_FAST, anthropicSimple.modelRouted());

        RouterResponse anthropicComplex = routerService.routePrompt(
                new PromptRequest("multi-user", "Necesito analizar en profundidad la arquitectura con Claude", "anthropic")
        );
        assertEquals(RouterService.PROVIDER_ANTHROPIC, anthropicComplex.provider());
        assertEquals(RouterService.MODEL_ANTHROPIC_PRO, anthropicComplex.modelRouted());
    }

    /**
     * Verifica que solicitar un proveedor no soportado (ej. {@code "META_LLAMA"}) lance
     * {@link InvalidPromptException} sin guardar ningún registro en base de datos.
     */
    @Test
    @DisplayName("Multi-Proveedor Validación: Debe lanzar InvalidPromptException cuando el proveedor no está soportado")
    void shouldRejectUnsupportedProviderWithInvalidPromptException() {
        InvalidPromptException ex = assertThrows(
                InvalidPromptException.class,
                () -> routerService.routePrompt(new PromptRequest("multi-user", "Hola", "META_LLAMA"))
        );

        assertTrue(ex.getMessage().contains("META_LLAMA"));
        verify(requestLogRepository, never()).save(any(RequestLog.class));
    }

    /**
     * Verifica que {@link RouterService#getStats(String)} calcule con exactitud {@code estimatedActualCostUsd}
     * y {@code estimatedBaselineProCostUsd} aplicando las tarifas oficiales de cada proveedor:
     * <ul>
     *   <li>{@code OPENAI}: {@code gpt-4o-mini} a {@code $0.15/1M} frente a base {@code gpt-4o} a {@code $2.50/1M}.</li>
     *   <li>{@code ANTHROPIC}: {@code claude-3-5-haiku} a {@code $0.80/1M} frente a base {@code claude-3-7-sonnet} a {@code $3.00/1M}.</li>
     * </ul>
     */
    @Test
    @DisplayName("Multi-Proveedor FinOps en getStats: Debe calcular costes reales y base según las tarifas de OpenAI y Anthropic")
    void shouldCalculateMultiProviderCostsAndSavingsInGetStats() {
        // 100.000 tokens en gpt-4o-mini: Real = $0.015000 USD, Base (gpt-4o) = $0.250000 USD
        RequestLog openAiMiniLog = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("multi-finops-user")
                .promptContent("Prompt OpenAI económico")
                .modelRouted(RouterService.MODEL_OPENAI_FAST)
                .provider(RouterService.PROVIDER_OPENAI)
                .tokensEstimated(100_000)
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .cacheHit(false)
                .build();

        // 100.000 tokens en claude-3-5-haiku: Real = $0.080000 USD, Base (claude-3-7-sonnet) = $0.300000 USD
        RequestLog anthropicHaikuLog = RequestLog.builder()
                .id(UUID.randomUUID())
                .userId("multi-finops-user")
                .promptContent("Prompt Anthropic económico")
                .modelRouted(RouterService.MODEL_ANTHROPIC_FAST)
                .provider(RouterService.PROVIDER_ANTHROPIC)
                .tokensEstimated(100_000)
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .cacheHit(false)
                .build();

        when(requestLogRepository.findByUserIdOrderByCreatedAtDesc("multi-finops-user"))
                .thenReturn(List.of(anthropicHaikuLog, openAiMiniLog));

        RouterStatsResponse stats = routerService.getStats("multi-finops-user");

        assertEquals(2L, stats.totalRequests());
        assertEquals(2L, stats.fastModelRequests());
        assertEquals(0L, stats.proModelRequests());
        assertEquals(200_000L, stats.totalTokensEstimated());
        // Coste real total: 0.015000 + 0.080000 = $0.095000 USD
        assertEquals(new BigDecimal("0.095000"), stats.estimatedActualCostUsd());
        // Coste base Pro total: 0.250000 + 0.300000 = $0.550000 USD
        assertEquals(new BigDecimal("0.550000"), stats.estimatedBaselineProCostUsd());
        // Ahorro neto: 0.550000 - 0.095000 = $0.455000 USD (82.73%)
        assertEquals(new BigDecimal("0.455000"), stats.estimatedSavingsUsd());
        assertEquals(82.73, stats.savingsPercentage());
    }
}
