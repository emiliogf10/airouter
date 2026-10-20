package com.tuproyecto.airouter.services;

import com.tuproyecto.airouter.exceptions.InvalidPromptException;
import com.tuproyecto.airouter.models.dto.PromptRequest;
import com.tuproyecto.airouter.models.dto.RouterResponse;
import com.tuproyecto.airouter.models.entities.RequestLog;
import com.tuproyecto.airouter.repositories.RequestLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio encargado de evaluar la complejidad de los prompts entrantes,
 * estimar el consumo de tokens, decidir el modelo LLM óptimo en coste/rendimiento
 * y registrar el historial de peticiones.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RouterService {

    /**
     * Identificador del modelo rápido y de bajo coste para prompts sencillos o cortos.
     */
    public static final String MODEL_FAST = "gemini-2.5-flash";

    /**
     * Identificador del modelo avanzado de razonamiento para prompts largos o complejos.
     */
    public static final String MODEL_PRO = "gemini-2.5-pro";

    /**
     * Umbral máximo de tokens estimados para considerar un prompt como ligero.
     * Superado este valor ({@code > 50}), la petición se enruta al modelo avanzado.
     */
    public static final int TOKEN_THRESHOLD = 50;

    /**
     * Longitud máxima permitida para el identificador de usuario según el esquema de base de datos.
     */
    public static final int MAX_USER_ID_LENGTH = 255;

    /**
     * Factor medio de caracteres por token empleado en la heurística determinista de estimación.
     */
    private static final double CHARS_PER_TOKEN = 4.0;

    /**
     * Lista inmutable de palabras o frases clave que denotan alta complejidad técnica o analítica
     * y fuerzan el enrutamiento hacia el modelo avanzado ({@link #MODEL_PRO}).
     */
    public static final List<String> COMPLEX_KEYWORDS = List.of(
            "arquitectura",
            "refactorizar",
            "optimizar",
            "analizar en profundidad"
    );

    private final RequestLogRepository requestLogRepository;

    /**
     * Evalúa un prompt de entrada, estima sus tokens, selecciona el modelo LLM óptimo,
     * persiste el registro de auditoría en la base de datos y devuelve la respuesta simulada.
     *
     * @param request DTO con el identificador de usuario y el texto del prompt
     * @return DTO {@link RouterResponse} con la decisión de enrutamiento y los datos persistidos
     * @throws InvalidPromptException si la petición, el usuario o el prompt son inválidos
     */
    @Transactional
    public RouterResponse routePrompt(PromptRequest request) {
        validateRequest(request);

        String normalizedUserId = request.userId().trim();
        String promptContent = request.prompt();

        int tokensEstimated = estimateTokens(promptContent);
        String selectedModel = selectModel(promptContent, tokensEstimated);

        log.info("Enrutando petición del usuario '{}' [tokens estimados: {}] -> modelo seleccionado: {}",
                normalizedUserId, tokensEstimated, selectedModel);

        RequestLog entityToSave = RequestLog.builder()
                .userId(normalizedUserId)
                .promptContent(promptContent)
                .modelRouted(selectedModel)
                .tokensEstimated(tokensEstimated)
                .createdAt(LocalDateTime.now())
                .build();

        RequestLog savedEntity = requestLogRepository.save(entityToSave);
        return toRouterResponse(savedEntity);
    }

    /**
     * Consulta el historial de peticiones procesadas, permitiendo filtrar opcionalmente por usuario.
     *
     * <p>Si el parámetro {@code userId} es nulo o está en blanco, devuelve el historial global
     * ordenado de más reciente a más antiguo. Si contiene un valor válido, se normaliza mediante
     * {@code trim()} y se filtran únicamente los registros de dicho usuario.</p>
     *
     * @param userId identificador opcional del usuario para filtrar los registros
     * @return lista de {@link RouterResponse} ordenada por fecha de creación descendente
     */
    @Transactional(readOnly = true)
    public List<RouterResponse> getLogs(String userId) {
        List<RequestLog> logs;
        if (userId == null || userId.isBlank()) {
            logs = requestLogRepository.findAllByOrderByCreatedAtDesc();
        } else {
            logs = requestLogRepository.findByUserIdOrderByCreatedAtDesc(userId.trim());
        }

        return logs.stream()
                .map(this::toRouterResponse)
                .toList();
    }

    /**
     * Calcula una estimación determinista de la cantidad de tokens de un prompt.
     *
     * <p>Utiliza la heurística estándar para modelos LLM donde 1 token equivale aproximadamente
     * a 4 caracteres ({@code Math.ceil(prompt.trim().length() / 4.0)}), garantizando siempre
     * un mínimo de 1 token para cualquier prompt válido no vacío.</p>
     *
     * @param prompt texto del prompt cuyos tokens se desean estimar
     * @return número entero de tokens estimados (siempre {@code >= 1})
     * @throws InvalidPromptException si el prompt es nulo o está en blanco
     */
    public int estimateTokens(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new InvalidPromptException("El contenido del prompt no puede ser nulo ni estar en blanco.");
        }
        int trimmedLength = prompt.trim().length();
        return Math.max(1, (int) Math.ceil(trimmedLength / CHARS_PER_TOKEN));
    }

    /**
     * Selecciona el modelo LLM más adecuado en función del volumen de tokens estimados
     * y la presencia de palabras clave de alta complejidad semántica.
     *
     * <p>Se elige el modelo avanzado ({@link #MODEL_PRO}) cuando los tokens estimados superan
     * el umbral {@link #TOKEN_THRESHOLD} ({@code > 50}) o cuando el texto contiene términos
     * complejos como {@code "arquitectura"}, {@code "refactorizar"}, {@code "optimizar"} o
     * {@code "analizar en profundidad"}. En caso contrario, se selecciona el modelo económico
     * y rápido ({@link #MODEL_FAST}).</p>
     *
     * @param prompt          contenido textual del prompt
     * @param tokensEstimated cantidad de tokens previamente estimada para el prompt
     * @return identificador del modelo seleccionado ({@link #MODEL_FAST} o {@link #MODEL_PRO})
     */
    public String selectModel(String prompt, int tokensEstimated) {
        if (tokensEstimated > TOKEN_THRESHOLD) {
            return MODEL_PRO;
        }
        if (prompt != null && containsComplexKeyword(prompt)) {
            return MODEL_PRO;
        }
        return MODEL_FAST;
    }

    /**
     * Verifica que el objeto de petición y sus campos obligatorios cumplan las reglas de negocio
     * y las restricciones del esquema de base de datos.
     *
     * @param request petición a validar
     * @throws InvalidPromptException si algún campo obligatorio falta o incumple restricciones
     */
    private void validateRequest(PromptRequest request) {
        if (request == null) {
            throw new InvalidPromptException("La petición de enrutamiento no puede ser nula.");
        }
        if (request.userId() == null || request.userId().isBlank()) {
            throw new InvalidPromptException("El identificador de usuario (userId) es obligatorio y no puede estar en blanco.");
        }
        if (request.userId().trim().length() > MAX_USER_ID_LENGTH || request.userId().length() > MAX_USER_ID_LENGTH) {
            throw new InvalidPromptException(
                    "El identificador de usuario (userId) no puede superar los " + MAX_USER_ID_LENGTH + " caracteres.");
        }
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new InvalidPromptException("El contenido del prompt es obligatorio y no puede estar en blanco.");
        }
    }

    /**
     * Comprueba de forma insensible a mayúsculas/minúsculas si el prompt contiene alguna
     * palabra o frase clave asociada a tareas de alta complejidad.
     *
     * @param prompt texto del prompt a inspeccionar
     * @return {@code true} si contiene al menos una palabra clave compleja, {@code false} en caso contrario
     */
    private boolean containsComplexKeyword(String prompt) {
        String normalizedPrompt = prompt.toLowerCase(Locale.ROOT);
        return COMPLEX_KEYWORDS.stream().anyMatch(normalizedPrompt::contains);
    }

    /**
     * Construye el mensaje de respuesta simulada indicando el modelo al que se enrutó la petición
     * y los tokens estimados.
     *
     * @param modelRouted     modelo LLM seleccionado
     * @param tokensEstimated cantidad de tokens estimados
     * @return texto descriptivo de la respuesta simulada
     */
    private String buildSimulatedResponse(String modelRouted, Integer tokensEstimated) {
        return String.format(
                "[Simulación AI Cost Router] Petición procesada exitosamente por el modelo '%s' (%d tokens estimados).",
                modelRouted,
                tokensEstimated
        );
    }

    /**
     * Transforma una entidad JPA {@link RequestLog} en su correspondiente DTO {@link RouterResponse}
     * para evitar exponer entidades de persistencia fuera de la capa de servicio.
     *
     * @param entity entidad persistida en base de datos
     * @return DTO {@link RouterResponse} con todos los campos mapeados
     */
    private RouterResponse toRouterResponse(RequestLog entity) {
        return new RouterResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getPromptContent(),
                entity.getModelRouted(),
                entity.getTokensEstimated(),
                buildSimulatedResponse(entity.getModelRouted(), entity.getTokensEstimated()),
                entity.getCreatedAt()
        );
    }
}
