package com.tuproyecto.airouter.services;

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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio encargado de evaluar la complejidad de los prompts entrantes,
 * estimar el consumo de tokens, decidir el modelo LLM óptimo en coste/rendimiento,
 * aplicar el cortafuegos presupuestario FinOps por usuario (auto-downgrade al 80% y bloqueo al 100%),
 * invocar la API de Google Gemini en modo híbrido (BYOK, clave de servidor o fallback a simulación),
 * calcular las métricas financieras de ahorro y registrar el historial de peticiones.
 */
@Service
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
     * Longitud máxima permitida para el identificador de usuario según el esquema de base de datos de logs.
     */
    public static final int MAX_USER_ID_LENGTH = 255;

    /**
     * Longitud máxima permitida para el identificador de usuario en la tabla {@code user_budgets}.
     */
    public static final int MAX_BUDGET_USER_ID_LENGTH = 120;

    /**
     * Estado FinOps cuando el usuario no tiene límite presupuestario activo.
     */
    public static final String BUDGET_STATUS_UNLIMITED = "UNLIMITED";

    /**
     * Estado FinOps cuando el gasto del usuario está por debajo del umbral de alerta (80%).
     */
    public static final String BUDGET_STATUS_OK = "OK";

    /**
     * Estado FinOps cuando el gasto alcanza o supera el 80% y se activa la degradación automática a Flash.
     */
    public static final String BUDGET_STATUS_WARNING_80 = "WARNING_80_DOWNGRADE";

    /**
     * Estado FinOps cuando el usuario alcanza o supera el 100% de su presupuesto y queda bloqueado (HTTP 402).
     */
    public static final String BUDGET_STATUS_EXCEEDED_100 = "EXCEEDED_100_BLOCKED";

    /**
     * Porcentaje de consumo a partir del cual se activa la degradación automática (Auto-Downgrade) a {@link #MODEL_FAST}.
     */
    public static final double BUDGET_DOWNGRADE_THRESHOLD_PERCENT = 80.0;

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
     * Escala decimal empleada para redondear el porcentaje de ahorro económico o uso presupuestario.
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
     * Repositorio JPA para la gestión de los límites presupuestarios por usuario (FinOps).
     */
    private UserBudgetRepository userBudgetRepository;

    /**
     * Cliente HTTP síncrono para comunicarse con la API oficial de Google Gemini.
     */
    private final GeminiRestClient geminiRestClient;

    /**
     * Clave de API global configurada a nivel de servidor como segundo nivel del modo híbrido.
     */
    private final String serverApiKey;

    /**
     * Constructor de compatibilidad para pruebas unitarias que solo requieren el repositorio JPA de logs
     * y operan exclusivamente en modo simulación sin cortafuegos presupuestario.
     *
     * @param requestLogRepository repositorio de persistencia de registros de peticiones
     */
    public RouterService(RequestLogRepository requestLogRepository) {
        this(requestLogRepository, null, null, "");
    }

    /**
     * Constructor de compatibilidad para pruebas unitarias de presupuestos que inyectan
     * el repositorio de logs y el repositorio de presupuestos de usuario.
     *
     * @param requestLogRepository repositorio de persistencia de registros de peticiones
     * @param userBudgetRepository repositorio de persistencia de presupuestos de usuario
     */
    public RouterService(
            RequestLogRepository requestLogRepository,
            UserBudgetRepository userBudgetRepository) {
        this(requestLogRepository, userBudgetRepository, null, "");
    }

    /**
     * Constructor de compatibilidad para pruebas unitarias que inyectan el repositorio de logs,
     * el cliente HTTP de Google Gemini y la clave de API de servidor.
     *
     * @param requestLogRepository repositorio de persistencia de registros de peticiones
     * @param geminiRestClient     cliente HTTP para invocar la API de Google Gemini
     * @param serverApiKey         clave de API configurada en el servidor ({@code airouter.gemini.api-key})
     */
    public RouterService(
            RequestLogRepository requestLogRepository,
            GeminiRestClient geminiRestClient,
            String serverApiKey) {
        this(requestLogRepository, null, geminiRestClient, serverApiKey);
    }

    /**
     * Constructor principal empleado por Spring para inyectar los repositorios JPA, el cliente HTTP
     * de Google Gemini y la clave de API opcional de servidor.
     *
     * @param requestLogRepository repositorio de persistencia de registros de peticiones
     * @param userBudgetRepository repositorio de persistencia de límites presupuestarios por usuario
     * @param geminiRestClient     cliente HTTP para invocar la API de Google Gemini
     * @param serverApiKey         clave de API configurada en el servidor ({@code airouter.gemini.api-key})
     */
    @Autowired
    public RouterService(
            RequestLogRepository requestLogRepository,
            UserBudgetRepository userBudgetRepository,
            GeminiRestClient geminiRestClient,
            @Value("${airouter.gemini.api-key:}") String serverApiKey) {
        this.requestLogRepository = requestLogRepository;
        this.userBudgetRepository = userBudgetRepository;
        this.geminiRestClient = geminiRestClient;
        this.serverApiKey = serverApiKey != null ? serverApiKey.trim() : "";
    }

    /**
     * Permite asignar o reemplazar el repositorio de presupuestos en pruebas unitarias.
     *
     * @param userBudgetRepository instancia del repositorio de presupuestos de usuario
     */
    public void setUserBudgetRepository(UserBudgetRepository userBudgetRepository) {
        this.userBudgetRepository = userBudgetRepository;
    }

    /**
     * Evalúa un prompt de entrada sin clave de cliente explícita, delegando en el enrutador híbrido.
     *
     * @param request DTO con el identificador de usuario y el texto del prompt
     * @return DTO {@link RouterResponse} con la decisión de enrutamiento y la respuesta obtenida
     * @throws InvalidPromptException  si la petición, el usuario o el prompt son inválidos
     * @throws BudgetExceededException si el usuario ha alcanzado el 100% de su presupuesto máximo
     */
    @Transactional
    public RouterResponse routePrompt(PromptRequest request) {
        return routePrompt(request, null);
    }

    /**
     * Evalúa un prompt de entrada, verifica el cortafuegos presupuestario del usuario (bloqueo al 100%
     * o degradación automática al 80%), estima sus tokens, selecciona el modelo LLM óptimo,
     * ejecuta la inferencia en modo híbrido (1º BYOK del cliente, 2º clave del servidor,
     * 3º fallback resiliente a simulación), persiste el registro y devuelve la respuesta.
     *
     * @param request      DTO con el identificador de usuario y el texto del prompt
     * @param clientApiKey clave de API opcional proporcionada por el cliente en la cabecera {@code X-API-Key}
     * @return DTO {@link RouterResponse} con la decisión de enrutamiento y la respuesta real o simulada
     * @throws InvalidPromptException  si la petición, el usuario o el prompt son inválidos
     * @throws BudgetExceededException si el usuario ha alcanzado o superado el 100% de su presupuesto configurado
     */
    @Transactional
    public RouterResponse routePrompt(PromptRequest request, String clientApiKey) {
        validateRequest(request);

        String normalizedUserId = request.userId().trim();
        String promptContent = request.prompt();

        int tokensEstimated = estimateTokens(promptContent);
        String initialModel = selectModel(promptContent, tokensEstimated);
        String selectedModel = applyBudgetFirewallAndSelectModel(normalizedUserId, initialModel);

        log.info("Enrutando petición del usuario '{}' [tokens estimados: {}] -> modelo seleccionado: {}",
                normalizedUserId, tokensEstimated, selectedModel);

        String finalResponseText = resolveModelResponse(selectedModel, promptContent, tokensEstimated, clientApiKey);

        RequestLog entityToSave = RequestLog.builder()
                .userId(normalizedUserId)
                .promptContent(promptContent)
                .modelRouted(selectedModel)
                .tokensEstimated(tokensEstimated)
                .createdAt(LocalDateTime.now())
                .build();

        RequestLog savedEntity = requestLogRepository.save(entityToSave);
        return new RouterResponse(
                savedEntity.getId(),
                savedEntity.getUserId(),
                savedEntity.getPromptContent(),
                savedEntity.getModelRouted(),
                savedEntity.getTokensEstimated(),
                finalResponseText,
                savedEntity.getCreatedAt()
        );
    }

    /**
     * Consulta el estado presupuestario actual de un usuario, calculando su gasto acumulado en USD,
     * saldo restante, porcentaje de uso y estado operativo del cortafuegos FinOps.
     *
     * <p>Si el usuario aún no tiene un límite presupuestario registrado en base de datos, devuelve
     * el estado {@link #BUDGET_STATUS_UNLIMITED} con {@code maxBudgetUsd = null} y {@code remainingBudgetUsd = null}.</p>
     *
     * @param userId identificador del usuario a consultar
     * @return DTO {@link UserBudgetResponse} con el estado presupuestario detallado
     * @throws InvalidPromptException si el identificador de usuario es nulo, está en blanco o supera 120 caracteres
     */
    @Transactional(readOnly = true)
    public UserBudgetResponse getUserBudget(String userId) {
        String cleanedUserId = validateAndTrimBudgetUserId(userId);
        BigDecimal currentSpendUsd = getStats(cleanedUserId).estimatedActualCostUsd();

        if (userBudgetRepository == null) {
            return buildUnlimitedBudgetResponse(cleanedUserId, currentSpendUsd);
        }

        Optional<UserBudget> budgetOpt = userBudgetRepository.findByUserId(cleanedUserId);
        if (budgetOpt.isEmpty()
                || budgetOpt.get().getMaxBudgetUsd() == null
                || budgetOpt.get().getMaxBudgetUsd().compareTo(BigDecimal.ZERO) <= 0) {
            return buildUnlimitedBudgetResponse(cleanedUserId, currentSpendUsd);
        }

        return buildActiveBudgetResponse(cleanedUserId, budgetOpt.get(), currentSpendUsd);
    }

    /**
     * Crea, actualiza o elimina el límite presupuestario (Hard Budget Cap) de un usuario.
     *
     * <p>Si {@code request} es nulo, {@code maxBudgetUsd} es nulo o {@code <= 0}, se elimina cualquier
     * límite existente para el usuario y se devuelve el estado {@link #BUDGET_STATUS_UNLIMITED}.
     * En caso contrario, se persiste el límite escalado a 6 decimales junto con la preferencia
     * {@code autoDowngradeEnabled} (por defecto {@code true} si se recibe nula).</p>
     *
     * @param userId  identificador del usuario cuyo presupuesto se desea configurar
     * @param request DTO con el presupuesto máximo en USD y el indicador de auto-downgrade
     * @return DTO {@link UserBudgetResponse} con el estado actualizado del presupuesto
     * @throws InvalidPromptException si el identificador de usuario es nulo, está en blanco o supera 120 caracteres
     */
    @Transactional
    public UserBudgetResponse updateUserBudget(String userId, UserBudgetRequest request) {
        String cleanedUserId = validateAndTrimBudgetUserId(userId);
        BigDecimal currentSpendUsd = getStats(cleanedUserId).estimatedActualCostUsd();

        if (request == null
                || request.maxBudgetUsd() == null
                || request.maxBudgetUsd().compareTo(BigDecimal.ZERO) <= 0) {
            if (userBudgetRepository != null) {
                userBudgetRepository.findByUserId(cleanedUserId).ifPresent(userBudgetRepository::delete);
            }
            return buildUnlimitedBudgetResponse(cleanedUserId, currentSpendUsd);
        }

        BigDecimal scaledMaxBudget = request.maxBudgetUsd().setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);
        boolean autoDowngrade = request.autoDowngradeEnabled() == null || request.autoDowngradeEnabled();

        if (userBudgetRepository == null) {
            UserBudget transientBudget = UserBudget.builder()
                    .userId(cleanedUserId)
                    .maxBudgetUsd(scaledMaxBudget)
                    .autoDowngradeEnabled(autoDowngrade)
                    .updatedAt(LocalDateTime.now())
                    .build();
            return buildActiveBudgetResponse(cleanedUserId, transientBudget, currentSpendUsd);
        }

        UserBudget budgetEntity = userBudgetRepository.findByUserId(cleanedUserId)
                .orElseGet(() -> UserBudget.builder()
                        .userId(cleanedUserId)
                        .build());

        budgetEntity.setMaxBudgetUsd(scaledMaxBudget);
        budgetEntity.setAutoDowngradeEnabled(autoDowngrade);
        budgetEntity.setUpdatedAt(LocalDateTime.now());

        UserBudget savedBudget = userBudgetRepository.save(budgetEntity);
        return buildActiveBudgetResponse(cleanedUserId, savedBudget, currentSpendUsd);
    }

    /**
     * Evalúa las reglas del cortafuegos presupuestario antes de procesar un prompt:
     * <ul>
     *   <li><b>Regla del 100% (Hard Cap):</b> Si {@code currentSpendUsd >= maxBudgetUsd}, lanza
     *       {@link BudgetExceededException} (HTTP 402 Payment Required).</li>
     *   <li><b>Regla del 80% (Auto-Downgrade):</b> Si el consumo alcanza o supera el 80% y
     *       {@code autoDowngradeEnabled} es {@code true}, fuerza el modelo {@link #MODEL_FAST}.</li>
     * </ul>
     *
     * @param userId       identificador normalizado del usuario
     * @param initialModel modelo inicialmente seleccionado por la heurística de complejidad
     * @return modelo definitivo a utilizar ({@code initialModel} o {@link #MODEL_FAST} si aplica auto-downgrade)
     * @throws BudgetExceededException si el gasto acumulado iguala o supera el presupuesto máximo
     */
    private String applyBudgetFirewallAndSelectModel(String userId, String initialModel) {
        if (userBudgetRepository == null) {
            return initialModel;
        }

        Optional<UserBudget> budgetOpt = userBudgetRepository.findByUserId(userId);
        if (budgetOpt.isEmpty()
                || budgetOpt.get().getMaxBudgetUsd() == null
                || budgetOpt.get().getMaxBudgetUsd().compareTo(BigDecimal.ZERO) <= 0) {
            return initialModel;
        }

        UserBudget budget = budgetOpt.get();
        BigDecimal maxBudgetUsd = budget.getMaxBudgetUsd().setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal currentSpendUsd = getStats(userId).estimatedActualCostUsd();

        if (currentSpendUsd.compareTo(maxBudgetUsd) >= 0) {
            log.warn("Cortafuegos FinOps [HARD CAP 100%]: Usuario '{}' bloqueado (gasto actual: ${} USD / límite: ${} USD).",
                    userId, currentSpendUsd.toPlainString(), maxBudgetUsd.toPlainString());
            throw new BudgetExceededException(userId, currentSpendUsd, maxBudgetUsd);
        }

        double usagePercentage = calculateBudgetUsagePercentage(currentSpendUsd, maxBudgetUsd);
        boolean autoDowngrade = budget.getAutoDowngradeEnabled() == null || budget.getAutoDowngradeEnabled();

        if (usagePercentage >= BUDGET_DOWNGRADE_THRESHOLD_PERCENT && autoDowngrade) {
            if (!MODEL_FAST.equals(initialModel)) {
                log.info("Cortafuegos FinOps [AUTO-DOWNGRADE >= 80%]: Usuario '{}' al {}% de su presupuesto. Degradando modelo '{}' -> '{}'.",
                        userId, usagePercentage, initialModel, MODEL_FAST);
            }
            return MODEL_FAST;
        }

        return initialModel;
    }

    /**
     * Valida y recorta el identificador de usuario recibido en las operaciones de presupuesto.
     *
     * @param userId identificador de usuario a validar
     * @return identificador de usuario sin espacios laterales
     * @throws InvalidPromptException si el identificador es nulo, vacío o supera {@link #MAX_BUDGET_USER_ID_LENGTH} caracteres
     */
    private String validateAndTrimBudgetUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidPromptException("El identificador de usuario (userId) es obligatorio y no puede estar en blanco.");
        }
        String trimmed = userId.trim();
        if (trimmed.length() > MAX_BUDGET_USER_ID_LENGTH) {
            throw new InvalidPromptException(
                    "El identificador de usuario (userId) no puede superar los " + MAX_BUDGET_USER_ID_LENGTH + " caracteres.");
        }
        return trimmed;
    }

    /**
     * Construye la respuesta presupuestaria para un usuario sin límite configurado ({@code UNLIMITED}).
     *
     * @param userId          identificador del usuario
     * @param currentSpendUsd gasto acumulado actual en USD
     * @return DTO {@link UserBudgetResponse} en estado {@link #BUDGET_STATUS_UNLIMITED}
     */
    private UserBudgetResponse buildUnlimitedBudgetResponse(String userId, BigDecimal currentSpendUsd) {
        return new UserBudgetResponse(
                userId,
                null,
                currentSpendUsd.setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP),
                null,
                0.0,
                true,
                BUDGET_STATUS_UNLIMITED
        );
    }

    /**
     * Construye la respuesta presupuestaria para un usuario con límite activo ({@code maxBudgetUsd > 0}).
     *
     * @param userId          identificador del usuario
     * @param budget          entidad {@link UserBudget} con el límite y configuración del usuario
     * @param currentSpendUsd gasto acumulado actual en USD
     * @return DTO {@link UserBudgetResponse} con saldo restante, porcentaje y estado calculados
     */
    private UserBudgetResponse buildActiveBudgetResponse(
            String userId,
            UserBudget budget,
            BigDecimal currentSpendUsd) {
        BigDecimal scaledSpend = currentSpendUsd.setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal scaledMaxBudget = budget.getMaxBudgetUsd().setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal remainingBudgetUsd = scaledMaxBudget.subtract(scaledSpend)
                .max(BigDecimal.ZERO)
                .setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);

        double usagePercentage = calculateBudgetUsagePercentage(scaledSpend, scaledMaxBudget);
        boolean autoDowngrade = budget.getAutoDowngradeEnabled() == null || budget.getAutoDowngradeEnabled();

        String status;
        if (scaledSpend.compareTo(scaledMaxBudget) >= 0) {
            status = BUDGET_STATUS_EXCEEDED_100;
        } else if (usagePercentage >= BUDGET_DOWNGRADE_THRESHOLD_PERCENT && autoDowngrade) {
            status = BUDGET_STATUS_WARNING_80;
        } else {
            status = BUDGET_STATUS_OK;
        }

        return new UserBudgetResponse(
                userId,
                scaledMaxBudget,
                scaledSpend,
                remainingBudgetUsd,
                usagePercentage,
                autoDowngrade,
                status
        );
    }

    /**
     * Calcula el porcentaje de consumo presupuestario {@code (currentSpendUsd / maxBudgetUsd) * 100}
     * redondeado a 2 decimales.
     *
     * @param currentSpendUsd gasto acumulado actual en USD
     * @param maxBudgetUsd    presupuesto máximo en USD (debe ser mayor que cero)
     * @return porcentaje de uso con 2 decimales
     */
    private double calculateBudgetUsagePercentage(BigDecimal currentSpendUsd, BigDecimal maxBudgetUsd) {
        if (maxBudgetUsd == null || maxBudgetUsd.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        return currentSpendUsd.multiply(ONE_HUNDRED)
                .divide(maxBudgetUsd, INTERNAL_CALCULATION_SCALE, RoundingMode.HALF_UP)
                .setScale(PERCENTAGE_DECIMAL_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Resuelve la clave de API efectiva aplicando la jerarquía de 3 niveles del modo híbrido:
     * <ol>
     *   <li>Clave BYOK proporcionada por el usuario en la petición ({@code clientApiKey}).</li>
     *   <li>Clave global configurada en el servidor ({@code airouter.gemini.api-key}).</li>
     *   <li>{@link Optional#empty()} si ninguna clave está disponible (activa el modo simulación).</li>
     * </ol>
     *
     * @param clientApiKey clave opcional recibida en la cabecera HTTP de la petición
     * @return {@link Optional} con la clave efectiva recortada, o vacío si debe operar en simulación
     */
    public Optional<String> resolveEffectiveApiKey(String clientApiKey) {
        if (clientApiKey != null && !clientApiKey.isBlank()) {
            return Optional.of(clientApiKey.trim());
        }
        if (serverApiKey != null && !serverApiKey.isBlank()) {
            return Optional.of(serverApiKey.trim());
        }
        return Optional.empty();
    }

    /**
     * Obtiene la respuesta textual del modelo invocando Google Gemini si existe una clave activa
     * y el cliente HTTP está disponible, o generando la respuesta simulada de respaldo en caso contrario.
     *
     * @param selectedModel   modelo LLM elegido por la heurística de costes
     * @param promptContent   contenido del prompt del usuario
     * @param tokensEstimated cantidad de tokens estimada
     * @param clientApiKey    clave opcional enviada por el usuario
     * @return texto de respuesta real de Gemini o respuesta simulada de fallback
     */
    private String resolveModelResponse(
            String selectedModel,
            String promptContent,
            int tokensEstimated,
            String clientApiKey) {
        Optional<String> effectiveApiKey = resolveEffectiveApiKey(clientApiKey);
        if (effectiveApiKey.isPresent() && geminiRestClient != null) {
            Optional<String> realResponse = geminiRestClient.generateContent(
                    selectedModel,
                    promptContent.trim(),
                    effectiveApiKey.get()
            );
            if (realResponse.isPresent() && !realResponse.get().isBlank()) {
                return realResponse.get();
            }
        }
        return buildSimulatedResponse(selectedModel, tokensEstimated);
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
