/**
 * Código de divisa soportado en el panel de control para visualizar los costes y ahorros.
 * - `'EUR'`: Euros (€), opción seleccionada por defecto en la interfaz.
 * - `'USD'`: Dólares estadounidenses ($), moneda nativa devuelta por el backend.
 */
export type CurrencyCode = 'EUR' | 'USD';

/**
 * Estado operativo del cortafuegos presupuestario (FinOps) para un usuario:
 * - `'UNLIMITED'`: El usuario no tiene un límite presupuestario activo.
 * - `'OK'`: Consumo por debajo del umbral preventivo del 80%.
 * - `'WARNING_80_DOWNGRADE'`: Consumo entre el 80% y el 99.99% con degradación automática a modelo económico (`gemini-2.5-flash`).
 * - `'EXCEEDED_100_BLOCKED'`: Consumo igual o superior al 100% del presupuesto; peticiones bloqueadas con HTTP 402.
 */
export type BudgetStatus =
  | 'UNLIMITED'
  | 'OK'
  | 'WARNING_80_DOWNGRADE'
  | 'EXCEEDED_100_BLOCKED';

/**
 * DTO de entrada para configurar o actualizar el límite de presupuesto de un usuario (`PUT /api/v1/router/budget/{userId}`).
 */
export interface UserBudgetRequest {
  /** Presupuesto máximo permitido en USD (`null` o `<= 0` para eliminar el límite). */
  maxBudgetUsd: number | null;
  /** Indica si se debe forzar el modelo económico (`gemini-2.5-flash`) al alcanzar el 80% del presupuesto. */
  autoDowngradeEnabled: boolean;
}

/**
 * DTO de salida con el estado actual del presupuesto y gasto acumulado de un usuario (`GET /api/v1/router/budget/{userId}`).
 */
export interface UserBudgetResponse {
  /** Identificador único del usuario al que aplica el presupuesto. */
  userId: string;
  /** Límite máximo de gasto configurado en USD (`null` si es ilimitado). */
  maxBudgetUsd: number | null;
  /** Gasto real acumulado por el usuario en USD. */
  currentSpendUsd: number;
  /** Saldo disponible restante en USD (`null` si el presupuesto es ilimitado). */
  remainingBudgetUsd: number | null;
  /** Porcentaje de consumo respecto al límite configurado (`0.0` a `100.0+`). */
  usagePercentage: number;
  /** Indica si la degradación automática al 80% está habilitada. */
  autoDowngradeEnabled: boolean;
  /** Estado calculado del cortafuegos presupuestario. */
  status: BudgetStatus;
}

/**
 * DTO de entrada para generar una nueva clave API del Gateway (`POST /api/v1/router/keys`).
 */
export interface CreateApiKeyRequest {
  /** Identificador del usuario propietario de la clave. */
  userId: string;
  /** Nombre descriptivo de la clave (ej. "Producción App Web"). */
  keyName: string;
}

/**
 * DTO de salida que representa los metadatos seguros de una clave API almacenada (`GET /api/v1/router/keys/{userId}`).
 * Por seguridad, nunca incluye el secreto en texto plano ni el hash SHA-256 completo.
 */
export interface ApiKeyResponse {
  /** Identificador único universal (UUID) de la clave API. */
  id: string;
  /** Identificador del usuario propietario de la clave. */
  userId: string;
  /** Nombre descriptivo asignado a la clave. */
  keyName: string;
  /** Prefijo visible enmascarado de la clave (ej. `air_live_9f8e...`). */
  keyPrefix: string;
  /** Indica si la clave se encuentra activa (`true`) o revocada (`false`). */
  active: boolean;
  /** Fecha y hora ISO de creación de la clave. */
  createdAt: string;
}

/**
 * DTO de salida devuelto exclusivamente en el momento de crear una clave (`POST /api/v1/router/keys`).
 * Incluye por única vez el token completo `rawApiKey` antes de que solo su hash SHA-256 quede persistido.
 */
export interface ApiKeyCreatedResponse extends ApiKeyResponse {
  /** Token secreto completo en texto plano (`air_live_<32_hex_chars>`), mostrado una única vez al crear la clave. */
  rawApiKey: string;
}

/**
 * Estado operativo de la billetera prepago (`Billing`) de un usuario:
 * - `'POSTPAID_UNLIMITED'`: El usuario opera en modo pospago libre sin haber activado prepago.
 * - `'PREPAID_ACTIVE'`: Billetera prepago activa con saldo disponible (`balanceUsd > 0`).
 * - `'PREPAID_DEPLETED'`: Billetera prepago activa con saldo agotado (`balanceUsd <= 0`); bloquea nuevas consultas con HTTP 402.
 */
export type WalletStatus =
  | 'POSTPAID_UNLIMITED'
  | 'PREPAID_ACTIVE'
  | 'PREPAID_DEPLETED';

/**
 * Métodos de pago soportados en la pasarela de recarga de créditos (`Billing`).
 */
export type PaymentMethodCode = 'STRIPE_CARD' | 'PAYPAL';

