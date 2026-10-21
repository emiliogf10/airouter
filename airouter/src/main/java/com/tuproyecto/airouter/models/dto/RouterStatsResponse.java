package com.tuproyecto.airouter.models.dto;

import java.math.BigDecimal;

/**
 * DTO de salida que encapsula las métricas agregadas de uso de tokens, distribución de tráfico
 * por modelo LLM y el ahorro económico estimado por el enrutador inteligente (AI Cost Router).
 *
 * @param totalRequests               número total de peticiones procesadas
 * @param totalTokensEstimated        suma total de tokens estimados en todas las peticiones
 * @param fastModelRequests           cantidad de peticiones enrutadas al modelo rápido y económico ({@code gemini-2.5-flash})
 * @param proModelRequests            cantidad de peticiones enrutadas al modelo avanzado ({@code gemini-2.5-pro})
 * @param fastModelTokens             suma de tokens estimados procesados por el modelo económico ({@code gemini-2.5-flash})
 * @param proModelTokens              suma de tokens estimados procesados por el modelo avanzado ({@code gemini-2.5-pro})
 * @param estimatedActualCostUsd      coste real estimado en dólares estadounidenses (USD) según el modelo asignado a cada petición (escalado a 6 decimales)
 * @param estimatedBaselineProCostUsd coste hipotético de referencia en USD si todas las peticiones se hubieran enrutado al modelo avanzado (escalado a 6 decimales)
 * @param estimatedSavingsUsd         ahorro económico neto estimado en USD ({@code estimatedBaselineProCostUsd - estimatedActualCostUsd}, escalado a 6 decimales)
 * @param savingsPercentage           porcentaje de ahorro económico obtenido respecto al coste base ({@code 0.0} a {@code 100.0}, redondeado a 2 decimales)
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
        double savingsPercentage
) {
}
