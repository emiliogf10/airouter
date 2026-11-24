package com.tuproyecto.airouter.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tuproyecto.airouter.models.entities.ApiKey;
import com.tuproyecto.airouter.models.entities.RequestLog;
import com.tuproyecto.airouter.models.entities.UserBudget;
import com.tuproyecto.airouter.models.entities.UserWallet;
import com.tuproyecto.airouter.services.RouterService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pruebas de integración para {@link RequestLogRepository}, {@link UserBudgetRepository} y las entidades
 * {@link RequestLog} y {@link UserBudget}, verificando las migraciones Flyway {@code V1} y {@code V2}
 * en H2, generación de UUID, callbacks {@code @PrePersist}/{@code @PreUpdate}, precisión decimal de
 * presupuestos FinOps y contrato manual de {@code equals}/{@code hashCode}.
 */
@SpringBootTest
@Transactional
class RequestLogRepositoryTest {

    /**
     * Repositorio JPA de logs bajo prueba conectado a la base de datos H2 en memoria.
     */
    @Autowired
    private RequestLogRepository requestLogRepository;

    /**
     * Repositorio JPA de presupuestos por usuario (migración Flyway V2) bajo prueba.
     */
    @Autowired
    private UserBudgetRepository userBudgetRepository;

    /**
     * Repositorio JPA de claves API con hash SHA-256 (migración Flyway V4) bajo prueba.
     */
    @Autowired
    private ApiKeyRepository apiKeyRepository;

    /**
     * Repositorio JPA de billeteras prepago por usuario (migración Flyway V5) bajo prueba.
     */
    @Autowired
    private UserWalletRepository userWalletRepository;

    /**
     * Limpia todos los registros de las tablas {@code request_logs}, {@code user_budgets}, {@code api_keys} y {@code user_wallets} antes de cada prueba.
     */
    @BeforeEach
    void setUp() {
        requestLogRepository.deleteAll();
        userBudgetRepository.deleteAll();
        apiKeyRepository.deleteAll();
        userWalletRepository.deleteAll();
    }

    /**
     * Comprueba que al persistir una entidad {@link RequestLog} sin {@code id} ni {@code createdAt},
     * JPA genere automáticamente el {@code UUID} y el callback {@code @PrePersist} asigne la marca temporal.
     */
    @Test
    @DisplayName("Debe generar UUID automáticamente y asignar createdAt mediante @PrePersist cuando es nulo")
    void shouldGenerateUuidAndPopulateCreatedAtOnPrePersist() {
        RequestLog entity = RequestLog.builder()
                .userId("db-user-01")
                .promptContent("Prompt de prueba de persistencia")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(8)
                .createdAt(null)
                .build();

        RequestLog saved = requestLogRepository.saveAndFlush(entity);

        assertNotNull(saved.getId(), "El ID UUID debe generarse automáticamente al persistir");
        assertNotNull(saved.getCreatedAt(), "El campo createdAt debe poblarse mediante @PrePersist");
        assertEquals("db-user-01", saved.getUserId());
        assertEquals("Prompt de prueba de persistencia", saved.getPromptContent());
        assertEquals(RouterService.MODEL_FAST, saved.getModelRouted());
        assertEquals(8, saved.getTokensEstimated());
    }

    /**
     * Verifica que los métodos derivados {@link RequestLogRepository#findAllByOrderByCreatedAtDesc()}
     * y {@link RequestLogRepository#findByUserIdOrderByCreatedAtDesc(String)} ordenen los resultados
     * de más reciente a más antiguo.
     */
    @Test
    @DisplayName("Debe recuperar los registros ordenados por createdAt descendente de forma global y por usuario")
    void shouldFindLogsOrderedByCreatedAtDescGloballyAndByUserId() {
        LocalDateTime baseTime = LocalDateTime.of(2026, 10, 20, 10, 0, 0);

        RequestLog oldest = RequestLog.builder()
                .userId("user-a")
                .promptContent("Primero")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(2)
                .createdAt(baseTime)
                .build();

        RequestLog middle = RequestLog.builder()
                .userId("user-b")
                .promptContent("Segundo")
                .modelRouted(RouterService.MODEL_PRO)
                .tokensEstimated(60)
                .createdAt(baseTime.plusMinutes(5))
                .build();

        RequestLog newest = RequestLog.builder()
                .userId("user-a")
                .promptContent("Tercero")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(2)
                .createdAt(baseTime.plusMinutes(10))
                .build();

        requestLogRepository.saveAllAndFlush(List.of(oldest, middle, newest));

        List<RequestLog> allOrdered = requestLogRepository.findAllByOrderByCreatedAtDesc();
        assertEquals(3, allOrdered.size());
        assertEquals("Tercero", allOrdered.get(0).getPromptContent());
        assertEquals("Segundo", allOrdered.get(1).getPromptContent());
        assertEquals("Primero", allOrdered.get(2).getPromptContent());

        List<RequestLog> userAOrdered = requestLogRepository.findByUserIdOrderByCreatedAtDesc("user-a");
        assertEquals(2, userAOrdered.size());
        assertEquals("Tercero", userAOrdered.get(0).getPromptContent());
        assertEquals("Primero", userAOrdered.get(1).getPromptContent());

        List<RequestLog> nonExistentUser = requestLogRepository.findByUserIdOrderByCreatedAtDesc("user-inexistente");
        assertTrue(nonExistentUser.isEmpty());
    }

