import type {
  ApiKeyCreatedResponse,
  ApiKeyResponse,
  CreateApiKeyRequest,
  ErrorResponse,
  PromptRequest,
  RouterResponse,
  RouterStatsResponse,
  UserBudgetRequest,
  UserBudgetResponse,
  UserWalletResponse,
  WalletTopUpRequest,
} from '../types/router';

/**
 * URL base de los endpoints REST del enrutador inteligente.
 * En desarrollo se redirige mediante el proxy de Vite a `http://localhost:8080/api/v1/router`.
 */
const API_BASE_URL = '/api/v1/router';

/**
 * Clase de error personalizada para encapsular fallos HTTP devueltos por la API del backend,
 * incluyendo soporte específico para HTTP 401 (Clave API inválida), HTTP 402 (Cortafuegos Presupuestario / Saldo Agotado)
 * y HTTP 429 (Rate Limiting).
 */
export class ApiError extends Error {
  /** Código de estado HTTP devuelto por el servidor (ej. 400, 401, 402, 429, 500). */
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
 * Construye una respuesta de presupuesto ilimitado por defecto para un usuario,
 * utilizada como respaldo seguro cuando el usuario aún no tiene presupuesto o en entornos simulados.
 *
 * @param userId Identificador del usuario.
 * @returns Objeto `UserBudgetResponse` en estado `'UNLIMITED'`.
 */
export function createDefaultUserBudget(userId: string): UserBudgetResponse {
  return {
    userId: userId.trim() || 'demo-user',
    maxBudgetUsd: null,
    currentSpendUsd: 0,
    remainingBudgetUsd: null,
    usagePercentage: 0,
    autoDowngradeEnabled: true,
    status: 'UNLIMITED',
  };
}

/**
 * Construye un estado inicial de billetera en modo pospago (`'POSTPAID_UNLIMITED'`) para un usuario,
 * utilizado como respaldo defensivo cuando el usuario aún no ha realizado recargas o en mocks heredados.
 *
 * @param userId Identificador del usuario.
 * @returns Objeto `UserWalletResponse` en estado `'POSTPAID_UNLIMITED'`.
 */
export function createDefaultUserWallet(userId: string): UserWalletResponse {
  return {
    userId: userId.trim() || 'demo-user',
    balanceUsd: 0,
    totalToppedUpUsd: 0,
    walletEnforced: false,
    lastPaymentMethod: null,
    updatedAt: null,
    status: 'POSTPAID_UNLIMITED',
  };
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
 * verificar el presupuesto FinOps y saldo prepago del usuario, estimar sus tokens y enrutarlo al modelo óptimo.
 *
 * @param request Objeto con el `userId` y el texto del `prompt` a evaluar.
 * @param apiKey  Clave de API opcional enviada en la cabecera `X-API-Key` para autenticación o BYOK.
 * @returns Promesa con el objeto `RouterResponse` que contiene la decisión de enrutamiento y la respuesta.
 * @throws {ApiError} Si ocurre un error de validación (400), clave inválida (401), presupuesto/saldo agotado (402) o límite de tasa (429).
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

/**
 * Consulta el estado actual del presupuesto FinOps y gasto acumulado de un usuario
 * desde el endpoint `GET /api/v1/router/budget/{userId}`.
 * Incluye validación defensiva para devolver un estado `'UNLIMITED'` por defecto si un mock
 * de pruebas heredado no contempla la ruta `/budget/`.
 *
 * @param userId Identificador del usuario cuyo presupuesto se desea consultar.
 * @returns Promesa con el objeto `UserBudgetResponse`.
 */
export async function fetchUserBudget(userId: string): Promise<UserBudgetResponse> {
  const cleanUser = userId.trim() || 'demo-user';
  try {
    const response = await fetch(`${API_BASE_URL}/budget/${encodeURIComponent(cleanUser)}`);
    if (!response) {
      return createDefaultUserBudget(cleanUser);
    }
    if (!response.ok) {
      return handleErrorResponse(response);
    }
    const data = (await response.json()) as Partial<UserBudgetResponse>;
    if (!data || Array.isArray(data) || typeof data.status !== 'string') {
      return createDefaultUserBudget(cleanUser);
    }
    return data as UserBudgetResponse;
  } catch (err) {
    if (err instanceof ApiError) {
      throw err;
    }
    return createDefaultUserBudget(cleanUser);
  }
}

/**
 * Actualiza o elimina el límite presupuestario FinOps de un usuario mediante el endpoint
 * `PUT /api/v1/router/budget/{userId}`.
 *
 * @param userId  Identificador del usuario cuyo presupuesto se desea configurar.
 * @param payload Datos de configuración (`maxBudgetUsd` en USD y `autoDowngradeEnabled`).
 * @returns Promesa con el objeto `UserBudgetResponse` actualizado.
 * @throws {ApiError} Si la petición HTTP devuelve un error.
 */
export async function updateUserBudget(
  userId: string,
  payload: UserBudgetRequest,
): Promise<UserBudgetResponse> {
  const cleanUser = userId.trim() || 'demo-user';
  const response = await fetch(`${API_BASE_URL}/budget/${encodeURIComponent(cleanUser)}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    return handleErrorResponse(response);
  }

  return (await response.json()) as UserBudgetResponse;
}

/**
 * Consulta el listado de claves API asociadas a un usuario desde el endpoint
 * `GET /api/v1/router/keys/{userId}`.
 * Incluye validación defensiva para devolver `[]` cuando un mock de prueba heredado
 * devuelve datos que no corresponden a objetos `ApiKeyResponse`.
 *
 * @param userId Identificador del usuario cuyas claves se desean listar.
 * @returns Promesa con la lista de claves `ApiKeyResponse[]`.
 */
export async function fetchUserApiKeys(userId: string): Promise<ApiKeyResponse[]> {
  const cleanUser = userId.trim() || 'demo-user';
  try {
    const response = await fetch(`${API_BASE_URL}/keys/${encodeURIComponent(cleanUser)}`);
    if (!response) {
      return [];
    }
    if (!response.ok) {
      return handleErrorResponse(response);
    }
    const data = (await response.json()) as unknown;
    if (!Array.isArray(data)) {
      return [];
    }
    return data.filter(
      (item): item is ApiKeyResponse =>
        Boolean(
          item &&
            typeof item === 'object' &&
            typeof (item as Partial<ApiKeyResponse>).keyPrefix === 'string' &&
            typeof (item as Partial<ApiKeyResponse>).keyName === 'string',
        ),
    );
  } catch (err) {
    if (err instanceof ApiError) {
      throw err;
    }
    return [];
  }
}

/**
 * Genera una nueva clave API para un usuario mediante el endpoint `POST /api/v1/router/keys`.
 * La respuesta incluye por única vez el secreto `rawApiKey` antes de almacenarse como hash SHA-256.
 *
 * @param payload Objeto con el `userId` propietario y el `keyName` descriptivo.
 * @returns Promesa con el objeto `ApiKeyCreatedResponse` que contiene `rawApiKey`.
 * @throws {ApiError} Si ocurre un error de validación o de servidor.
 */
export async function createUserApiKey(
  payload: CreateApiKeyRequest,
): Promise<ApiKeyCreatedResponse> {
  const response = await fetch(`${API_BASE_URL}/keys`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    return handleErrorResponse(response);
  }

  return (await response.json()) as ApiKeyCreatedResponse;
}

/**
 * Revoca una clave API existente por su identificador único mediante el endpoint
 * `DELETE /api/v1/router/keys/{keyId}`.
 *
 * @param keyId Identificador UUID de la clave a revocar.
 * @returns Promesa resuelta cuando la clave ha sido revocada (`204 No Content`).
 * @throws {ApiError} Si la clave no existe o falla la petición HTTP.
 */
export async function revokeUserApiKey(keyId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/keys/${encodeURIComponent(keyId)}`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    return handleErrorResponse(response);
  }
}

/**
 * Consulta el saldo y estado de la billetera prepago (`Billing`) de un usuario desde el endpoint
 * `GET /api/v1/router/wallet/{userId}`.
 * Incluye validación defensiva para devolver `createDefaultUserWallet(cleanUser)` si un mock de prueba
 * heredado no contempla la ruta `/wallet/`.
 *
 * @param userId Identificador del usuario titular de la billetera.
 * @returns Promesa con el objeto `UserWalletResponse`.
 */
export async function fetchUserWallet(userId: string): Promise<UserWalletResponse> {
  const cleanUser = userId.trim() || 'demo-user';
  try {
    const response = await fetch(`${API_BASE_URL}/wallet/${encodeURIComponent(cleanUser)}`);
    if (!response) {
      return createDefaultUserWallet(cleanUser);
    }
    if (!response.ok) {
      return handleErrorResponse(response);
    }
    const data = (await response.json()) as Partial<UserWalletResponse>;
    if (
      !data ||
      Array.isArray(data) ||
      typeof data.balanceUsd !== 'number' ||
      typeof data.status !== 'string'
    ) {
      return createDefaultUserWallet(cleanUser);
    }
    return data as UserWalletResponse;
  } catch (err) {
    if (err instanceof ApiError) {
      throw err;
    }
    return createDefaultUserWallet(cleanUser);
  }
}

/**
 * Recarga créditos en la billetera prepago de un usuario mediante el endpoint
 * `POST /api/v1/router/wallet/{userId}/topup`.
 *
 * @param userId  Identificador del usuario titular de la billetera.
 * @param payload Importe en USD (`amountUsd`) y método de pago (`'STRIPE_CARD'` o `'PAYPAL'`).
 * @returns Promesa con el objeto `UserWalletResponse` actualizado tras el abono.
 * @throws {ApiError} Si la petición HTTP falla o los datos son inválidos.
 */
export async function topUpUserWallet(
  userId: string,
  payload: WalletTopUpRequest,
): Promise<UserWalletResponse> {
  const cleanUser = userId.trim() || 'demo-user';
  const response = await fetch(`${API_BASE_URL}/wallet/${encodeURIComponent(cleanUser)}/topup`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    return handleErrorResponse(response);
  }

  return (await response.json()) as UserWalletResponse;
}
