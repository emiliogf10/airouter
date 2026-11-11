package com.tuproyecto.airouter.models.dto;

import java.math.BigDecimal;

/**
 * DTO de salida que encapsula las métricas agregadas de uso de tokens, distribución de tráfico
 * por modelo LLM, aciertos de la Caché Semántica y el ahorro económico estimado por el enrutador
 * inteligente (AI Cost Router).
 *
 * @param totalRequests               número total de peticiones procesadas
 * @param totalTokensEstimated        suma total de tokens estimados en todas las peticiones
 * @param fastModelRequests           cantidad de peticiones enrutadas al modelo rápido y económico ({@code gemini-2.5-flash})
 * @param proModelRequests            cantidad de peticiones enrutadas al modelo avanzado ({@code gemini-2.5-pro})
 * @param fastModelTokens             suma de tokens estimados procesados por el modelo económico ({@code gemini-2.5-flash})
 * @param proModelTokens              suma de tokens estimados procesados por el modelo avanzado ({@code gemini-2.5-pro})
 * @param estimatedActualCostUsd      coste real estimado en dólares estadounidenses (USD) descontando las peticiones servidas desde caché a coste cero (escalado a 6 decimales)
 * @param estimatedBaselineProCostUsd coste hipotético de referencia en USD si todas las peticiones se hubieran enrutado al modelo avanzado sin caché (escalado a 6 decimales)
 * @param estimatedSavingsUsd         ahorro económico neto estimado en USD ({@code estimatedBaselineProCostUsd - estimatedActualCostUsd}, escalado a 6 decimales)
 * @param savingsPercentage           porcentaje de ahorro económico obtenido respecto al coste base ({@code 0.0} a {@code 100.0}, redondeado a 2 decimales)
 * @param cacheHits                   cantidad total de peticiones resueltas mediante la Caché Semántica a coste cero ({@code $0.000000 USD})
 */
public record RouterStatsResponse(
        long totalRequests,
        long totalTokensEstimated,
        long fastModelRequests,
        long proModelRequests,
        long fastModelTokens,
        long proModelTokens,
        BigDecimal estimatedActualCostUsd,
        BigDecimal estimatedBaselineProCostUsd,
        BigDecimal estimatedSavingsUsd,
        double savingsPercentage,
        long cacheHits
) {

    /**
     * Constructor de compatibilidad hacia atrás que inicializa {@code cacheHits} en {@code 0L}.
     *
     * @param totalRequests               número total de peticiones procesadas
     * @param totalTokensEstimated        suma total de tokens estimados en todas las peticiones
     * @param fastModelRequests           cantidad de peticiones enrutadas al modelo económico
     * @param proModelRequests            cantidad de peticiones enrutadas al modelo avanzado
     * @param fastModelTokens             suma de tokens del modelo económico
     * @param proModelTokens              suma de tokens del modelo avanzado
     * @param estimatedActualCostUsd      coste real estimado en USD
     * @param estimatedBaselineProCostUsd coste base hipotético en USD
     * @param estimatedSavingsUsd         ahorro neto estimado en USD
     * @param savingsPercentage           porcentaje de ahorro económico
     */
    public RouterStatsResponse(
            long totalRequests,
            long totalTokensEstimated,
            long fastModelRequests,
            long proModelRequests,
            long fastModelTokens,
            long proModelTokens,
            BigDecimal estimatedActualCostUsd,
            BigDecimal estimatedBaselineProCostUsd,
            BigDecimal estimatedSavingsUsd,
            double savingsPercentage) {
        this(
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
                0L
        );
    }
}