    /**
     * Comprueba la implementación manual de {@link RequestLog#equals(Object)} y {@link RequestLog#hashCode()}
     * basada únicamente en la clave primaria {@code id}, garantizando que entidades transitorias sin {@code id}
     * no se consideren iguales entre sí.
     */
    @Test
    @DisplayName("Debe cumplir el contrato de equals y hashCode basado exclusivamente en el id de la entidad")
    void shouldFulfillEqualsAndHashCodeContractBasedOnId() {
        UUID sharedId = UUID.randomUUID();
        UUID differentId = UUID.randomUUID();

        RequestLog log1 = RequestLog.builder()
                .id(sharedId)
                .userId("user-1")
                .promptContent("Prompt A")
                .tokensEstimated(5)
                .build();

        RequestLog log2 = RequestLog.builder()
                .id(sharedId)
                .userId("user-2")
                .promptContent("Prompt B distinto")
                .tokensEstimated(99)
                .build();

        RequestLog logWithDifferentId = RequestLog.builder()
                .id(differentId)
                .userId("user-1")
                .promptContent("Prompt A")
                .tokensEstimated(5)
                .build();

        RequestLog transientLog1 = RequestLog.builder()
                .id(null)
                .userId("user-1")
                .promptContent("Prompt A")
                .tokensEstimated(5)
                .build();

        RequestLog transientLog2 = RequestLog.builder()
                .id(null)
                .userId("user-1")
                .promptContent("Prompt A")
                .tokensEstimated(5)
                .build();

        // Reflexividad e igualdad por ID idéntico
        assertEquals(log1, log1);
        assertEquals(log1, log2);
        assertEquals(log1.hashCode(), log2.hashCode());

        // Desigualdad con ID distinto, nulo o tipo diferente
        assertNotEquals(log1, logWithDifferentId);
        assertFalse(log1.equals(null));
        assertFalse(log1.equals("otro-tipo-de-objeto"));

        // Dos entidades transitorias (id == null) distintas en memoria no deben considerarse iguales
        assertNotEquals(transientLog1, transientLog2);
        assertEquals(0, transientLog1.hashCode());
    }

    /**
     * Verifica la migración Flyway {@code V2__create_user_budgets_table.sql} y las operaciones de
     * {@link UserBudgetRepository}: guardado con precisión de 6 decimales en {@code maxBudgetUsd},
     * búsqueda mediante {@link UserBudgetRepository#findByUserId(String)}, eliminación con
     * {@link UserBudgetRepository#deleteByUserId(String)} y contrato {@code equals}/{@code hashCode}.
     */
    @Test
    @DisplayName("Migración V2 (user_budgets): Debe persistir, buscar por userId y eliminar UserBudget con precisión de 6 decimales")
    void shouldSaveFindAndDeleteUserBudgetWithSixDecimalScale() {
        UserBudget budget = UserBudget.builder()
                .userId("finops-user-01")
                .maxBudgetUsd(new BigDecimal("0.001250"))
                .autoDowngradeEnabled(null)
                .build();

        UserBudget saved = userBudgetRepository.saveAndFlush(budget);

        assertNotNull(saved.getId(), "El UUID de UserBudget debe generarse automáticamente");
        assertNotNull(saved.getUpdatedAt(), "El campo updatedAt debe poblarse mediante @PrePersist");
        assertTrue(saved.getAutoDowngradeEnabled(), "autoDowngradeEnabled debe inicializarse a true por defecto");
        assertEquals(new BigDecimal("0.001250"), saved.getMaxBudgetUsd());

        Optional<UserBudget> foundOpt = userBudgetRepository.findByUserId("finops-user-01");
        assertTrue(foundOpt.isPresent());
        assertEquals(0, new BigDecimal("0.001250").compareTo(foundOpt.get().getMaxBudgetUsd()));
        assertEquals(saved, foundOpt.get());
        assertEquals(saved.hashCode(), foundOpt.get().hashCode());

        userBudgetRepository.deleteByUserId("finops-user-01");
        userBudgetRepository.flush();

        assertTrue(userBudgetRepository.findByUserId("finops-user-01").isEmpty());
    }

