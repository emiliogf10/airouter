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
import com.tuproyecto.airouter.models.entities.RequestLog;
import com.tuproyecto.airouter.repositories.RequestLogRepository;
import java.time.LocalDateTime;
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
 * de dominio y persistencia en el repositorio.
 */
@ExtendWith(MockitoExtension.class)
class RouterServiceTest {

    @Mock
    private RequestLogRepository requestLogRepository;

    @InjectMocks
    private RouterService routerService;

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

    @Test
    @DisplayName("Debe lanzar InvalidPromptException al llamar directamente a estimateTokens con null o cadena en blanco")
    void shouldThrowInvalidPromptExceptionWhenEstimateTokensReceivesNullOrBlank() {
        assertThrows(InvalidPromptException.class, () -> routerService.estimateTokens(null));
        assertThrows(InvalidPromptException.class, () -> routerService.estimateTokens(""));
        assertThrows(InvalidPromptException.class, () -> routerService.estimateTokens("   "));
        assertThrows(InvalidPromptException.class, () -> routerService.estimateTokens("\t\n\r "));
    }

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
}
