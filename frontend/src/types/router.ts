/**
 * Código de divisa soportado en el panel de control para visualizar los costes y ahorros.
 * - `'EUR'`: Euros (€), opción seleccionada por defecto en la interfaz.
 * - `'USD'`: Dólares estadounidenses ($), moneda nativa devuelta por el backend.
 */
export type CurrencyCode = 'EUR' | 'USD';

/**
 * DTO de entrada que representa una solicitud de evaluación y enrutamiento de un prompt hacia el backend.
 */
export interface PromptRequest {
  /** Identificador del usuario que realiza la petición (obligatorio, máximo 255 caracteres). */
  userId: string;
  /** Contenido textual del prompt que se desea evaluar y enviar al LLM (obligatorio, no vacío). */
  prompt: string;
}

/**
 * DTO de salida que encapsula el resultado del enrutamiento de un prompt y los metadatos
 * registrados en el historial de peticiones (`request_logs`).
 */
export interface RouterResponse {
  /** Identificador único universal (UUID) del registro de la petición. */
  id: string;
  /** Identificador del usuario que originó la petición. */
  userId: string;
  /** Contenido íntegro del prompt evaluado. */
  promptContent: string;
  /** Nombre del modelo LLM seleccionado por el enrutador de costes (`gemini-2.5-flash` o `gemini-2.5-pro`). */
  modelRouted: string;
  /** Cantidad estimada de tokens calculada para el prompt. */
  tokensEstimated: number;
  /** Respuesta simulada generada por el motor de enrutamiento. */
  simulatedResponse: string | null;
  /** Fecha y hora ISO en la que se registró la petición. */
  createdAt: string;
}

/**
 * DTO de salida que encapsula las métricas agregadas de uso de tokens, distribución de tráfico
 * por modelo LLM y el ahorro económico estimado en USD por el enrutador inteligente (AI Cost Router).
 */
export interface RouterStatsResponse {
  /** Número total de peticiones procesadas. */
  totalRequests: number;
  /** Suma total de tokens estimados en todas las peticiones. */
  totalTokensEstimated: number;
  /** Cantidad de peticiones enrutadas al modelo rápido y económico (`gemini-2.5-flash`). */
  fastModelRequests: number;
  /** Cantidad de peticiones enrutadas al modelo avanzado (`gemini-2.5-pro`). */
  proModelRequests: number;
  /** Suma de tokens estimados procesados por el modelo económico (`gemini-2.5-flash`). */
  fastModelTokens: number;
  /** Suma de tokens estimados procesados por el modelo avanzado (`gemini-2.5-pro`). */
  proModelTokens: number;
  /** Coste real estimado en dólares estadounidenses (USD) según el modelo asignado a cada petición. */
  estimatedActualCostUsd: number;
  /** Coste hipotético de referencia en USD si todas las peticiones se hubieran enrutado al modelo avanzado. */
  estimatedBaselineProCostUsd: number;
  /** Ahorro económico neto estimado en USD (`estimatedBaselineProCostUsd - estimatedActualCostUsd`). */
  estimatedSavingsUsd: number;
  /** Porcentaje de ahorro económico obtenido respecto al coste base (`0.0` a `100.0`). */
  savingsPercentage: number;
}

/**
 * DTO de respuesta estandarizado devuelto por el backend cuando ocurre un error de validación (HTTP 400)
 * o de límite de tasa (HTTP 429 Too Many Requests).
 */
export interface ErrorResponse {
  /** Descripción legible en español del error ocurrido. */
  message: string;
  /** Fecha y hora exacta en la que se generó la respuesta de error. */
  timestamp: string;
}
