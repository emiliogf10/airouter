package com.tuproyecto.airouter.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro de seguridad para el control de tasa de peticiones (Rate Limiting) en memoria.
 *
 * <p>Protege el endpoint de enrutamiento de prompts ({@code POST /api/v1/router/prompt}) contra
 * abusos de cuota o ataques de denegación de servicio que puedan disparar los costes de facturación
 * de proveedores LLM externos.</p>
 *
 * <p>Identifica a cada cliente de forma prioritaria mediante la cabecera {@code X-API-Key} y, en su
 * defecto, por la dirección IP de origen ({@code RemoteAddr}). Mantiene contadores concurrentes en
 * ventanas fijas de 60 segundos y devuelve un estado HTTP 429 ({@code Too Many Requests}) junto con
 * la cabecera estándar {@code Retry-After} cuando se supera el umbral configurado.</p>
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    /**
     * Nombre de la cabecera HTTP utilizada para identificar clientes mediante clave de API.
     */
    public static final String API_KEY_HEADER = "X-API-Key";

    /**
     * Nombre de la cabecera HTTP estándar que informa los segundos de espera antes de reintentar.
     */
    public static final String RETRY_AFTER_HEADER = "Retry-After";

    /**
     * Ruta del endpoint protegido por el control de cuota.
     */
    private static final String PROTECTED_PATH = "/api/v1/router/prompt";

    /**
     * Duración de la ventana de tiempo de control de cuota en milisegundos (60 segundos).
     */
    private static final long WINDOW_DURATION_MS = 60_000L;

    /**
     * Número máximo de peticiones permitidas por minuto por cada cliente.
     */
    @Value("${airouter.security.rate-limit.requests-per-minute:20}")
    private int maxRequestsPerMinute;

    /**
     * Almacén concurrente en memoria que asocia el identificador del cliente con su ventana de consumo.
     */
    private final ConcurrentHashMap<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Determina si la petición entrante debe excluirse del filtro de control de cuota.
     *
     * <p>Solo se aplica el límite sobre peticiones {@code POST} dirigidas a {@code /api/v1/router/prompt}.
     * Las peticiones {@code OPTIONS} (preflight de CORS) y otros endpoints quedan excluidos para no
     * consumir cuota ni interferir en la negociación CORS.</p>
     *
     * @param request petición HTTP entrante
     * @return {@code true} si la petición no debe ser filtrada; {@code false} si debe evaluarse su cuota
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        String servletPath = request.getServletPath();
        return !PROTECTED_PATH.equals(uri) && !PROTECTED_PATH.equals(servletPath);
    }

    /**
     * Evalúa el consumo de cuota del cliente dentro de la ventana actual de 60 segundos.
     *
     * <p>Si el cliente supera el límite configurado en {@code maxRequestsPerMinute}, se registra
     * una alerta de auditoría enmascarando credenciales sensibles, se interrumpe la cadena de filtros
     * y se responde con estado HTTP 429, cabecera {@code Retry-After} y cuerpo JSON homologado con
     * {@code ErrorResponse}.</p>
     *
     * @param request     petición HTTP entrante
     * @param response    respuesta HTTP saliente
     * @param filterChain cadena de filtros de Spring
     * @throws ServletException si ocurre un error en el procesamiento del servlet
     * @throws IOException      si ocurre un error de entrada/salida al escribir la respuesta
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String clientKey = resolveClientKey(request);
        long now = System.currentTimeMillis();

        RateLimitEvaluation evaluation = evaluateRequest(clientKey, now);

        if (!evaluation.allowed()) {
            String maskedClient = maskClientKeyForAudit(clientKey);
            log.warn("Alerta de seguridad [Rate Limit]: Cuota excedida ({} peticiones/min) para el cliente [{}] en {}. Reintentar en {} s.",
                    maxRequestsPerMinute, maskedClient, request.getRequestURI(), evaluation.retryAfterSeconds());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(RETRY_AFTER_HEADER, String.valueOf(evaluation.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            String jsonError = String.format(
                    "{\"message\":\"Límite de peticiones excedido. Por favor, inténtelo de nuevo más tarde.\",\"timestamp\":\"%s\"}",
                    LocalDateTime.now().toString()
            );
            response.getWriter().write(jsonError);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resuelve la clave identificadora del cliente priorizando la cabecera {@code X-API-Key}
     * sobre la dirección IP de origen.
     *
     * @param request petición HTTP entrante
     * @return identificador interno del bucket ({@code apikey:<valor>} o {@code ip:<dirección>})
     */
    private String resolveClientKey(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return "apikey:" + apiKey.trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    /**
     * Enmascara el identificador del cliente para su registro seguro en los logs de auditoría,
     * garantizando que nunca se exponga el valor completo de una {@code X-API-Key}.
     *
     * @param clientKey identificador interno del cliente
     * @return representación enmascarada segura para logs (máximo los primeros 3 caracteres seguidos de {@code ***})
     */
    public static String maskClientKeyForAudit(String clientKey) {
        if (clientKey == null) {
            return "unknown";
        }
        if (clientKey.startsWith("apikey:")) {
            String rawKey = clientKey.substring("apikey:".length());
            if (rawKey.length() <= 3) {
                return "apikey:***";
            }
            return "apikey:" + rawKey.substring(0, 3) + "***";
        }
        return clientKey;
    }

    /**
     * Actualiza de forma atómica el contador de peticiones del cliente para la ventana en curso
     * y determina si la petición debe ser permitida o rechazada.
     *
     * @param clientKey identificador del cliente
     * @param now       marca de tiempo actual en milisegundos
     * @return resultado de la evaluación con el indicador de permiso y los segundos de espera restantes
     */
    private RateLimitEvaluation evaluateRequest(String clientKey, long now) {
        RateLimitBucket bucket = buckets.compute(clientKey, (key, existing) -> {
            if (existing == null || (now - existing.windowStartMillis) >= WINDOW_DURATION_MS) {
                return new RateLimitBucket(now, new AtomicInteger(1));
            }
            existing.requestCount.incrementAndGet();
            return existing;
        });

        int currentCount = bucket.requestCount.get();
        if (currentCount > maxRequestsPerMinute) {
            long remainingMillis = (bucket.windowStartMillis + WINDOW_DURATION_MS) - now;
            long retryAfterSeconds = Math.max(1L, (long) Math.ceil(remainingMillis / 1000.0));
            return new RateLimitEvaluation(false, retryAfterSeconds);
        }

        return new RateLimitEvaluation(true, 0L);
    }

    /**
     * Limpia todos los contadores de cuota almacenados en memoria.
     *
     * <p>Método de utilidad diseñado para restablecer el estado entre pruebas automatizadas.</p>
     */
    public void clearBuckets() {
        buckets.clear();
    }

    /**
     * Alias de {@link #clearBuckets()} para compatibilidad con suites de pruebas que invoquen {@code resetBuckets()}.
     */
    public void resetBuckets() {
        clearBuckets();
    }

    /**
     * Alias de {@link #clearBuckets()} para compatibilidad con suites de pruebas que invoquen {@code reset()}.
     */
    public void reset() {
        clearBuckets();
    }

    /**
     * Estructura interna que representa la ventana de tiempo activa y el contador atómico de un cliente.
     */
    private static final class RateLimitBucket {

        /**
         * Marca de tiempo en milisegundos en la que comenzó la ventana actual de 60 segundos.
         */
        private final long windowStartMillis;

        /**
         * Contador atómico de peticiones consumidas por el cliente dentro de la ventana actual.
         */
        private final AtomicInteger requestCount;

        /**
         * Crea una nueva ventana de control de cuota para un cliente.
         *
         * @param windowStartMillis instante de inicio de la ventana en milisegundos
         * @param requestCount      contador atómico inicializado para la ventana
         */
        private RateLimitBucket(long windowStartMillis, AtomicInteger requestCount) {
            this.windowStartMillis = windowStartMillis;
            this.requestCount = requestCount;
        }
    }

    /**
     * Resultado inmutable de la evaluación de cuota para una petición individual.
     *
     * @param allowed            indica si la petición se encuentra dentro del límite permitido
     * @param retryAfterSeconds  segundos restantes hasta el reinicio de la ventana en caso de bloqueo
     */
    private record RateLimitEvaluation(boolean allowed, long retryAfterSeconds) {
    }
}
