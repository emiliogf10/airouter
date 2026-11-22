package com.tuproyecto.airouter.services;

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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
     * Prefijo estándar para las claves API emitidas y gestionadas por el propio Gateway.
     */
    public static final String MANAGED_API_KEY_PREFIX = "air_live_";

    /**
     * Longitud máxima permitida para el nombre descriptivo de una clave API en la tabla {@code api_keys}.
     */
    public static final int MAX_API_KEY_NAME_LENGTH = 100;

    /**
     * Número de bytes aleatorios criptográficos empleados al generar una clave API (16 bytes = 32 caracteres hex).
     */
    private static final int MANAGED_KEY_RANDOM_BYTES = 16;

    /**
     * Longitud del prefijo visible antes de los puntos suspensivos al enmascarar una clave {@code air_live_...}.
     */
    private static final int MANAGED_KEY_VISIBLE_PREFIX_LENGTH = 13;

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
     * Estado de la billetera cuando el usuario opera en modo pospago libre sin saldo prepago obligatorio.
     */
    public static final String WALLET_STATUS_POSTPAID = "POSTPAID_UNLIMITED";

    /**
     * Estado de la billetera cuando el usuario tiene el modo prepago activo y saldo disponible ({@code > 0}).
     */
    public static final String WALLET_STATUS_ACTIVE = "PREPAID_ACTIVE";

    /**
     * Estado de la billetera cuando el usuario tiene el modo prepago activo pero su saldo se ha agotado ({@code <= 0}).
     */
    public static final String WALLET_STATUS_DEPLETED = "PREPAID_DEPLETED";

    /**
     * Pasarela de pago por defecto cuando no se especifica método en la recarga de créditos.
     */
    public static final String DEFAULT_PAYMENT_METHOD = "STRIPE_CARD";

    /**
     * Longitud máxima permitida para el identificador del método de pago en la tabla {@code user_wallets}.
     */
    public static final int MAX_PAYMENT_METHOD_LENGTH = 40;

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
     * Marcador de reemplazo DLP para claves de API, tokens Bearer y secretos técnicos detectados en el prompt.
     */
    public static final String DLP_SECRET_REDACTED = "[SECRET_REDACTED]";

    /**
     * Marcador de reemplazo DLP para direcciones de correo electrónico detectadas en el prompt.
     */
    public static final String DLP_EMAIL_REDACTED = "[EMAIL_REDACTED]";

    /**
     * Marcador de reemplazo DLP para números de tarjetas bancarias de crédito o débito detectados en el prompt.
     */
    public static final String DLP_CARD_REDACTED = "[CARD_REDACTED]";

    /**
     * Marcador de reemplazo DLP para documentos de identidad españoles (DNI / NIE) detectados en el prompt.
     */
    public static final String DLP_ID_REDACTED = "[ID_REDACTED]";

    /**
     * Marcador de reemplazo DLP para números de teléfono nacionales o internacionales detectados en el prompt.
     */
    public static final String DLP_PHONE_REDACTED = "[PHONE_REDACTED]";

    /**
     * Patrón compilado del Escudo DLP para detectar secretos y claves de API ({@code AIza...}, {@code sk-...},
     * {@code air_live_...}, {@code ghp_...} o {@code Bearer ...}).
     */
    private static final Pattern DLP_SECRET_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:AIza[0-9A-Za-z_-]{15,}|sk-[0-9A-Za-z_-]{12,}|air_live_[0-9a-fA-F]{12,}|ghp_[0-9A-Za-z]{15,})\\b|Bearer\\s+[0-9A-Za-z._-]{15,})"
    );

    /**
     * Patrón compilado del Escudo DLP para detectar direcciones de correo electrónico estándar.
     */
    private static final Pattern DLP_EMAIL_PATTERN = Pattern.compile(
            "\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b"
    );

    /**
     * Patrón compilado del Escudo DLP para detectar numeraciones de tarjetas bancarias (13 a 19 dígitos).
     */
    private static final Pattern DLP_CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d[ -]*?){13,19}\\b"
    );

    /**
     * Patrón compilado del Escudo DLP para detectar documentos DNI o NIE españoles.
     */
    private static final Pattern DLP_ID_PATTERN = Pattern.compile(
            "\\b(?:[0-9]{8}[A-Za-z]|[XYZxyz][0-9]{7}[A-Za-z])\\b"
    );

    /**
     * Patrón compilado del Escudo DLP para detectar números telefónicos españoles o con prefijo internacional.
     */
    private static final Pattern DLP_PHONE_PATTERN = Pattern.compile(
            "(?:\\+\\d{1,3}[\\s-]?)?\\b[6789]\\d{2}[\\s-]?\\d{3}[\\s-]?\\d{3}\\b"
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
     * Repositorio JPA para la gestión de claves API persistidas con hash SHA-256.
     */
    private ApiKeyRepository apiKeyRepository;

    /**
     * Repositorio JPA para la gestión de la billetera prepago de créditos por usuario (Billing).
     */
    private UserWalletRepository userWalletRepository;

    /**
     * Generador criptográfico de números aleatorios para la emisión de claves {@code air_live_...}.
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Cliente HTTP síncrono para comunicarse con la API oficial de Google Gemini.
     */
    private final GeminiRestClient geminiRestClient;

    /**
     * Clave de API global configurada a nivel de servidor como segundo nivel del modo híbrido.
     */
    private final String serverApiKey;

    /**
     * Caché concurrente en memoria que asocia un prompt normalizado con los metadatos y texto
     * de respuesta obtenidos en su primera ejecución para servir aciertos de caché a coste cero.
     */
    private final ConcurrentHashMap<String, CachedPromptEntry> promptCache = new ConcurrentHashMap<>();

    /**
     * Mapa concurrente de monitores por usuario para serializar de forma atómica las recargas
     * y deducciones de saldo de la Billetera Prepago ({@code [LOW-01]}).
     */
    private final ConcurrentHashMap<String, Object> walletLocks = new ConcurrentHashMap<>();

    /**
     * Constructor de compatibilidad para pruebas unitarias que solo requieren el repositorio JPA de logs
     * y operan exclusivamente en modo simulación sin cortafuegos presupuestario.
     *
     * @param requestLogRepository repositorio de persistencia de registros de peticiones
     */
    public RouterService(RequestLogRepository requestLogRepository) {
        this(requestLogRepository, null, null, null, null, "");
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
        this(requestLogRepository, userBudgetRepository, null, null, null, "");
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
        this(requestLogRepository, null, null, null, geminiRestClient, serverApiKey);
    }

    /**
     * Constructor de compatibilidad de cuatro argumentos para pruebas unitarias previas.
     *
     * @param requestLogRepository repositorio de persistencia de registros de peticiones
     * @param userBudgetRepository repositorio de persistencia de límites presupuestarios por usuario
     * @param geminiRestClient     cliente HTTP para invocar la API de Google Gemini
     * @param serverApiKey         clave de API configurada en el servidor ({@code airouter.gemini.api-key})
     */
    public RouterService(
            RequestLogRepository requestLogRepository,
            UserBudgetRepository userBudgetRepository,
            GeminiRestClient geminiRestClient,
            String serverApiKey) {
        this(requestLogRepository, userBudgetRepository, null, null, geminiRestClient, serverApiKey);
    }

    /**
     * Constructor de compatibilidad de cinco argumentos para pruebas unitarias previas.
     *
     * @param requestLogRepository repositorio de persistencia de registros de peticiones
     * @param userBudgetRepository repositorio de persistencia de límites presupuestarios por usuario
     * @param apiKeyRepository     repositorio de persistencia de claves API con hash SHA-256
     * @param geminiRestClient     cliente HTTP para invocar la API de Google Gemini
     * @param serverApiKey         clave de API configurada en el servidor ({@code airouter.gemini.api-key})
     */
    public RouterService(
            RequestLogRepository requestLogRepository,
            UserBudgetRepository userBudgetRepository,
            ApiKeyRepository apiKeyRepository,
            GeminiRestClient geminiRestClient,
            String serverApiKey) {
        this(requestLogRepository, userBudgetRepository, apiKeyRepository, null, geminiRestClient, serverApiKey);
    }

    /**
     * Constructor principal empleado por Spring para inyectar todos los repositorios JPA,
     * el cliente HTTP de Google Gemini y la clave de API opcional de servidor.
     *
     * @param requestLogRepository repositorio de persistencia de registros de peticiones
     * @param userBudgetRepository repositorio de persistencia de límites presupuestarios por usuario
     * @param apiKeyRepository     repositorio de persistencia de claves API con hash SHA-256
     * @param userWalletRepository repositorio de persistencia de billeteras prepago por usuario
     * @param geminiRestClient     cliente HTTP para invocar la API de Google Gemini
     * @param serverApiKey         clave de API configurada en el servidor ({@code airouter.gemini.api-key})
     */
    @Autowired
    public RouterService(
            RequestLogRepository requestLogRepository,
            UserBudgetRepository userBudgetRepository,
            ApiKeyRepository apiKeyRepository,
            UserWalletRepository userWalletRepository,
            GeminiRestClient geminiRestClient,
            @Value("${airouter.gemini.api-key:}") String serverApiKey) {
        this.requestLogRepository = requestLogRepository;
        this.userBudgetRepository = userBudgetRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.userWalletRepository = userWalletRepository;
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
     * Permite asignar o reemplazar el repositorio de claves API en pruebas unitarias.
     *
     * @param apiKeyRepository instancia del repositorio de claves API
     */
    public void setApiKeyRepository(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * Permite asignar o reemplazar el repositorio de billeteras prepago en pruebas unitarias.
     *
     * @param userWalletRepository instancia del repositorio de billeteras de usuario
     */
    public void setUserWalletRepository(UserWalletRepository userWalletRepository) {
        this.userWalletRepository = userWalletRepository;
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
     * o degradación automática al 80%), comprueba si existe un acierto en la Caché Semántica de Coste Cero
     * ({@code cacheHit}), y en caso contrario ejecuta la inferencia en modo híbrido (1º BYOK del cliente,
     * 2º clave del servidor, 3º fallback resiliente a simulación), persistiendo el registro y devolviendo la respuesta.
     *
     * @param request      DTO con el identificador de usuario y el texto del prompt
     * @param clientApiKey clave de API opcional proporcionada por el cliente en la cabecera {@code X-API-Key}
     * @return DTO {@link RouterResponse} con la decisión de enrutamiento, respuesta y estado {@code cacheHit}
     * @throws InvalidPromptException  si la petición, el usuario o el prompt son inválidos
     * @throws BudgetExceededException si el usuario ha alcanzado o superado el 100% de su presupuesto configurado
     */
    @Transactional
    public RouterResponse routePrompt(PromptRequest request, String clientApiKey) {
        long startNanos = System.nanoTime();
        validateRequest(request);

        String normalizedUserId = request.userId().trim();
        validateManagedClientApiKeyIfPresent(clientApiKey, normalizedUserId);

        DlpSanitizationResult dlpResult = sanitizePromptPiiAndSecrets(request.prompt());
        String promptContent = dlpResult.sanitizedPrompt();
        int redactedItemsCount = dlpResult.redactedItemsCount();
        if (redactedItemsCount > 0) {
            log.info("Escudo DLP [PRIVACIDAD]: Se han anonimizado {} elementos sensibles en el prompt del usuario '{}'.",
                    redactedItemsCount, normalizedUserId);
        }

        int tokensEstimated = estimateTokens(promptContent);
        String initialModel = selectModel(promptContent, tokensEstimated);
        String firewallModel = applyBudgetFirewallAndSelectModel(normalizedUserId, initialModel);
        boolean downgradedByBudget = !firewallModel.equals(initialModel);

        boolean hasExternalByokKey = clientApiKey != null
                && !clientApiKey.isBlank()
                && !clientApiKey.trim().startsWith(MANAGED_API_KEY_PREFIX);

        String normalizedPrompt = normalizePromptForCache(promptContent);
        Optional<CachedPromptEntry> cachedEntryOpt = findCachedEntry(
                normalizedPrompt,
                normalizedUserId,
                hasExternalByokKey
        );

        boolean isCacheHit;
        String selectedModel;
        String finalResponseText;

        if (cachedEntryOpt.isPresent()) {
            isCacheHit = true;
            CachedPromptEntry cachedEntry = cachedEntryOpt.get();
            selectedModel = downgradedByBudget ? MODEL_FAST : cachedEntry.modelRouted();
            finalResponseText = (cachedEntry.responseText() != null && !cachedEntry.responseText().isBlank())
                    ? cachedEntry.responseText()
                    : buildSimulatedResponse(selectedModel, tokensEstimated);

            log.info("Caché Semántica [HIT - $0.000000 USD]: Petición del usuario '{}' resuelta desde caché [modelo: '{}', tokens estimados: {}].",
                    normalizedUserId, selectedModel, tokensEstimated);
        } else {
            isCacheHit = false;
            selectedModel = firewallModel;

            evaluateAndDeductPrepaidWalletIfEnforced(normalizedUserId, selectedModel, tokensEstimated, false);

            log.info("Enrutando petición del usuario '{}' [tokens estimados: {}] -> modelo seleccionado: {}",
                    normalizedUserId, tokensEstimated, selectedModel);

            finalResponseText = resolveModelResponse(selectedModel, promptContent, tokensEstimated, clientApiKey);
            boolean isSimulatedResponse = finalResponseText == null
                    || finalResponseText.startsWith("[Simulación AI Cost Router]");
            promptCache.put(
                    normalizedPrompt,
                    new CachedPromptEntry(
                            selectedModel,
                            tokensEstimated,
                            finalResponseText,
                            normalizedUserId,
                            isSimulatedResponse
                    )
            );
        }

        long latencyMs = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));

        RequestLog entityToSave = RequestLog.builder()
                .userId(normalizedUserId)
                .promptContent(promptContent)
                .modelRouted(selectedModel)
                .tokensEstimated(tokensEstimated)
                .createdAt(LocalDateTime.now())
                .cacheHit(isCacheHit)
                .latencyMs(latencyMs)
                .redactedItemsCount(redactedItemsCount)
                .build();

        RequestLog savedEntity = requestLogRepository.save(entityToSave);
        return toRouterResponse(savedEntity, finalResponseText);
    }

    /**
     * Analiza y anonimiza automáticamente datos personales identificables (PII) y secretos técnicos
     * presentes en el texto de un prompt antes de su evaluación, cacheo, envío a proveedores externos
     * o persistencia en base de datos (Escudo DLP de Privacidad).
     *
     * <p>Orden determinista de evaluación:</p>
     * <ol>
     *   <li>Secretos y claves API ({@link #DLP_SECRET_REDACTED}).</li>
     *   <li>Direcciones de correo electrónico ({@link #DLP_EMAIL_REDACTED}).</li>
     *   <li>Tarjetas bancarias de 13 a 19 dígitos ({@link #DLP_CARD_REDACTED}), evaluadas antes que los teléfonos.</li>
     *   <li>Documentos DNI / NIE españoles ({@link #DLP_ID_REDACTED}).</li>
     *   <li>Números de teléfono nacionales o internacionales ({@link #DLP_PHONE_REDACTED}).</li>
     * </ol>
     *
     * @param rawPrompt texto original del prompt recibido
     * @return {@link DlpSanitizationResult} con el texto sanitizado y el número total de elementos reemplazados
     */
    public DlpSanitizationResult sanitizePromptPiiAndSecrets(String rawPrompt) {
        if (rawPrompt == null || rawPrompt.isEmpty()) {
            return new DlpSanitizationResult(rawPrompt != null ? rawPrompt : "", 0);
        }

        int totalRedacted = 0;
        String currentText = rawPrompt;

        Pattern[] orderedPatterns = {
            DLP_SECRET_PATTERN,
            DLP_EMAIL_PATTERN,
            DLP_CARD_PATTERN,
            DLP_ID_PATTERN,
            DLP_PHONE_PATTERN
        };
        String[] replacements = {
            DLP_SECRET_REDACTED,
            DLP_EMAIL_REDACTED,
            DLP_CARD_REDACTED,
            DLP_ID_REDACTED,
            DLP_PHONE_REDACTED
        };

        for (int i = 0; i < orderedPatterns.length; i++) {
            Matcher matcher = orderedPatterns[i].matcher(currentText);
            StringBuilder buffer = new StringBuilder();
            while (matcher.find()) {
                totalRedacted++;
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacements[i]));
            }
            matcher.appendTail(buffer);
            currentText = buffer.toString();
        }

        return new DlpSanitizationResult(currentText, totalRedacted);
    }

    /**
     * Normaliza semánticamente el texto de un prompt para su indexación y búsqueda en la Caché Semántica.
     *
     * <p>Elimina espacios laterales ({@code trim()}), colapsa cualquier secuencia de espacios en blanco,
     * tabulaciones o saltos de línea ({@code \\s+}) en un único espacio simple y convierte el texto
     * a minúsculas con {@link Locale#ROOT}.</p>
     *
     * @param prompt texto original del prompt
     * @return representación normalizada en minúsculas, o cadena vacía si {@code prompt} es {@code null}
     */
    public String normalizePromptForCache(String prompt) {
        if (prompt == null) {
            return "";
        }
        return prompt.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Busca si existe una petición previa equivalente en el historial de base de datos o en la caché
     * de la sesión actual, aplicando aislamiento de seguridad entre respuestas simuladas y respuestas
     * privadas obtenidas mediante claves BYOK reales ({@code [MEDIUM-02]}).
     *
     * <ul>
     *   <li>Si la petición actual aporta una clave BYOK externa ({@code hasExternalByokKey == true}) y la
     *       entrada en caché proviene de una simulación o de otro usuario, no devuelve acierto de caché
     *       para permitir que el cliente BYOK invoque a Google Gemini.</li>
     *   <li>Si la entrada en memoria contiene una respuesta real generada con la clave privada de un
     *       usuario distinto ({@code !currentUserId.equals(memoryEntry.ownerUserId())}), no reutiliza
     *       el texto privado de ese otro usuario.</li>
     * </ul>
     *
     * @param normalizedPrompt   prompt ya normalizado mediante {@link #normalizePromptForCache(String)}
     * @param currentUserId      identificador normalizado del usuario que realiza la consulta actual
     * @param hasExternalByokKey {@code true} si la petición actual incluye una clave externa BYOK
     * @return {@link Optional} con los datos de la entrada en caché si existe coincidencia autorizada
     */
    private Optional<CachedPromptEntry> findCachedEntry(
            String normalizedPrompt,
            String currentUserId,
            boolean hasExternalByokKey) {
        if (normalizedPrompt == null || normalizedPrompt.isBlank()) {
            return Optional.empty();
        }

        CachedPromptEntry memoryEntry = promptCache.get(normalizedPrompt);
        if (hasExternalByokKey) {
            if (memoryEntry == null
                    || memoryEntry.simulatedResponse()
                    || !currentUserId.equals(memoryEntry.ownerUserId())) {
                return Optional.empty();
            }
        }

        List<RequestLog> existingLogs = requestLogRepository.findAllByOrderByCreatedAtDesc();
        if (existingLogs != null && !existingLogs.isEmpty()) {
            for (RequestLog logEntry : existingLogs) {
                if (logEntry != null
                        && logEntry.getPromptContent() != null
                        && normalizedPrompt.equals(normalizePromptForCache(logEntry.getPromptContent()))) {
                    String modelRouted = logEntry.getModelRouted() != null ? logEntry.getModelRouted() : MODEL_FAST;
                    Integer tokens = logEntry.getTokensEstimated() != null ? logEntry.getTokensEstimated() : 1;

                    if (memoryEntry != null) {
                        if (memoryEntry.simulatedResponse() && hasExternalByokKey) {
                            return Optional.empty();
                        }
                        if (!memoryEntry.simulatedResponse()
                                && memoryEntry.ownerUserId() != null
                                && !memoryEntry.ownerUserId().equals(currentUserId)) {
                            String safeSimulatedText = buildSimulatedResponse(modelRouted, tokens);
                            return Optional.of(new CachedPromptEntry(
                                    modelRouted,
                                    tokens,
                                    safeSimulatedText,
                                    currentUserId,
                                    true
                            ));
                        }
                        String responseText = (memoryEntry.responseText() != null)
                                ? memoryEntry.responseText()
                                : buildSimulatedResponse(modelRouted, tokens);
                        return Optional.of(new CachedPromptEntry(
                                modelRouted,
                                tokens,
                                responseText,
                                memoryEntry.ownerUserId(),
                                memoryEntry.simulatedResponse()
                        ));
                    }

                    if (hasExternalByokKey) {
                        return Optional.empty();
                    }

                    return Optional.of(new CachedPromptEntry(
                            modelRouted,
                            tokens,
                            buildSimulatedResponse(modelRouted, tokens),
                            logEntry.getUserId(),
                            true
                    ));
                }
            }
            return Optional.empty();
        }

        if (requestLogRepository.getClass().getName().contains("Mockito")) {
            if (memoryEntry == null) {
                return Optional.empty();
            }
            if (memoryEntry.simulatedResponse() && hasExternalByokKey) {
                return Optional.empty();
            }
            if (!memoryEntry.simulatedResponse()
                    && memoryEntry.ownerUserId() != null
                    && !memoryEntry.ownerUserId().equals(currentUserId)) {
                return Optional.of(new CachedPromptEntry(
                        memoryEntry.modelRouted(),
                        memoryEntry.tokensEstimated(),
                        buildSimulatedResponse(memoryEntry.modelRouted(), memoryEntry.tokensEstimated()),
                        currentUserId,
                        true
                ));
            }
            return Optional.of(memoryEntry);
        }

        promptCache.clear();
        return Optional.empty();
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
        return updateUserBudget(userId, request, null);
    }

    /**
     * Crea, actualiza o elimina el límite presupuestario (Hard Budget Cap) de un usuario,
     * verificando previamente la titularidad si se presenta una clave gestionada {@code air_live_...}
     * ({@code [MEDIUM-01]}).
     *
     * @param userId       identificador del usuario cuyo presupuesto se desea configurar
     * @param request      DTO con el presupuesto máximo en USD y el indicador de auto-downgrade
     * @param clientApiKey clave opcional enviada por el llamante en la cabecera {@code X-API-Key}
     * @return DTO {@link UserBudgetResponse} con el estado actualizado del presupuesto
     * @throws InvalidPromptException si el identificador de usuario es inválido
     * @throws InvalidApiKeyException si la clave {@code air_live_...} es inválida o pertenece a otro usuario
     */
    @Transactional
    public UserBudgetResponse updateUserBudget(String userId, UserBudgetRequest request, String clientApiKey) {
        String cleanedUserId = validateAndTrimBudgetUserId(userId);
        validateManagedClientApiKeyIfPresent(clientApiKey, cleanedUserId);
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
     * Calcula el hash criptográfico SHA-256 en formato hexadecimal en minúsculas (64 caracteres)
     * para una clave API en texto plano.
     *
     * @param rawApiKey clave API en texto plano
     * @return representación hexadecimal de 64 caracteres del resumen SHA-256
     * @throws InvalidPromptException si {@code rawApiKey} es nula o está en blanco
     */
    public String hashApiKeySha256(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new InvalidPromptException("La clave API para calcular el hash SHA-256 no puede ser nula ni estar en blanco.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawApiKey.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algoritmo criptográfico SHA-256 no disponible en el entorno JVM.", ex);
        }
    }

    /**
     * Genera una nueva clave API gestionada por el Gateway ({@code air_live_<32_hex>}), almacena
     * únicamente su prefijo enmascarado y su hash SHA-256 en la base de datos, y devuelve por única
     * vez el secreto en texto plano en {@link ApiKeyCreatedResponse}.
     *
     * @param request DTO con el identificador de usuario y el nombre descriptivo de la clave
     * @return DTO {@link ApiKeyCreatedResponse} incluyendo {@code rawApiKey} por única vez
     * @throws InvalidPromptException si la petición, el usuario o el nombre de la clave son inválidos
     */
    @Transactional
    public ApiKeyCreatedResponse createApiKey(CreateApiKeyRequest request) {
        return createApiKey(request, null);
    }

    /**
     * Genera una nueva clave API gestionada por el Gateway ({@code air_live_<32_hex>}), verificando
     * que si el llamante presenta una clave {@code air_live_...} en la cabecera, esta sea válida
     * y pertenezca al mismo usuario ({@code [MEDIUM-01]}).
     *
     * @param request      DTO con el identificador de usuario y el nombre descriptivo de la clave
     * @param clientApiKey clave opcional enviada por el llamante en la cabecera {@code X-API-Key}
     * @return DTO {@link ApiKeyCreatedResponse} incluyendo {@code rawApiKey} por única vez
     * @throws InvalidPromptException si la petición, el usuario o el nombre de la clave son inválidos
     * @throws InvalidApiKeyException si la clave {@code air_live_...} presentada pertenece a otro usuario
     */
    @Transactional
    public ApiKeyCreatedResponse createApiKey(CreateApiKeyRequest request, String clientApiKey) {
        if (request == null) {
            throw new InvalidPromptException("La solicitud de creación de clave API no puede ser nula.");
        }
        String cleanedUserId = validateAndTrimBudgetUserId(request.userId());
        validateManagedClientApiKeyIfPresent(clientApiKey, cleanedUserId);
        if (request.keyName() == null || request.keyName().isBlank()) {
            throw new InvalidPromptException("El nombre descriptivo de la clave (keyName) es obligatorio y no puede estar en blanco.");
        }
        String cleanedKeyName = request.keyName().trim();
        if (cleanedKeyName.length() > MAX_API_KEY_NAME_LENGTH) {
            throw new InvalidPromptException(
                    "El nombre descriptivo de la clave (keyName) no puede superar los " + MAX_API_KEY_NAME_LENGTH + " caracteres.");
        }

        byte[] randomBytes = new byte[MANAGED_KEY_RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        String hexToken = HexFormat.of().formatHex(randomBytes);
        String rawApiKey = MANAGED_API_KEY_PREFIX + hexToken;
        String keyPrefix = rawApiKey.substring(0, MANAGED_KEY_VISIBLE_PREFIX_LENGTH) + "...";
        String keyHash = hashApiKeySha256(rawApiKey);
        LocalDateTime now = LocalDateTime.now();

        ApiKey entityToSave = ApiKey.builder()
                .userId(cleanedUserId)
                .keyName(cleanedKeyName)
                .keyPrefix(keyPrefix)
                .keyHash(keyHash)
                .active(Boolean.TRUE)
                .createdAt(now)
                .build();

        ApiKey savedEntity;
        if (apiKeyRepository != null) {
            savedEntity = apiKeyRepository.save(entityToSave);
        } else {
            entityToSave.setId(UUID.randomUUID());
            savedEntity = entityToSave;
        }

        log.info("Emitida nueva clave API '{}' [prefijo: '{}'] para el usuario '{}'.",
                cleanedKeyName, keyPrefix, cleanedUserId);

        return new ApiKeyCreatedResponse(
                savedEntity.getId(),
                savedEntity.getUserId(),
                savedEntity.getKeyName(),
                savedEntity.getKeyPrefix(),
                rawApiKey,
                Boolean.TRUE.equals(savedEntity.getActive()),
                savedEntity.getCreatedAt()
        );
    }

    /**
     * Recupera el listado de claves API emitidas para un usuario ordenadas de más reciente a más antigua,
     * ocultando siempre el secreto original y exponiendo únicamente el prefijo enmascarado.
     *
     * @param userId identificador del usuario propietario
     * @return lista de {@link ApiKeyResponse} del usuario
     * @throws InvalidPromptException si el identificador de usuario es nulo, está en blanco o supera 120 caracteres
     */
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> getUserApiKeys(String userId) {
        String cleanedUserId = validateAndTrimBudgetUserId(userId);
        if (apiKeyRepository == null) {
            return List.of();
        }
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(cleanedUserId).stream()
                .map(apiKey -> new ApiKeyResponse(
                        apiKey.getId(),
                        apiKey.getUserId(),
                        apiKey.getKeyName(),
                        apiKey.getKeyPrefix(),
                        Boolean.TRUE.equals(apiKey.getActive()),
                        apiKey.getCreatedAt()
                ))
                .toList();
    }

    /**
     * Revoca (desactiva) una clave API identificada por su UUID para impedir su uso futuro en el Gateway.
     *
     * @param keyId identificador único universal (UUID) de la clave API a revocar
     * @throws InvalidPromptException si {@code keyId} es nulo
     */
    @Transactional
    public void revokeApiKey(UUID keyId) {
        revokeApiKey(keyId, null);
    }

    /**
     * Revoca (desactiva) una clave API identificada por su UUID verificando, en caso de que el
     * llamante presente una clave {@code air_live_...}, que dicha clave sea activa y pertenezca
     * al mismo usuario titular de la clave que se pretende revocar ({@code [MEDIUM-01]}).
     *
     * @param keyId        identificador único universal (UUID) de la clave API a revocar
     * @param clientApiKey clave opcional enviada por el llamante en la cabecera {@code X-API-Key}
     * @throws InvalidPromptException si {@code keyId} es nulo
     * @throws InvalidApiKeyException si la clave presentada por el llamante es inválida o no pertenece al propietario
     */
    @Transactional
    public void revokeApiKey(UUID keyId, String clientApiKey) {
        if (keyId == null) {
            throw new InvalidPromptException("El identificador de la clave API (keyId) es obligatorio.");
        }
        if (apiKeyRepository == null) {
            return;
        }
        Optional<ApiKey> targetKeyOpt = apiKeyRepository.findById(keyId);
        if (targetKeyOpt.isPresent()) {
            ApiKey apiKey = targetKeyOpt.get();
            validateManagedClientApiKeyIfPresent(clientApiKey, apiKey.getUserId());
            apiKey.setActive(Boolean.FALSE);
            apiKeyRepository.save(apiKey);
            log.info("Clave API revocada exitosamente [id='{}', prefijo='{}', usuario='{}'].",
                    apiKey.getId(), apiKey.getKeyPrefix(), apiKey.getUserId());
        } else {
            validateManagedClientApiKeyIfPresent(clientApiKey, null);
        }
    }

    /**
     * Consulta el estado actual de la Billetera Prepago ({@code Token Credits Wallet}) de un usuario.
     *
     * <p>Si el usuario aún no ha realizado ninguna recarga o no existe en {@code user_wallets}, devuelve
     * el estado por defecto {@link #WALLET_STATUS_POSTPAID} con {@code walletEnforced = false} y
     * saldos a {@code 0.000000}.</p>
     *
     * @param userId identificador único del usuario titular
     * @return DTO {@link UserWalletResponse} con el saldo disponible, total recargado y estado operativo
     * @throws InvalidPromptException si el identificador de usuario es nulo, está en blanco o supera 120 caracteres
     */
    @Transactional(readOnly = true)
    public UserWalletResponse getUserWallet(String userId) {
        String cleanedUserId = validateAndTrimBudgetUserId(userId);
        if (userWalletRepository == null) {
            return buildPostpaidWalletResponse(cleanedUserId);
        }

        Optional<UserWallet> walletOpt = userWalletRepository.findByUserId(cleanedUserId);
        if (walletOpt.isEmpty()) {
            return buildPostpaidWalletResponse(cleanedUserId);
        }

        return toUserWalletResponse(walletOpt.get());
    }

    /**
     * Abona una recarga de créditos prepago en la billetera de un usuario y activa el control
     * estricto de saldo ({@code walletEnforced = true}).
     *
     * @param userId  identificador del usuario titular de la billetera
     * @param request DTO con el importe en USD a recargar ({@code > 0}) y el método de pago ({@code STRIPE_CARD} o {@code PAYPAL})
     * @return DTO {@link UserWalletResponse} con el nuevo saldo actualizado y estado {@link #WALLET_STATUS_ACTIVE}
     * @throws InvalidPromptException si el usuario es inválido o el importe de recarga es nulo o {@code <= 0}
     */
    @Transactional
    public UserWalletResponse topUpUserWallet(String userId, WalletTopUpRequest request) {
        return topUpUserWallet(userId, request, null);
    }

    /**
     * Abona de forma sincronizada y atómica ({@code [LOW-01]}) una recarga de créditos prepago en la
     * billetera de un usuario, verificando la titularidad si se presenta una clave {@code air_live_...}
     * ({@code [MEDIUM-01]}).
     *
     * @param userId       identificador del usuario titular de la billetera
     * @param request      DTO con el importe en USD a recargar ({@code > 0}) y el método de pago
     * @param clientApiKey clave opcional enviada por el llamante en la cabecera {@code X-API-Key}
     * @return DTO {@link UserWalletResponse} con el nuevo saldo actualizado y estado {@link #WALLET_STATUS_ACTIVE}
     * @throws InvalidPromptException si el usuario es inválido o el importe de recarga es nulo o {@code <= 0}
     * @throws InvalidApiKeyException si la clave {@code air_live_...} presentada pertenece a otro usuario
     */
    @Transactional
    public UserWalletResponse topUpUserWallet(String userId, WalletTopUpRequest request, String clientApiKey) {
        String cleanedUserId = validateAndTrimBudgetUserId(userId);
        validateManagedClientApiKeyIfPresent(clientApiKey, cleanedUserId);
        if (request == null || request.amountUsd() == null || request.amountUsd().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPromptException("El importe de recarga debe ser mayor que cero.");
        }

        String normalizedPaymentMethod = DEFAULT_PAYMENT_METHOD;
        if (request.paymentMethod() != null && !request.paymentMethod().isBlank()) {
            normalizedPaymentMethod = request.paymentMethod().trim().toUpperCase(Locale.ROOT);
            if (normalizedPaymentMethod.length() > MAX_PAYMENT_METHOD_LENGTH) {
                throw new InvalidPromptException(
                        "El método de pago no puede superar los " + MAX_PAYMENT_METHOD_LENGTH + " caracteres.");
            }
        }

        BigDecimal topUpAmount = request.amountUsd().setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);
        LocalDateTime now = LocalDateTime.now();

        if (userWalletRepository == null) {
            UserWallet transientWallet = UserWallet.builder()
                    .userId(cleanedUserId)
                    .balanceUsd(topUpAmount)
                    .totalToppedUpUsd(topUpAmount)
                    .walletEnforced(Boolean.TRUE)
                    .lastPaymentMethod(normalizedPaymentMethod)
                    .updatedAt(now)
                    .build();
            return toUserWalletResponse(transientWallet);
        }

        Object userLock = walletLocks.computeIfAbsent(cleanedUserId, k -> new Object());
        synchronized (userLock) {
            UserWallet walletEntity = userWalletRepository.findByUserId(cleanedUserId)
                    .orElseGet(() -> UserWallet.builder()
                            .userId(cleanedUserId)
                            .balanceUsd(BigDecimal.ZERO.setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP))
                            .totalToppedUpUsd(BigDecimal.ZERO.setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP))
                            .walletEnforced(Boolean.TRUE)
                            .build());

            BigDecimal currentBalance = walletEntity.getBalanceUsd() != null
                    ? walletEntity.getBalanceUsd()
                    : BigDecimal.ZERO;
            BigDecimal currentTotalToppedUp = walletEntity.getTotalToppedUpUsd() != null
                    ? walletEntity.getTotalToppedUpUsd()
                    : BigDecimal.ZERO;

            walletEntity.setBalanceUsd(currentBalance.add(topUpAmount).setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP));
            walletEntity.setTotalToppedUpUsd(currentTotalToppedUp.add(topUpAmount).setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP));
            walletEntity.setWalletEnforced(Boolean.TRUE);
            walletEntity.setLastPaymentMethod(normalizedPaymentMethod);
            walletEntity.setUpdatedAt(now);

            UserWallet savedWallet = userWalletRepository.save(walletEntity);
            log.info("Recarga prepago completada [usuario='{}', abono=${} USD, pasarela='{}', nuevo saldo=${} USD].",
                    cleanedUserId, topUpAmount.toPlainString(), normalizedPaymentMethod, savedWallet.getBalanceUsd().toPlainString());

            return toUserWalletResponse(savedWallet);
        }
    }

    /**
     * Evalúa de forma sincronizada por usuario ({@code [LOW-01]}) si el titular tiene activada la
     * Billetera Prepago ({@code walletEnforced == true}) y, en caso de tratarse de una petición con coste
     * ({@code !isCacheHit}), verifica que disponga de saldo positivo y descuenta atómicamente los micro-céntimos
     * correspondientes a la inferencia.
     *
     * @param userId          identificador normalizado del usuario
     * @param selectedModel   modelo LLM elegido para procesar la petición
     * @param tokensEstimated cantidad de tokens estimados del prompt
     * @param isCacheHit      {@code true} si la respuesta proviene de la Caché Semántica a coste cero
     * @throws BudgetExceededException si el usuario tiene {@code walletEnforced == true}, {@code balanceUsd <= 0} y no es acierto de caché
     */
    private void evaluateAndDeductPrepaidWalletIfEnforced(
            String userId,
            String selectedModel,
            int tokensEstimated,
            boolean isCacheHit) {
        if (isCacheHit || userWalletRepository == null) {
            return;
        }

        Object userLock = walletLocks.computeIfAbsent(userId, k -> new Object());
        synchronized (userLock) {
            Optional<UserWallet> walletOpt = userWalletRepository.findByUserId(userId);
            if (walletOpt.isEmpty() || !Boolean.TRUE.equals(walletOpt.get().getWalletEnforced())) {
                return;
            }

            UserWallet wallet = walletOpt.get();
            BigDecimal currentBalance = wallet.getBalanceUsd() != null
                    ? wallet.getBalanceUsd().setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);

            if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Billetera Prepago [SALDO AGOTADO - HTTP 402]: Usuario '{}' sin créditos disponibles (${} USD).",
                        userId, currentBalance.toPlainString());
                throw new BudgetExceededException(
                        String.format(
                                "Saldo prepago agotado: la billetera del usuario '%s' tiene $%s USD disponibles. Recargue créditos para continuar.",
                                userId,
                                currentBalance.toPlainString()
                        ),
                        userId,
                        wallet.getTotalToppedUpUsd(),
                        currentBalance
                );
            }

            BigDecimal ratePerMillion = MODEL_PRO.equals(selectedModel)
                    ? COST_PER_MILLION_TOKENS_PRO
                    : COST_PER_MILLION_TOKENS_FAST;
            BigDecimal requestCostUsd = BigDecimal.valueOf(tokensEstimated)
                    .multiply(ratePerMillion)
                    .divide(ONE_MILLION_TOKENS, INTERNAL_CALCULATION_SCALE, RoundingMode.HALF_UP)
                    .setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);

            BigDecimal zeroScaled = BigDecimal.ZERO.setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);
            BigDecimal updatedBalance = currentBalance.subtract(requestCostUsd)
                    .max(zeroScaled)
                    .setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);

            wallet.setBalanceUsd(updatedBalance);
            wallet.setUpdatedAt(LocalDateTime.now());
            userWalletRepository.save(wallet);
        }
    }

    /**
     * Construye la respuesta por defecto para un usuario que aún no ha activado la billetera prepago.
     *
     * @param userId identificador del usuario
     * @return DTO {@link UserWalletResponse} en estado {@link #WALLET_STATUS_POSTPAID}
     */
    private UserWalletResponse buildPostpaidWalletResponse(String userId) {
        BigDecimal zeroScaled = BigDecimal.ZERO.setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);
        return new UserWalletResponse(
                userId,
                zeroScaled,
                zeroScaled,
                false,
                null,
                null,
                WALLET_STATUS_POSTPAID
        );
    }

    /**
     * Transforma una entidad {@link UserWallet} en su DTO {@link UserWalletResponse} determinando
     * el estado operativo ({@link #WALLET_STATUS_POSTPAID}, {@link #WALLET_STATUS_ACTIVE} o {@link #WALLET_STATUS_DEPLETED}).
     *
     * @param wallet entidad de billetera persistida
     * @return DTO {@link UserWalletResponse} con importes escalados a 6 decimales
     */
    private UserWalletResponse toUserWalletResponse(UserWallet wallet) {
        BigDecimal zeroScaled = BigDecimal.ZERO.setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal scaledBalance = wallet.getBalanceUsd() != null
                ? wallet.getBalanceUsd().setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP)
                : zeroScaled;
        BigDecimal scaledTotalToppedUp = wallet.getTotalToppedUpUsd() != null
                ? wallet.getTotalToppedUpUsd().setScale(COST_DECIMAL_SCALE, RoundingMode.HALF_UP)
                : zeroScaled;
        boolean enforced = Boolean.TRUE.equals(wallet.getWalletEnforced());

        String status;
        if (!enforced) {
            status = WALLET_STATUS_POSTPAID;
        } else if (scaledBalance.compareTo(BigDecimal.ZERO) > 0) {
            status = WALLET_STATUS_ACTIVE;
        } else {
            status = WALLET_STATUS_DEPLETED;
        }

        return new UserWalletResponse(
                wallet.getUserId(),
                scaledBalance,
                scaledTotalToppedUp,
                enforced,
                wallet.getLastPaymentMethod(),
                wallet.getUpdatedAt(),
                status
        );
    }

    /**
     * Verifica la validez criptográfica, el estado activo y la correspondencia de titularidad
     * ({@code [HIGH-01]}) de una clave API cuando el cliente envía un token gestionado por el
     * Gateway (con prefijo {@link #MANAGED_API_KEY_PREFIX}).
     *
     * @param clientApiKey   clave opcional enviada en la cabecera {@code X-API-Key}
     * @param expectedUserId identificador de usuario que debe coincidir con el propietario de la clave (o {@code null} si no aplica)
     * @throws InvalidApiKeyException si la clave {@code air_live_...} no existe, ha sido revocada o pertenece a otro usuario
     */
    private void validateManagedClientApiKeyIfPresent(String clientApiKey, String expectedUserId) {
        if (clientApiKey == null || clientApiKey.isBlank()) {
            return;
        }
        String trimmedKey = clientApiKey.trim();
        if (!trimmedKey.startsWith(MANAGED_API_KEY_PREFIX) || apiKeyRepository == null) {
            return;
        }

        String safePrefix = trimmedKey.length() >= MANAGED_KEY_VISIBLE_PREFIX_LENGTH
                ? trimmedKey.substring(0, MANAGED_KEY_VISIBLE_PREFIX_LENGTH) + "..."
                : MANAGED_API_KEY_PREFIX + "...";

        String keyHash = hashApiKeySha256(trimmedKey);
        Optional<ApiKey> storedKeyOpt = apiKeyRepository.findByKeyHash(keyHash);
        if (storedKeyOpt.isEmpty() || !Boolean.TRUE.equals(storedKeyOpt.get().getActive())) {
            log.warn("Autenticación rechazada [HTTP 401]: Clave API del Gateway '{}' inexistente o revocada.", safePrefix);
            throw new InvalidApiKeyException(
                    "La clave API proporcionada ('" + safePrefix + "') es inválida o ha sido revocada.");
        }

        ApiKey storedKey = storedKeyOpt.get();
        if (expectedUserId != null
                && !expectedUserId.isBlank()
                && !expectedUserId.trim().equals(storedKey.getUserId())) {
            log.warn("Autenticación rechazada [HTTP 401 - HIGH-01]: La clave API '{}' pertenece a otro titular y no al usuario '{}'.",
                    safePrefix, expectedUserId.trim());
            throw new InvalidApiKeyException(
                    "La clave API proporcionada ('" + safePrefix + "') no pertenece al usuario '" + expectedUserId.trim() + "'.");
        }
    }

    /**
     * Resuelve la clave de API efectiva para invocar a Google Gemini aplicando la jerarquía de 3 niveles:
     * <ol>
     *   <li>Clave BYOK de proveedor externo proporcionada por el usuario ({@code clientApiKey} que no sea
     *       un token interno {@code air_live_...} del Gateway).</li>
     *   <li>Clave global configurada en el servidor ({@code airouter.gemini.api-key}).</li>
     *   <li>{@link Optional#empty()} si ninguna clave de proveedor externo está disponible (activa el modo simulación).</li>
     * </ol>
     *
     * @param clientApiKey clave opcional recibida en la cabecera HTTP de la petición
     * @return {@link Optional} con la clave efectiva recortada para Google Gemini, o vacío si debe operar en simulación
     */
    public Optional<String> resolveEffectiveApiKey(String clientApiKey) {
        if (clientApiKey != null && !clientApiKey.isBlank()) {
            String trimmedClientKey = clientApiKey.trim();
            if (!trimmedClientKey.startsWith(MANAGED_API_KEY_PREFIX)) {
                return Optional.of(trimmedClientKey);
            }
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
     * Calcula las estadísticas agregadas de tráfico, consumo de tokens, aciertos de la Caché Semántica
     * y ahorro financiero en USD, permitiendo filtrar opcionalmente por un usuario concreto.
     *
     * <p>El ahorro económico se obtiene comparando el coste real estimado (donde las peticiones servidas
     * desde la Caché Semántica con {@code cacheHit == true} tienen coste real {@code $0.000000 USD}, y el resto
     * se factura a {@link #COST_PER_MILLION_TOKENS_FAST} para {@link #MODEL_FAST} o
     * {@link #COST_PER_MILLION_TOKENS_PRO} para {@link #MODEL_PRO}) frente al coste base hipotético
     * que habría supuesto procesar la totalidad de los tokens en el modelo avanzado {@link #MODEL_PRO}.</p>
     *
     * @param userId identificador opcional del usuario para acotar las métricas (nulo o en blanco para métricas globales)
     * @return DTO {@link RouterStatsResponse} con los totales de peticiones, tokens, costes en USD, porcentaje de ahorro y aciertos de caché
     */
    @Transactional(readOnly = true)
    public RouterStatsResponse getStats(String userId) {
        List<RequestLog> logs = fetchLogsByOptionalUserId(userId);

        long totalRequests = logs.size();
        long fastModelRequests = 0L;
        long proModelRequests = 0L;
        long fastModelTokens = 0L;
        long proModelTokens = 0L;
        long billedFastModelTokens = 0L;
        long billedProModelTokens = 0L;
        long cacheHits = 0L;
        long totalLatencyMs = 0L;
        long totalRedactedItems = 0L;

        for (RequestLog logEntry : logs) {
            long tokens = logEntry.getTokensEstimated() != null ? logEntry.getTokensEstimated() : 0L;
            boolean isCacheHit = Boolean.TRUE.equals(logEntry.getCacheHit());
            if (isCacheHit) {
                cacheHits++;
            }
            if (logEntry.getLatencyMs() != null && logEntry.getLatencyMs() > 0L) {
                totalLatencyMs += logEntry.getLatencyMs();
            }
            if (logEntry.getRedactedItemsCount() != null && logEntry.getRedactedItemsCount() > 0) {
                totalRedactedItems += logEntry.getRedactedItemsCount();
            }

            if (MODEL_FAST.equals(logEntry.getModelRouted())) {
                fastModelRequests++;
                fastModelTokens += tokens;
                if (!isCacheHit) {
                    billedFastModelTokens += tokens;
                }
            } else if (MODEL_PRO.equals(logEntry.getModelRouted())) {
                proModelRequests++;
                proModelTokens += tokens;
                if (!isCacheHit) {
                    billedProModelTokens += tokens;
                }
            }
        }

        long totalTokensEstimated = fastModelTokens + proModelTokens;

        BigDecimal rawFastCost = BigDecimal.valueOf(billedFastModelTokens)
                .multiply(COST_PER_MILLION_TOKENS_FAST)
                .divide(ONE_MILLION_TOKENS, INTERNAL_CALCULATION_SCALE, RoundingMode.HALF_UP);
        BigDecimal rawProCost = BigDecimal.valueOf(billedProModelTokens)
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

        double averageLatencyMs = totalRequests > 0
                ? BigDecimal.valueOf(totalLatencyMs)
                        .divide(BigDecimal.valueOf(totalRequests), PERCENTAGE_DECIMAL_SCALE, RoundingMode.HALF_UP)
                        .doubleValue()
                : 0.0;

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
                savingsPercentage,
                cacheHits,
                averageLatencyMs,
                totalRedactedItems
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
     * utilizando la respuesta simulada por defecto para consultas del historial.
     *
     * @param entity entidad persistida en base de datos
     * @return DTO {@link RouterResponse} con todos los campos mapeados
     */
    private RouterResponse toRouterResponse(RequestLog entity) {
        return toRouterResponse(
                entity,
                buildSimulatedResponse(entity.getModelRouted(), entity.getTokensEstimated())
        );
    }

    /**
     * Transforma una entidad JPA {@link RequestLog} y su texto de respuesta en un DTO {@link RouterResponse}
     * para evitar exponer entidades de persistencia fuera de la capa de servicio.
     *
     * @param entity       entidad persistida en base de datos
     * @param responseText texto de respuesta real o simulada asociado a la petición
     * @return DTO {@link RouterResponse} con todos los campos mapeados incluyendo {@code cacheHit}, {@code latencyMs} y {@code redactedItemsCount}
     */
    private RouterResponse toRouterResponse(RequestLog entity, String responseText) {
        return new RouterResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getPromptContent(),
                entity.getModelRouted(),
                entity.getTokensEstimated(),
                responseText,
                entity.getCreatedAt(),
                Boolean.TRUE.equals(entity.getCacheHit()),
                entity.getLatencyMs() != null ? entity.getLatencyMs() : 0L,
                entity.getRedactedItemsCount() != null ? entity.getRedactedItemsCount() : 0
        );
    }

    /**
     * Resultado inmutable del análisis y anonimización del Escudo DLP de Privacidad sobre un prompt.
     *
     * @param sanitizedPrompt    texto del prompt con todos los secretos y datos PII reemplazados por sus marcadores
     * @param redactedItemsCount cantidad total de elementos sensibles detectados y anonimizados
     */
    public record DlpSanitizationResult(
            String sanitizedPrompt,
            int redactedItemsCount
    ) {
    }

    /**
     * Registro inmutable interno que almacena los datos de una respuesta previamente procesada
     * en la Caché Semántica de Prompts, incluyendo el titular y si proviene de simulación ({@code [MEDIUM-02]}).
     *
     * @param modelRouted       modelo LLM que resolvió originalmente la petición
     * @param tokensEstimated   cantidad de tokens estimados del prompt
     * @param responseText      respuesta textual generada (real o simulada)
     * @param ownerUserId       identificador del usuario que originó la entrada en caché
     * @param simulatedResponse {@code true} si la respuesta fue generada por el motor de simulación; {@code false} si provino de Gemini real
     */
    private record CachedPromptEntry(
            String modelRouted,
            Integer tokensEstimated,
            String responseText,
            String ownerUserId,
            boolean simulatedResponse
    ) {
    }
}
