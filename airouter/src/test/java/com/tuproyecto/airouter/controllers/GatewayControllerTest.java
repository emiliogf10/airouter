package com.tuproyecto.airouter.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tuproyecto.airouter.repositories.RequestLogRepository;
import com.tuproyecto.airouter.services.RouterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Pruebas de integración extremo a extremo para {@link GatewayController} y {@link com.tuproyecto.airouter.exceptions.GlobalExceptionHandler}.
 *
 * <p>Verifica el comportamiento HTTP de los endpoints {@code POST /api/v1/router/prompt} y
 * {@code GET /api/v1/router/logs}, el formato de errores {@code ErrorResponse} ante entradas inválidas
 * y la persistencia real en la base de datos H2 gestionada por Flyway.</p>
 */
@SpringBootTest
class GatewayControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestLogRepository requestLogRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
        this.requestLogRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/router/prompt debe procesar un prompt sencillo, persistirlo en H2 y devolver 200 OK")
    void shouldRouteSimplePromptAndPersistInDatabase() throws Exception {
        String payload = """
                {
                    "userId": "qa-user-1",
                    "prompt": "Hola, ¿cómo estás?"
                }
                """;

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.userId", is("qa-user-1")))
                .andExpect(jsonPath("$.promptContent", is("Hola, ¿cómo estás?")))
                .andExpect(jsonPath("$.modelRouted", is(RouterService.MODEL_FAST)))
                .andExpect(jsonPath("$.tokensEstimated", is(5)))
                .andExpect(jsonPath("$.simulatedResponse", containsString(RouterService.MODEL_FAST)))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/router/prompt debe enrutar a gemini-2.5-pro cuando contiene palabra clave compleja")
    void shouldRouteComplexPromptToProModel() throws Exception {
        String payload = """
                {
                    "userId": "qa-architect",
                    "prompt": "Necesito refactorizar y optimizar la arquitectura del microservicio"
                }
                """;

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("qa-architect")))
                .andExpect(jsonPath("$.modelRouted", is(RouterService.MODEL_PRO)))
                .andExpect(jsonPath("$.simulatedResponse", containsString(RouterService.MODEL_PRO)));
    }

    @Test
    @DisplayName("GET /api/v1/router/logs debe devolver el historial ordenado descendentemente y permitir filtrar por userId")
    void shouldPersistAndReturnLogsOrderedAndFilteredByUserId() throws Exception {
        String firstPayload = """
                {
                    "userId": "alice",
                    "prompt": "Primer prompt de Alice"
                }
                """;
        String secondPayload = """
                {
                    "userId": "bob",
                    "prompt": "Quiero analizar en profundidad este algoritmo"
                }
                """;
        String thirdPayload = """
                {
                    "userId": "alice",
                    "prompt": "Segundo prompt de Alice"
                }
                """;

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPayload))
                .andExpect(status().isOk());

        Thread.sleep(15);

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondPayload))
                .andExpect(status().isOk());

        Thread.sleep(15);

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(thirdPayload))
                .andExpect(status().isOk());

        // Consulta global sin userId -> 3 registros en orden descendente (más reciente primero)
        mockMvc.perform(get("/api/v1/router/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].promptContent", is("Segundo prompt de Alice")))
                .andExpect(jsonPath("$[1].promptContent", is("Quiero analizar en profundidad este algoritmo")))
                .andExpect(jsonPath("$[2].promptContent", is("Primer prompt de Alice")));

        // Consulta con userId en blanco -> equivale a consulta global (3 registros)
        mockMvc.perform(get("/api/v1/router/logs").param("userId", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // Consulta filtrada por usuario "alice" -> 2 registros ordenados del más reciente al más antiguo
        mockMvc.perform(get("/api/v1/router/logs").param("userId", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].promptContent", is("Segundo prompt de Alice")))
                .andExpect(jsonPath("$[1].promptContent", is("Primer prompt de Alice")));
    }

    @Test
    @DisplayName("POST /api/v1/router/prompt debe devolver 400 Bad Request y ErrorResponse cuando userId o prompt están vacíos")
    void shouldReturnBadRequestWhenPayloadFieldsAreBlank() throws Exception {
        String blankUserPayload = """
                {
                    "userId": "   ",
                    "prompt": "Hola"
                }
                """;

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankUserPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("userId")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        String blankPromptPayload = """
                {
                    "userId": "user-1",
                    "prompt": "   "
                }
                """;

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankPromptPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("prompt")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/router/prompt debe devolver 400 Bad Request y ErrorResponse cuando el JSON está malformado o vacío")
    void shouldReturnBadRequestWhenJsonIsMalformedOrEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ json_invalido: "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        mockMvc.perform(post("/api/v1/router/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }
}