/**
 * DTO de entrada para recargar créditos en la billetera prepago de un usuario (`POST /api/v1/router/wallet/{userId}/topup`).
 */
export interface WalletTopUpRequest {
  /** Importe a recargar expresado en dólares estadounidenses (USD, mayor que 0). */
  amountUsd: number;
  /** Código de la pasarela o método de pago empleado (`'STRIPE_CARD'` o `'PAYPAL'`). */
  paymentMethod: PaymentMethodCode;
}

/**
 * DTO de salida que representa el saldo prepago en tiempo real y el historial de recargas de un usuario (`GET /api/v1/router/wallet/{userId}`).
 */
export interface UserWalletResponse {
  /** Identificador único del usuario titular de la billetera. */
  userId: string;
  /** Saldo prepago disponible actualmente en USD (precisión de 6 decimales). */
  balanceUsd: number;
  /** Suma histórica total recargada por el usuario en USD. */
  totalToppedUpUsd: number;
  /** Indica si el control de saldo prepago está activo (`true` tras la primera recarga). */
  walletEnforced: boolean;
  /** Último método de pago utilizado (`'STRIPE_CARD'`, `'PAYPAL'` o `null`). */
  lastPaymentMethod: string | null;
  /** Fecha y hora ISO de la última actualización de la billetera (`null` si nunca recargó). */
  updatedAt: string | null;
  /** Estado operativo actual de la billetera prepago. */
  status: WalletStatus;
}

/**
 * Código de proveedor LLM soportado por el motor de enrutamiento multi-proveedor:
 * - `'AUTO'`: Selección automática del proveedor más eficiente en coste.
 * - `'GOOGLE_GEMINI'`: Familia Google Gemini (`gemini-2.5-flash` / `gemini-2.5-pro`).
 * - `'OPENAI'`: Familia OpenAI (`gpt-4o-mini` / `gpt-4o`).
 * - `'ANTHROPIC'`: Familia Anthropic Claude (`claude-3-5-haiku` / `claude-3-7-sonnet`).
 */
export type LlmProviderCode = 'AUTO' | 'GOOGLE_GEMINI' | 'OPENAI' | 'ANTHROPIC';

/**
 * DTO de entrada que representa una solicitud de evaluación y enrutamiento de un prompt hacia el backend.
 */
export interface PromptRequest {
  /** Identificador del usuario que realiza la petición (obligatorio, máximo 255 caracteres). */
  userId: string;
  /** Contenido textual del prompt que se desea evaluar y enviar al LLM (obligatorio, no vacío). */
  prompt: string;
  /** Proveedor de IA objetivo opcional (`'AUTO'`, `'GOOGLE_GEMINI'`, `'OPENAI'` o `'ANTHROPIC'`). */
  provider?: LlmProviderCode | string;
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
  /** Nombre del modelo LLM seleccionado por el enrutador de costes (`gemini-2.5-flash`, `gpt-4o`, `claude-3-7-sonnet`, etc.). */
  modelRouted: string;
  /** Cantidad estimada de tokens calculada para el prompt. */
  tokensEstimated: number;
  /** Respuesta simulada o real generada tras el enrutamiento. */
  simulatedResponse: string | null;
  /** Fecha y hora ISO en la que se registró la petición. */
  createdAt: string;
  /** Indica si la petición fue resuelta mediante la Caché Semántica de Prompts a coste cero (`true`) o evaluada de nuevo (`false`). */
  cacheHit?: boolean;
  /** Tiempo de respuesta extremo a extremo de la petición en milisegundos. */
  latencyMs?: number;
  /** Cantidad de elementos sensibles (PII o secretos) anonimizados por el Escudo DLP antes del enrutamiento. */
  redactedItemsCount?: number;
  /** Cantidad de tokens ahorrados gracias al Compresor Automático de Prompts antes de la inferencia. */
  tokensSavedCompression?: number;
  /** Proveedor de IA efectivo que procesó la petición (`'GOOGLE_GEMINI'`, `'OPENAI'` o `'ANTHROPIC'`). */
  provider?: string;
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
  /** Cantidad total de peticiones servidas directamente desde la Caché Semántica a coste cero (`$0.000000`). */
  cacheHits?: number;
  /** Latencia media extremo a extremo en milisegundos de todas las peticiones procesadas. */
  averageLatencyMs?: number;
  /** Total acumulado de elementos sensibles (PII/secretos) anonimizados por el Escudo DLP. */
  totalRedactedItems?: number;
  /** Suma total acumulada de tokens ahorrados por el Compresor Automático de Prompts. */
  totalTokensSavedCompression?: number;
}

/**
 * DTO de respuesta estandarizado devuelto por el backend cuando ocurre un error de validación (HTTP 400),
 * clave API inválida/revocada (HTTP 401), bloqueo presupuestario o de billetera (HTTP 402 Payment Required) o límite de tasa (HTTP 429).
 */
export interface ErrorResponse {
  /** Descripción legible en español del error ocurrido. */
  message: string;
  /** Fecha y hora exacta en la que se generó la respuesta de error. */
  timestamp: string;
}
