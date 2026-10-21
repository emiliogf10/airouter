package com.tuproyecto.airouter.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tuproyecto.airouter.models.entities.RequestLog;
import com.tuproyecto.airouter.services.RouterService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pruebas de integración para {@link RequestLogRepository} y la entidad {@link RequestLog}
 * verificando el esquema Flyway en H2, generación de UUID, callback {@code @PrePersist},
 * ordenación cronológica descendente y contrato manual de {@code equals}/{@code hashCode}.
 */
@SpringBootTest
@Transactional
class RequestLogRepositoryTest {

    /**
     * Repositorio JPA bajo prueba conectado a la base de datos H2 en memoria.
     */
    @Autowired
    private RequestLogRepository requestLogRepository;

    /**
     * Limpia todos los registros de la tabla {@code request_logs} antes de cada prueba.
     */
    @BeforeEach
    void setUp() {
        requestLogRepository.deleteAll();
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
}