    /**
     * Verifica la migración Flyway {@code V3__add_cache_hit_to_request_logs.sql}:
     * comprueba que {@link RequestLog} persiste correctamente {@code cacheHit = false} por defecto
     * (incluso cuando se envía {@code null}) y {@code cacheHit = true} cuando la petición fue
     * servida desde la Caché Semántica de Coste Cero.
     */
    @Test
    @DisplayName("Migración V3 (cache_hit): Debe persistir cacheHit=false por defecto y cacheHit=true en aciertos de caché")
    void shouldPersistCacheHitDefaultFalseAndExplicitTrueInRequestLogs() {
        RequestLog defaultMissLog = RequestLog.builder()
                .userId("cache-user-01")
                .promptContent("¿Cuál es la capital de España?")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(8)
                .cacheHit(null)
                .build();

        RequestLog explicitHitLog = RequestLog.builder()
                .userId("cache-user-02")
                .promptContent("  ¿cuál es la   capital de españa? ")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(8)
                .cacheHit(Boolean.TRUE)
                .build();

        RequestLog savedMiss = requestLogRepository.saveAndFlush(defaultMissLog);
        RequestLog savedHit = requestLogRepository.saveAndFlush(explicitHitLog);

        assertFalse(savedMiss.getCacheHit(), "El campo cacheHit debe persistirse como false cuando es nulo");
        assertTrue(savedHit.getCacheHit(), "El campo cacheHit debe persistirse como true en un acierto de caché");
    }

    /**
     * Verifica la migración Flyway {@code V4__create_api_keys_table.sql} y las operaciones de
     * {@link ApiKeyRepository}: guardado de una entidad {@link ApiKey} con hash SHA-256 de 64 caracteres,
     * búsqueda mediante {@link ApiKeyRepository#findByKeyHash(String)}, listado por usuario con
     * {@link ApiKeyRepository#findByUserIdOrderByCreatedAtDesc(String)} y contrato {@code equals}/{@code hashCode}.
     */
    @Test
    @DisplayName("Migración V4 (api_keys): Debe persistir ApiKey, buscar por keyHash SHA-256 y listar por userId")
    void shouldSaveFindByKeyHashAndListApiKeysByUserId() {
        String sampleSha256Hash = "a1b2c3d4e5f60123456789abcdef0123456789abcdef0123456789abcdef0123";

        ApiKey apiKey = ApiKey.builder()
                .userId("sec-user-01")
                .keyName("Clave Backend Producción")
                .keyPrefix("air_live_9f8e...")
                .keyHash(sampleSha256Hash)
                .active(null)
                .createdAt(null)
                .build();

        ApiKey saved = apiKeyRepository.saveAndFlush(apiKey);

        assertNotNull(saved.getId(), "El UUID de ApiKey debe generarse automáticamente");
        assertNotNull(saved.getCreatedAt(), "El campo createdAt debe poblarse mediante @PrePersist");
        assertTrue(saved.getActive(), "El campo active debe inicializarse a true por defecto");

        Optional<ApiKey> foundByHash = apiKeyRepository.findByKeyHash(sampleSha256Hash);
        assertTrue(foundByHash.isPresent());
        assertEquals("sec-user-01", foundByHash.get().getUserId());
        assertEquals("Clave Backend Producción", foundByHash.get().getKeyName());
        assertEquals("air_live_9f8e...", foundByHash.get().getKeyPrefix());
        assertEquals(saved, foundByHash.get());
        assertEquals(saved.hashCode(), foundByHash.get().hashCode());

        List<ApiKey> userKeys = apiKeyRepository.findByUserIdOrderByCreatedAtDesc("sec-user-01");
        assertEquals(1, userKeys.size());
        assertEquals(saved.getId(), userKeys.get(0).getId());
    }

