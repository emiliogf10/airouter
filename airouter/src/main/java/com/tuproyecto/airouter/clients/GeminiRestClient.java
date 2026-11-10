package com.tuproyecto.airouter.clients;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP síncrono basado en {@link RestClient} de Spring Framework para la integración
 * con la API oficial de Google Gemini ({@code /v1beta/models/{model}:generateContent}).
 *
 * <p>Implementa medidas estrictas de seguridad y resiliencia:</p>
 * <ul>
 *   <li>Envía siempre la clave de autenticación mediante la cabecera HTTP {@code x-goog-api-key}
 *       para evitar su exposición en parámetros de URL o registros de acceso.</li>
 *   <li>Enmascara cualquier clave antes de escribir mensajes de advertencia en los logs.</li>
 *   <li>Captura errores de red, timeouts o respuestas de error HTTP devolviendo {@link Optional#empty()}
 *       para permitir que el servicio de enrutamiento active el fallback transparente a simulación.</li>
 * </ul>
 */
@Component
@Slf4j
public class GeminiRestClient {

    /**
     * Nombre de la cabecera HTTP oficial de Google para autenticar peticiones a Gemini API.
     */
    public static final String GEMINI_API_KEY_HEADER = "x-goog-api-key";

    /**
     * Plantilla de ruta relativa del endpoint de generación de contenido de Google Gemini v1beta.
     */
    public static final String GENERATE_CONTENT_URI_TEMPLATE = "/v1beta/models/{model}:generateContent";

    /**
     * Número máximo de caracteres iniciales visibles al enmascarar una clave de API en los logs.
     */
    private static final int MASK_VISIBLE_PREFIX_LENGTH = 4;

    /**
     * Instancia configurada de {@link RestClient} utilizada para ejecutar las llamadas HTTP.
     */
    private final RestClient restClient;

    /**
     * Construye el cliente HTTP de Google Gemini configurando la URL base y los tiempos máximos
     * de conexión y lectura a partir de las propiedades de la aplicación.
     *
     * @param baseUrl        URL base del servicio Google Generative Language API
     * @param timeoutSeconds tiempo máximo de espera (en segundos) para conexión y lectura HTTP
     */
    @Autowired
    public GeminiRestClient(
            @Value("${airouter.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${airouter.gemini.timeout-seconds:15}") int timeoutSeconds) {
        int safeTimeoutSeconds = Math.max(1, timeoutSeconds);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(safeTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(safeTimeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Constructor alternativo que recibe una instancia preconfigurada de {@link RestClient},
     * destinado a facilitar las pruebas unitarias aisladas sin tráfico de red real.
     *
     * @param restClient instancia de {@link RestClient} a utilizar
     */
    public GeminiRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Invoca el endpoint {@code generateContent} de Google Gemini para el modelo y prompt indicados.
     *
     * <p>Si los parámetros requeridos son nulos/vacíos o si se produce cualquier fallo de comunicación,
     * autenticación o cuota, registra una advertencia enmascarando la clave y devuelve
     * {@link Optional#empty()} para activar el modo de respaldo (fallback).</p>
     *
     * @param model  identificador del modelo de Gemini (ej. {@code gemini-2.5-flash} o {@code gemini-2.5-pro})
     * @param prompt texto del prompt del usuario a enviar al modelo
     * @param apiKey clave de API de Google Gemini (del usuario vía BYOK o del servidor)
     * @return {@link Optional} con el texto generado por Gemini, o {@link Optional#empty()} si no fue posible obtenerlo
     */
    public Optional<String> generateContent(String model, String prompt, String apiKey) {
        if (apiKey == null || apiKey.isBlank() || model == null || model.isBlank() || prompt == null || prompt.isBlank()) {
            return Optional.empty();
        }

        String trimmedApiKey = apiKey.trim();
        GeminiGenerateRequest requestPayload = new GeminiGenerateRequest(
                List.of(new GeminiContent(List.of(new GeminiPart(prompt))))
        );

        try {
            GeminiGenerateResponse response = restClient.post()
                    .uri(GENERATE_CONTENT_URI_TEMPLATE, model.trim())
                    .header(GEMINI_API_KEY_HEADER, trimmedApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(GeminiGenerateResponse.class);

            return extractTextFromResponse(response);
        } catch (RestClientException ex) {
            log.warn("Fallo al invocar Google Gemini API [modelo='{}', apiKey='{}']: {}. Se activará fallback a simulación.",
                    model, maskApiKey(trimmedApiKey), ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Error inesperado en el cliente de Google Gemini [modelo='{}', apiKey='{}']: {}. Se activará fallback a simulación.",
                    model, maskApiKey(trimmedApiKey), ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Enmascara una clave de API dejando visibles únicamente sus primeros caracteres
     * para impedir la filtración de credenciales en trazas o archivos de log.
     *
     * @param apiKey clave de API original
     * @return representación enmascarada segura (ej. {@code AIza***})
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "***";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= MASK_VISIBLE_PREFIX_LENGTH) {
            return "***";
        }
        return trimmed.substring(0, MASK_VISIBLE_PREFIX_LENGTH) + "***";
    }

    /**
     * Extrae de forma segura el texto de la primera parte del primer candidato devuelto por Gemini.
     *
     * @param response DTO de respuesta deserializado desde JSON
     * @return {@link Optional} con el texto recortado si existe y no está en blanco
     */
    private Optional<String> extractTextFromResponse(GeminiGenerateResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return Optional.empty();
        }
        GeminiCandidate firstCandidate = response.candidates().getFirst();
        if (firstCandidate == null || firstCandidate.content() == null
                || firstCandidate.content().parts() == null || firstCandidate.content().parts().isEmpty()) {
            return Optional.empty();
        }
        GeminiPart firstPart = firstCandidate.content().parts().getFirst();
        if (firstPart == null || firstPart.text() == null || firstPart.text().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(firstPart.text().trim());
    }

    /**
     * DTO que representa el cuerpo de la petición JSON enviada al endpoint {@code generateContent} de Gemini.
     *
     * @param contents lista de bloques de contenido enviados al modelo
     */
    public record GeminiGenerateRequest(List<GeminiContent> contents) {
    }

    /**
     * DTO que representa un bloque de contenido compuesto por una o varias partes textuales.
     *
     * @param parts lista de partes de texto que conforman el mensaje
     */
    public record GeminiContent(List<GeminiPart> parts) {
    }

    /**
     * DTO que representa un fragmento individual de texto dentro de un mensaje de Gemini.
     *
     * @param text contenido textual del fragmento
     */
    public record GeminiPart(String text) {
    }

    /**
     * DTO que representa la respuesta JSON devuelta por el endpoint {@code generateContent} de Gemini.
     *
     * @param candidates lista de respuestas candidatas generadas por el modelo
     */
    public record GeminiGenerateResponse(List<GeminiCandidate> candidates) {
    }

    /**
     * DTO que representa un candidato de respuesta generado por el modelo Gemini.
     *
     * @param content contenido textual estructurado generado en este candidato
     */
    public record GeminiCandidate(GeminiContent content) {
    }
}
