package com.tuproyecto.airouter.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tuproyecto.airouter.exceptions.InvalidPromptException;
import com.tuproyecto.airouter.models.dto.PromptRequest;
import com.tuproyecto.airouter.models.dto.RouterResponse;
import com.tuproyecto.airouter.models.dto.RouterStatsResponse;
import com.tuproyecto.airouter.models.entities.RequestLog;
import com.tuproyecto.airouter.repositories.RequestLogRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
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
 * de dominio, cálculo de estadísticas de ahorro y persistencia en el repositorio.
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
}