    /**
     * Verifica la migración Flyway {@code V5__create_user_wallets_table.sql} y las operaciones de
     * {@link UserWalletRepository}: guardado de una entidad {@link UserWallet} validando la precisión
     * de 6 decimales en {@code balanceUsd} y {@code totalToppedUpUsd}, búsqueda mediante
     * {@link UserWalletRepository#findByUserId(String)} y contrato {@code equals}/{@code hashCode}.
     */
    @Test
    @DisplayName("Migración V5 (user_wallets): Debe persistir y buscar por userId un UserWallet con precisión de 6 decimales")
    void shouldSaveAndFindUserWalletByUserIdWithSixDecimalScale() {
        UserWallet wallet = UserWallet.builder()
                .userId("wallet-user-01")
                .balanceUsd(new BigDecimal("5.123456"))
                .totalToppedUpUsd(new BigDecimal("10.654321"))
                .walletEnforced(Boolean.TRUE)
                .lastPaymentMethod("STRIPE_CARD")
                .updatedAt(null)
                .build();

        UserWallet saved = userWalletRepository.saveAndFlush(wallet);

        assertNotNull(saved.getId(), "El UUID de UserWallet debe generarse automáticamente");
        assertNotNull(saved.getUpdatedAt(), "El campo updatedAt debe poblarse mediante @PrePersist");
        assertTrue(saved.getWalletEnforced());
        assertEquals(0, new BigDecimal("5.123456").compareTo(saved.getBalanceUsd()));
        assertEquals(0, new BigDecimal("10.654321").compareTo(saved.getTotalToppedUpUsd()));

        Optional<UserWallet> foundOpt = userWalletRepository.findByUserId("wallet-user-01");
        assertTrue(foundOpt.isPresent());
        assertEquals(0, new BigDecimal("5.123456").compareTo(foundOpt.get().getBalanceUsd()));
        assertEquals(0, new BigDecimal("10.654321").compareTo(foundOpt.get().getTotalToppedUpUsd()));
        assertEquals("STRIPE_CARD", foundOpt.get().getLastPaymentMethod());
        assertEquals(saved, foundOpt.get());
        assertEquals(saved.hashCode(), foundOpt.get().hashCode());
    }

    /**
     * Verifica la migración Flyway {@code V6__add_latency_and_dlp_columns_to_request_logs.sql}:
     * comprueba la persistencia y recuperación en H2 de las columnas {@code latencyMs} y
     * {@code redactedItemsCount}, así como la inicialización a sus valores por defecto ({@code 0L} y {@code 0})
     * mediante {@code @PrePersist} cuando se reciben nulos.
     */
    @Test
    @DisplayName("Migración V6 (latency_ms y redacted_items_count): Debe persistir métricas de latencia y DLP o sus valores por defecto (0L y 0)")
    void shouldPersistLatencyMsAndRedactedItemsCountWithDefaultsAndExplicitValues() {
        RequestLog defaultTelemetryLog = RequestLog.builder()
                .userId("dlp-user-01")
                .promptContent("Prompt limpio sin PII")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(6)
                .latencyMs(null)
                .redactedItemsCount(null)
                .build();

        RequestLog explicitTelemetryLog = RequestLog.builder()
                .userId("dlp-user-02")
                .promptContent("Contacto: [EMAIL_REDACTED], clave [SECRET_REDACTED] y DNI [ID_REDACTED]")
                .modelRouted(RouterService.MODEL_PRO)
                .tokensEstimated(18)
                .latencyMs(42L)
                .redactedItemsCount(3)
                .build();

        RequestLog savedDefault = requestLogRepository.saveAndFlush(defaultTelemetryLog);
        RequestLog savedExplicit = requestLogRepository.saveAndFlush(explicitTelemetryLog);

        assertEquals(0L, savedDefault.getLatencyMs(), "latencyMs debe inicializarse a 0L por defecto cuando es nulo");
        assertEquals(0, savedDefault.getRedactedItemsCount(), "redactedItemsCount debe inicializarse a 0 por defecto cuando es nulo");

        assertEquals(42L, savedExplicit.getLatencyMs());
        assertEquals(3, savedExplicit.getRedactedItemsCount());
    }

    /**
     * Verifica la migración Flyway {@code V7__add_prompt_compression_column_to_request_logs.sql}:
     * comprueba la persistencia y recuperación en H2 de la columna {@code tokensSavedCompression}
     * tanto con un valor explícito (ej. {@code 14}) como con su valor por defecto ({@code 0})
     * asignado mediante {@code @PrePersist} cuando se recibe nulo.
     */
    @Test
    @DisplayName("Migración V7 (tokens_saved_compression): Debe persistir tokensSavedCompression explícito (14) o su valor por defecto (0)")
    void shouldPersistTokensSavedCompressionWithDefaultZeroAndExplicitValue() {
        RequestLog defaultCompressionLog = RequestLog.builder()
                .userId("comp-user-01")
                .promptContent("Prompt ya compacto")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(5)
                .tokensSavedCompression(null)
                .build();

        RequestLog explicitCompressionLog = RequestLog.builder()
                .userId("comp-user-02")
                .promptContent("Prompt optimizado tras eliminar espacios redundantes")
                .modelRouted(RouterService.MODEL_FAST)
                .tokensEstimated(13)
                .tokensSavedCompression(14)
                .build();

        RequestLog savedDefault = requestLogRepository.saveAndFlush(defaultCompressionLog);
        RequestLog savedExplicit = requestLogRepository.saveAndFlush(explicitCompressionLog);

        assertEquals(0, savedDefault.getTokensSavedCompression(), "tokensSavedCompression debe inicializarse a 0 por defecto cuando es nulo");
        assertEquals(14, savedExplicit.getTokensSavedCompression());
    }
}
