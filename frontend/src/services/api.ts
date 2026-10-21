import type {
  ErrorResponse,
  PromptRequest,
  RouterResponse,
  RouterStatsResponse,
} from '../types/router';

/**
 * URL base de los endpoints REST del enrutador inteligente.
 * En desarrollo se redirige mediante el proxy de Vite a `http://localhost:8080/api/v1/router`.
 */
const API_BASE_URL = '/api/v1/router';

/**
 * Clase de error personalizada para encapsular fallos HTTP devueltos por la API del backend,
 * incluyendo soporte específico para el código HTTP 429 (Rate Limiting) y la cabecera `Retry-After`.
 */
export class ApiError extends Error {
  /** Código de estado HTTP devuelto por el servidor (ej. 400, 429, 500). */
  public readonly status: number;
  /** Segundos de espera indicados por la cabecera `Retry-After` en caso de HTTP 429. */
  public readonly retryAfterSeconds?: number;

  /**
   * Crea una nueva instancia de `ApiError`.
   *
   * @param message           Mensaje descriptivo del error en español.
   * @param status            Código de estado HTTP de la respuesta.
   * @param retryAfterSeconds Segundos de espera sugeridos antes de reintentar (opcional, para HTTP 429).
   */
  constructor(message: string, status: number, retryAfterSeconds?: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

/**
 * Procesa una respuesta HTTP no exitosa (`!response.ok`), extrayendo el mensaje del DTO
 * `ErrorResponse` del backend y la cabecera `Retry-After` cuando el estado es HTTP 429.
 *
 * @param response Objeto `Response` nativo de la API Fetch.
 * @returns Promesa rechazada con una instancia enriquecida de `ApiError`.
 */
async function handleErrorResponse(response: Response): Promise<never> {
  let backendMessage = `Error HTTP ${response.status} al comunicarse con el servidor.`;

  try {
    const errorBody = (await response.json()) as Partial<ErrorResponse>;
    if (errorBody && typeof errorBody.message === 'string' && errorBody.message.trim() !== '') {
      backendMessage = errorBody.message;
    }
  } catch {
    // Si el cuerpo no es JSON válido, se conserva el mensaje genérico por defecto
  }

  if (response.status === 429) {
    const retryAfterHeader = response.headers?.get?.('Retry-After');
    const retryAfterSeconds = retryAfterHeader ? Number.parseInt(retryAfterHeader, 10) : undefined;
    const validRetrySeconds =
      retryAfterSeconds !== undefined && !Number.isNaN(retryAfterSeconds)
        ? retryAfterSeconds
        : undefined;

    const enrichedMessage =
      validRetrySeconds !== undefined
        ? `${backendMessage} (Reintentar en ${validRetrySeconds} segundos - Retry-After: ${validRetrySeconds}s)`
        : backendMessage;

    throw new ApiError(enrichedMessage, 429, validRetrySeconds);
  }

  throw new ApiError(backendMessage, response.status);
}

/**
 * Envía un prompt al endpoint `POST /api/v1/router/prompt` para evaluar su complejidad,
 * estimar sus tokens y enrutarlo al modelo óptimo en coste (`gemini-2.5-flash` o `gemini-2.5-pro`).
 *
 * @param request Objeto con el `userId` y el texto del `prompt` a evaluar.
 * @param apiKey  Clave de API opcional enviada en la cabecera `X-API-Key` para identificar la cuota de Rate Limiting.
 * @returns Promesa con el objeto `RouterResponse` que contiene la decisión de enrutamiento y la respuesta simulada.
 * @throws {ApiError} Si ocurre un error de validación (HTTP 400) o se supera el límite de peticiones (HTTP 429).
 */
export async function sendPrompt(
  request: PromptRequest,
  apiKey?: string,
): Promise<RouterResponse> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  if (apiKey && apiKey.trim() !== '') {
    headers['X-API-Key'] = apiKey.trim();
  }

  const response = await fetch(`${API_BASE_URL}/prompt`, {
    method: 'POST',
    headers,
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    return handleErrorResponse(response);
  }

  return (await response.json()) as RouterResponse;
}

/**
 * Consulta el historial de peticiones evaluadas desde el endpoint `GET /api/v1/router/logs`,
 * ordenadas de más reciente a más antigua.
 *
 * @param userId Identificador de usuario opcional para filtrar únicamente las peticiones de dicho usuario.
 * @returns Promesa con la lista de registros `RouterResponse[]`.
 * @throws {ApiError} Si la petición HTTP falla.
 */
export async function fetchLogs(userId?: string): Promise<RouterResponse[]> {
  const trimmedUser = userId?.trim();
  const query = trimmedUser ? `?userId=${encodeURIComponent(trimmedUser)}` : '';
  const response = await fetch(`${API_BASE_URL}/logs${query}`);

  if (!response.ok) {
    return handleErrorResponse(response);
  }

  return (await response.json()) as RouterResponse[];
}

/**
 * Consulta las métricas agregadas de uso de tokens, distribución de modelos y ahorro económico
 * desde el endpoint `GET /api/v1/router/stats`.
 *
 * @param userId Identificador de usuario opcional para calcular las métricas únicamente de dicho usuario.
 * @returns Promesa con el objeto `RouterStatsResponse` con los indicadores clave de rendimiento (KPIs).
 * @throws {ApiError} Si la petición HTTP falla.
 */
export async function fetchStats(userId?: string): Promise<RouterStatsResponse> {
  const trimmedUser = userId?.trim();
  const query = trimmedUser ? `?userId=${encodeURIComponent(trimmedUser)}` : '';
  const response = await fetch(`${API_BASE_URL}/stats${query}`);

  if (!response.ok) {
    return handleErrorResponse(response);
  }

  return (await response.json()) as RouterStatsResponse;
}
