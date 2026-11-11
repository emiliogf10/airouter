package com.tuproyecto.airouter.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Pruebas unitarias aisladas para {@link GeminiRestClient} empleando {@link MockRestServiceServer}.
 *
 * <p>Verifica el contrato HTTP con el endpoint oficial de Google Gemini ({@code /v1beta/models/{model}:generateContent}),
 * el envío seguro de la cabecera {@code x-goog-api-key}, la extracción de {@code candidates[0].content.parts[0].text},
 * el manejo resiliente de errores HTTP (401/500) sin lanzar excepciones y el enmascaramiento de claves.</p>
 */
class GeminiRestClientTest {

    /**
     * Servidor simulado de Spring Test para interceptar peticiones del {@link RestClient} sin tráfico de red real.
     */
    private MockRestServiceServer mockServer;

    /**
     * Instancia del cliente de Google Gemini bajo prueba.
     */
    private GeminiRestClient geminiRestClient;

    /**
     * Configura un {@link RestClient} vinculado a {@link MockRestServiceServer} antes de cada test.
     */
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com");
        this.mockServer = MockRestServiceServer.bindTo(builder).build();
        this.geminiRestClient = new GeminiRestClient(builder.build());
    }

    /**
     * Verifica que {@link GeminiRestClient#generateContent(String, String, String)} realice una petición
     * {@code POST /v1beta/models/{model}:generateContent} con la cabecera {@code x-goog-api-key} y extraiga
     * correctamente el texto de {@code candidates[0].content.parts[0].text}.
     */
    @Test
    @DisplayName("Debe enviar POST con cabecera x-goog-api-key y extraer candidates[0].content.parts[0].text exitosamente")
    void shouldSendPostWithApiKeyHeaderAndExtractCandidateText() {
        String geminiJsonResponse = """
                {
                    "candidates": [
                        {
                            "content": {
                                "parts": [
                                    {
                                        "text": "  Respuesta real generada por Google Gemini 2.5 Flash.  "
                                    }
                                ]
                            }
                        }
                    ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(GeminiRestClient.GEMINI_API_KEY_HEADER, "AIzaSyTestKey123456"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Explica qué es un API Gateway"))
                .andRespond(withSuccess(geminiJsonResponse, MediaType.APPLICATION_JSON));

        Optional<String> result = geminiRestClient.generateContent(
                "gemini-2.5-flash",
                "Explica qué es un API Gateway",
                "  AIzaSyTestKey123456  "
        );

        mockServer.verify();
        assertTrue(result.isPresent());
        assertEquals("Respuesta real generada por Google Gemini 2.5 Flash.", result.get());
    }

    /**
     * Verifica que cuando la clave de API, el modelo o el prompt son nulos o están en blanco,
     * el cliente devuelva {@link Optional#empty()} inmediatamente sin realizar ninguna petición HTTP.
     */
    @Test
    @DisplayName("Debe devolver Optional.empty() sin realizar llamada HTTP cuando apiKey, model o prompt son nulos o en blanco")
    void shouldReturnEmptyWithoutHttpCallWhenArgumentsAreNullOrBlank() {
        assertFalse(geminiRestClient.generateContent("gemini-2.5-flash", "Hola", null).isPresent());
        assertFalse(geminiRestClient.generateContent("gemini-2.5-flash", "Hola", "   ").isPresent());
        assertFalse(geminiRestClient.generateContent(null, "Hola", "AIzaSyKey").isPresent());
        assertFalse(geminiRestClient.generateContent("   ", "Hola", "AIzaSyKey").isPresent());
        assertFalse(geminiRestClient.generateContent("gemini-2.5-flash", null, "AIzaSyKey").isPresent());
        assertFalse(geminiRestClient.generateContent("gemini-2.5-flash", "   ", "AIzaSyKey").isPresent());

        mockServer.verify();
    }

    /**
     * Verifica que ante respuestas de error HTTP del servidor remoto (como 401 Unauthorized o 500 Internal Server Error)
     * o cuerpos vacíos, {@link GeminiRestClient#generateContent(String, String, String)} capture la excepción y
     * devuelva {@link Optional#empty()} para permitir el fallback transparente a simulación.
     */
    @Test
    @DisplayName("Debe capturar errores HTTP (401 Unauthorized y 500 Server Error) o candidatos vacíos devolviendo Optional.empty()")
    void shouldReturnEmptyWhenServerReturnsHttpErrorOrEmptyCandidates() {
        // Caso 1: Error HTTP 401 Unauthorized (API Key inválida)
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        Optional<String> unauthorizedResult = geminiRestClient.generateContent(
                "gemini-2.5-flash",
                "Hola",
                "AIzaSyInvalidKey"
        );
        assertFalse(unauthorizedResult.isPresent());
        mockServer.verify();

        // Caso 2: Error HTTP 500 Internal Server Error
        mockServer.reset();
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        Optional<String> serverErrorResult = geminiRestClient.generateContent(
                "gemini-2.5-pro",
                "Analizar arquitectura",
                "AIzaSyValidKey"
        );
        assertFalse(serverErrorResult.isPresent());
        mockServer.verify();

        // Caso 3: Respuesta 200 OK pero con lista de candidatos vacía
        mockServer.reset();
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        Optional<String> emptyCandidatesResult = geminiRestClient.generateContent(
                "gemini-2.5-flash",
                "Hola",
                "AIzaSyValidKey"
        );
        assertFalse(emptyCandidatesResult.isPresent());
        mockServer.verify();
    }

    /**
     * Comprueba que {@link GeminiRestClient#maskApiKey(String)} enmascare correctamente las claves de API,
     * mostrando únicamente los primeros 4 caracteres seguidos de {@code ***} o {@code ***} si es corta/nula.
     */
    @Test
    @DisplayName("Debe enmascarar correctamente las claves de API en maskApiKey sin exponer el secreto completo")
    void shouldMaskApiKeySafely() {
        assertEquals("AIza***", GeminiRestClient.maskApiKey("AIzaSyD-9876543210abcdef"));
        assertEquals("AIza***", GeminiRestClient.maskApiKey("  AIzaSyD-9876543210abcdef  "));
        assertEquals("***", GeminiRestClient.maskApiKey("abcd"));
        assertEquals("***", GeminiRestClient.maskApiKey("ab"));
        assertEquals("***", GeminiRestClient.maskApiKey("   "));
        assertEquals("***", GeminiRestClient.maskApiKey(null));
    }
}
