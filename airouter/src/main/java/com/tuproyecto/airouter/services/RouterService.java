package com.tuproyecto.airouter.services;

import com.tuproyecto.airouter.exceptions.InvalidPromptException;
import com.tuproyecto.airouter.models.dto.PromptRequest;
import com.tuproyecto.airouter.models.dto.RouterResponse;
import com.tuproyecto.airouter.models.dto.RouterStatsResponse;
import com.tuproyecto.airouter.models.entities.RequestLog;
import com.tuproyecto.airouter.repositories.RequestLogRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio encargado de evaluar la complejidad de los prompts entrantes,
 * estimar el consumo de tokens, decidir el modelo LLM óptimo en coste/rendimiento,
 * calcular las métricas financieras de ahorro y registrar el historial de peticiones.
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
     * Tarifa de referencia en dólares estadounidenses (USD) por cada millón de tokens
     * procesados por el modelo económico ({@link #MODEL_FAST}).
     */
    public static final BigDecimal COST_PER_MILLION_TOKENS_FAST = new BigDecimal("0.15");

    /**
     * Tarifa de referencia en dólares estadounidenses (USD) por cada millón de tokens
     * procesados por el modelo avanzado ({@link #MODEL_PRO}).
     */
    public static final BigDecimal COST_PER_MILLION_TOKENS_PRO = new BigDecimal("1.25");

    /**
     * Factor divisor equivalente a un millón de tokens para el cálculo de tarifas unitarias.
     */
    private static final BigDecimal ONE_MILLION_TOKENS = new BigDecimal("1000000");

    /**
     * Factor multiplicador para expresar proporciones como porcentaje sobre 100.
     */
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /**
     * Escala decimal estándar empleada para expresar importes monetarios de tokens en USD.
     */
    private static final int COST_DECIMAL_SCALE = 6;

    /**
     * Escala decimal intermedia de alta precisión para divisiones financieras antes del redondeo final.
     */
    private static final int INTERNAL_CALCULATION_SCALE = 12;

    /**
     * Escala decimal empleada para redondear el porcentaje de ahorro económico.
     */
    private static final int PERCENTAGE_DECIMAL_SCALE = 2;

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

    /**
     * Repositorio JPA para la persistencia y consulta del historial de peticiones evaluadas.
     */
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
        List<RequestLog> logs = fetchLogsByOptionalUserId(userId);
        return logs.stream()
                .map(this::toRouterResponse)
                .toList();
    }

    /**
     * Calcula las estadísticas agregadas de tráfico, consumo de tokens y ahorro financiero en USD,
     * permitiendo filtrar opcionalmente por un usuario concreto.
     *
     * <p>El ahorro económico se obtiene comparando el coste real estimado (según el modelo asignado
     * a cada petición: {@link #COST_PER_MILLION_TOKENS_FAST} para {@link #MODEL_FAST} y
     * {@link #COST_PER_MILLION_TOKENS_PRO} para {@link #MODEL_PRO}) frente al coste base hipotético
     * que habría supuesto procesar la totalidad de los tokens en el modelo avanzado {@link #MODEL_PRO}.</p>
     *
     * @param userId identificador opcional del usuario para acotar las métricas (nulo o en blanco para métricas globales)
     * @return DTO {@link RouterStatsResponse} con los totales de peticiones, tokens, costes en USD y porcentaje de ahorro
     */
    @Transactional(readOnly = true)
    public RouterStatsResponse getStats(String userId) {
        List<RequestLog> logs = fetchLogsByOptionalUserId(userId);

        long totalRequests = logs.size();
        long fastModelRequests = 0L;
        long proModelRequests = 0L;
        long fastModelTokens = 0L;
        long proModelTokens = 0L;

        for (RequestLog logEntry : logs) {
            long tokens = logEntry.getTokensEstimated() != null ? logEntry.getTokensEstimated() : 0L;
            if (MODEL_FAST.equals(logEntry.getModelRouted())) {
                fastModelRequests++;
                fastModelTokens += tokens;
            } else if (MODEL_PRO.equals(logEntry.getModelRouted())) {
                proModelRequests++;
                proModelTokens += tokens;
            }
        }

        long totalTokensEstimated = fastModelTokens + proModelTokens;

        BigDecimal rawFastCost = BigDecimal.valueOf(fastModelTokens)
                .multiply(COST_PER_MILLION_TOKENS_FAST)
                .divide(ONE_MILLION_TOKENS, INTERNAL_CALCULATION_SCALE, RoundingMode.HALF_UP);
        BigDecimal rawProCost = BigDecimal.valueOf(proModelTokens)
                .multiply(COST_PER_MILLION_TOKENS_PRO)
                .divide(ONE_MILLION_TOKENS, INTERNAL_CALCULATION_SCALE, RoundingMode.HALF_UP);

        BigDecimal rawActualCost = rawFastCost.add(rawProCost);
        BigDecimal rawBaselineProCost = BigDecimal.valueOf(totalTokensEstimated)
                .multiply(COST_PER_MILLION_TOKENS_PRO)
                .divide(ONE_MILLION_TOKENS, INTERNAL_CALCULATION_SCALE, RoundingMode.HALF_UP);

        BigDecimal estimatedActualCostUsd = rawActualCost.setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal estimatedBaselineProCostUsd = rawBaselineProCost.setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal estimatedSavingsUsd = estimatedBaselineProCostUsd
                .subtract(estimatedActualCostUsd)
                .setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);

        double savingsPercentage = 0.0;
        if (totalTokensEstimated > 0 && estimatedBaselineProCostUsd.compareTo(BigDecimal.ZERO) > 0) {
            savingsPercentage = rawBaselineProCost.subtract(rawActualCost)
                    .divide(rawBaselineProCost, INTERNAL_CALCULATION_SCALE, RoundingMode.HALF_UP)
                    .multiply(ONE_HUNDRED)
                    .setScale(PERCENTAGE_DECIMAL_SCALE, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return new RouterStatsResponse(
                totalRequests,
                totalTokensEstimated,
                fastModelRequests,
                proModelRequests,
                fastModelTokens,
                proModelTokens,
                estimatedActualCostUsd,
                estimatedBaselineProCostUsd,
                estimatedSavingsUsd,
                savingsPercentage
        );
    }

    /**
     * Recupera la lista de registros {@link RequestLog} desde el repositorio aplicando el filtro
     * opcional por usuario si {@code userId} contiene caracteres distintos de espacio en blanco.
     *
     * @param userId identificador opcional del usuario
     * @return lista de entidades {@link RequestLog} ordenadas por fecha de creación descendente
     */
    private List<RequestLog> fetchLogsByOptionalUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return requestLogRepository.findAllByOrderByCreatedAtDesc();
        }
        return requestLogRepository.findByUserIdOrderByCreatedAtDesc(userId.trim());
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
