package com.tuproyecto.airouter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración global de seguridad web y políticas CORS (Cross-Origin Resource Sharing).
 *
 * <p>Restringe explícitamente los orígenes autorizados para invocar los endpoints bajo
 * {@code /api/v1/**}, permitiendo por defecto únicamente el servidor de desarrollo frontend
 * de Vite ({@code http://localhost:5173}) o los orígenes definidos por configuración.</p>
 */
@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    /**
     * Lista de orígenes permitidos para peticiones CORS hacia la API REST.
     */
    @Value("${airouter.security.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    /**
     * Registra las reglas restrictivas de CORS para los endpoints de la API v1.
     *
     * <p>Autoriza exclusivamente los métodos {@code GET}, {@code POST} y {@code OPTIONS},
     * habilita el uso de credenciales y expone la cabecera {@code Retry-After} para que el
     * cliente frontend pueda gestionar adecuadamente las respuestas HTTP 429 de límite de cuota.</p>
     *
     * @param registry registro de configuraciones CORS de Spring Web MVC
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "X-API-Key", "Authorization", "Accept")
                .exposedHeaders("Retry-After")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
