package com.tuproyecto.airouter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tuproyecto.airouter.repositories.RequestLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Pruebas de integración y auditoría QA de seguridad para {@link RateLimitingFilter} y {@link SecurityConfig}.
 *
 * <p>Valida el cumplimiento de cuotas por minuto (HTTP 429 + {@code Retry-After}), la no persistencia
 * de peticiones bloqueadas, el aislamiento y normalización de contadores por {@code X-API-Key} e IP,
 * el enmascaramiento de secretos en auditoría y las políticas restrictivas de CORS para {@code /api/v1/**}.</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "airouter.security.rate-limit.requests-per-minute=3",
        "airouter.security.cors.allowed-origins=http://localhost:5173"
})
class SecurityAndRateLimitingTest {

    /**
     * Contexto web de la aplicación para configurar {@link MockMvc}.
     */
    @Autowired
    private WebApplicationContext webApplicationContext;

    /**
     * Filtro de control de tasa de peticiones inyectado para registrar en {@link MockMvc} y limpiar su estado.
     */
    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    /**
     * Repositorio JPA de logs utilizado para verificar que las peticiones bloqueadas no se persisten.
     */
    @Autowired
    private RequestLogRepository requestLogRepository;

    /**
     * Cliente de pruebas MVC con el filtro {@link RateLimitingFilter} registrado.
     */
    private MockMvc mockMvc;

    /**
     * Cuerpo JSON válido reutilizado en las pruebas de control de cuota.
     */
    private static final String VALID_PAYLOAD = """
            {
                "userId": "sec-user",
                "prompt": "Hola router seguro"
            }
            """;

    /**
     * Limpia los buckets de rate limiting y la base de datos H2 antes de cada test.
     */
    @BeforeEach
    void setUp() {
        this.rateLimitingFilter.clearBuckets();
        this.requestLogRepository.deleteAll();
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .addFilters(this.rateLimitingFilter)
                .build();
    }

    /**
     * Verifica que el filtro permita exactamente {@code N} peticiones por minuto, bloquee la petición
     * {@code N+1} con estado HTTP 429, incluya la cabecera {@code Retry-After} con un valor entre 1 y 60
     * segundos, y evite que la petición bloqueada se guarde en la base de datos.
     *
     * @throws Exception si ocurre un error durante la ejecución de MockMvc
     */
    @Test
    @DisplayName("RateLimitingFilter debe permitir N peticiones, bloquear la N+1 con HTTP 429, Retry-After (1..60s) y evitar persistencia en BD")
    void shouldAllowUpToMaxRequestsAndBlockNextWith429AndRetryAfterWithoutPersisting() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/router/prompt")
                            .header("X-API-Key", "sk-secret-key-9999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_PAYLOAD))
                    .andExpect(status().isOk());
        }

        assertThat(requestLogRepository.count()).isEqualTo(3L);

        MvcResult blockedResult = mockMvc.perform(post("/api/v1/router/prompt")
                        .header("X-API-Key", "sk-secret-key-9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message", containsString("Límite de peticiones excedido")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andReturn();

        String retryAfterHeader = blockedResult.getResponse().getHeader("Retry-After");
        assertThat(retryAfterHeader).isNotNull();
        int retryAfterSeconds = Integer.parseInt(retryAfterHeader);
        assertThat(retryAfterSeconds).isBetween(1, 60);

        // La petición bloqueada por el filtro nunca debe llegar al servicio ni guardarse en H2
        assertThat(requestLogRepository.count()).isEqualTo(3L);
    }

    /**
     * Comprueba que el agotamiento de cuota de un cliente (por {@code X-API-Key} o por dirección IP)
     * no afecte a otros clientes con claves o direcciones IP diferentes.
     *
     * @throws Exception si ocurre un error durante la ejecución de MockMvc
     */
    @Test
    @DisplayName("RateLimitingFilter debe aislar contadores entre distintas X-API-Key y distintas direcciones IP")
    void shouldIsolateBucketsByApiKeyAndIpAddress() throws Exception {
        // Agotar cuota para API Key A
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/router/prompt")
                            .header("X-API-Key", "sk-client-A-12345")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_PAYLOAD))
                    .andExpect(status().isOk());
        }

        // API Key A bloqueada
        mockMvc.perform(post("/api/v1/router/prompt")
                        .header("X-API-Key", "sk-client-A-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isTooManyRequests());

        // API Key B sigue teniendo cuota disponible
        mockMvc.perform(post("/api/v1/router/prompt")
                        .header("X-API-Key", "sk-client-B-67890")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk());

        // Cliente sin API Key identificado por IP 10.0.0.1 sigue teniendo cuota disponible
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/router/prompt")
                            .with(request -> {
                                request.setRemoteAddr("10.0.0.1");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_PAYLOAD))
                    .andExpect(status().isOk());
        }

        // Cuarta petición desde IP 10.0.0.1 bloqueada
        mockMvc.perform(post("/api/v1/router/prompt")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isTooManyRequests());

        // Otra IP 10.0.0.2 no se ve afectada
        mockMvc.perform(post("/api/v1/router/prompt")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.2");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk());
    }

    /**
     * Verifica que si se envía una cabecera {@code X-API-Key} vacía o con solo espacios/tabulaciones,
     * el filtro la descarte y aplique el límite de cuota por dirección IP.
     *
     * @throws Exception si ocurre un error durante la ejecución de MockMvc
     */
    @Test
    @DisplayName("RateLimitingFilter debe ignorar X-API-Key en blanco o con solo espacios y aplicar el límite por dirección IP")
    void shouldFallbackToIpBucketWhenApiKeyHeaderIsBlank() throws Exception {
        // Dos peticiones con X-API-Key en blanco y una sin cabecera desde la misma IP 172.16.0.10
        mockMvc.perform(post("/api/v1/router/prompt")
                        .with(req -> {
                            req.setRemoteAddr("172.16.0.10");
                            return req;
                        })
                        .header("X-API-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/router/prompt")
                        .with(req -> {
                            req.setRemoteAddr("172.16.0.10");
                            return req;
                        })
                        .header("X-API-Key", "\t  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/router/prompt")
                        .with(req -> {
                            req.setRemoteAddr("172.16.0.10");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk());

        // La cuarta petición desde la misma IP (incluso enviando X-API-Key en blanco) debe ser bloqueada con 429
        mockMvc.perform(post("/api/v1/router/prompt")
                        .with(req -> {
                            req.setRemoteAddr("172.16.0.10");
                            return req;
                        })
                        .header("X-API-Key", "     ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * Verifica que el filtro normalice mediante {@code trim()} el valor de {@code X-API-Key} para impedir
     * que un cliente multiplique su cuota añadiendo espacios al principio o al final de la clave.
     *
     * @throws Exception si ocurre un error durante la ejecución de MockMvc
     */
    @Test
    @DisplayName("RateLimitingFilter debe normalizar con trim() la cabecera X-API-Key para impedir evasión de cuota con espacios laterales")
    void shouldNormalizeApiKeyWithTrimToPreventQuotaBypass() throws Exception {
        mockMvc.perform(post("/api/v1/router/prompt")
                        .header("X-API-Key", "sk-trim-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/router/prompt")
                        .header("X-API-Key", "   sk-trim-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/router/prompt")
                        .header("X-API-Key", "sk-trim-key   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk());

        // La cuarta petición con espacios en ambos lados comparte el mismo bucket y debe ser bloqueada con 429
        mockMvc.perform(post("/api/v1/router/prompt")
                        .header("X-API-Key", "  sk-trim-key  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * Comprueba que las consultas {@code GET /api/v1/router/logs} y las peticiones preflight {@code OPTIONS}
     * estén excluidas del filtro de rate limiting y no consuman cuota.
     *
     * @throws Exception si ocurre un error durante la ejecución de MockMvc
     */
    @Test
    @DisplayName("RateLimitingFilter no debe consumir cuota en GET /api/v1/router/logs ni en preflight OPTIONS")
    void shouldNotFilterGetLogsOrOptionsPreflight() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/router/logs")
                            .header("X-API-Key", "sk-unfiltered-key"))
                    .andExpect(status().isOk());

            mockMvc.perform(options("/api/v1/router/prompt")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Verifica que {@link RateLimitingFilter#maskClientKeyForAudit(String)} oculte siempre los caracteres
     * sensibles de cualquier {@code X-API-Key} antes de escribir en los logs de auditoría.
     */
    @Test
    @DisplayName("RateLimitingFilter.maskClientKeyForAudit nunca debe exponer el valor completo de X-API-Key")
    void shouldMaskApiKeyForAuditLogs() {
        String maskedLongKey = RateLimitingFilter.maskClientKeyForAudit("apikey:sk-live-9876543210abcdef");
        assertThat(maskedLongKey)
                .isEqualTo("apikey:sk-***")
                .doesNotContain("9876543210abcdef");

        String maskedShortKey = RateLimitingFilter.maskClientKeyForAudit("apikey:abc");
        assertThat(maskedShortKey)
                .isEqualTo("apikey:***")
                .doesNotContain("abc");

        String ipKey = RateLimitingFilter.maskClientKeyForAudit("ip:192.168.1.50");
        assertThat(ipKey).isEqualTo("ip:192.168.1.50");

        String nullKey = RateLimitingFilter.maskClientKeyForAudit(null);
        assertThat(nullKey).isEqualTo("unknown");
    }

    /**
     * Comprueba que {@link SecurityConfig} autorice peticiones CORS desde el origen permitido
     * ({@code http://localhost:5173}) exponiendo {@code Retry-After} y rechace orígenes externos con HTTP 403.
     *
     * @throws Exception si ocurre un error durante la ejecución de MockMvc
     */
    @Test
    @DisplayName("SecurityConfig debe autorizar CORS para http://localhost:5173 exponiendo Retry-After y rechazar orígenes no autorizados")
    void shouldAllowConfiguredOriginAndRejectUnauthorizedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/router/prompt")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, X-API-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Retry-After")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/v1/router/prompt")
                        .header(HttpHeaders.ORIGIN, "http://evil-site.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    /**
     * Verifica que {@link SecurityConfig} rechace con HTTP 403 Forbidden cualquier petición preflight CORS
     * que solicite métodos HTTP no permitidos ({@code DELETE}, {@code PUT}) o cabeceras no autorizadas.
     *
     * @throws Exception si ocurre un error durante la ejecución de MockMvc
     */
    @Test
    @DisplayName("SecurityConfig debe rechazar preflights CORS que soliciten métodos HTTP destructivos no permitidos (DELETE, PUT) o cabeceras no autorizadas")
    void shouldRejectDisallowedCorsMethodsAndHeadersInPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/router/prompt")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isForbidden());

        mockMvc.perform(options("/api/v1/router/prompt")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isForbidden());

        mockMvc.perform(options("/api/v1/router/prompt")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Forbidden-Custom-Header"))
                .andExpect(status().isForbidden());
    }
}
