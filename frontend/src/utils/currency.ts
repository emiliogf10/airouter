import type { CurrencyCode } from '../types/router';

/**
 * Tipo de cambio fijo de referencia utilizado en el Dashboard para convertir
 * importes expresados en dólares estadounidenses (USD) a euros (EUR).
 * Equivalencia: 1.00 USD = 0.92 EUR.
 */
export const USD_TO_EUR_RATE = 0.92;

/**
 * Tarifa oficial en dólares estadounidenses (USD) por cada 1.000.000 de tokens
 * procesados en el modelo rápido y económico de Google (`gemini-2.5-flash`).
 */
export const FLASH_RATE_PER_MILLION_USD = 0.15;

/**
 * Tarifa oficial en dólares estadounidenses (USD) por cada 1.000.000 de tokens
 * procesados en el modelo de razonamiento avanzado de Google (`gemini-2.5-pro`).
 */
export const PRO_RATE_PER_MILLION_USD = 1.25;

/**
 * Tarifa oficial en USD por cada 1.000.000 de tokens en el modelo económico de OpenAI (`gpt-4o-mini`).
 */
export const OPENAI_MINI_RATE_PER_MILLION_USD = 0.15;

/**
 * Tarifa oficial en USD por cada 1.000.000 de tokens en el modelo avanzado de OpenAI (`gpt-4o`).
 */
export const OPENAI_PRO_RATE_PER_MILLION_USD = 2.5;

/**
 * Tarifa oficial en USD por cada 1.000.000 de tokens en el modelo rápido de Anthropic (`claude-3-5-haiku`).
 */
export const ANTHROPIC_HAIKU_RATE_PER_MILLION_USD = 0.8;

/**
 * Tarifa oficial en USD por cada 1.000.000 de tokens en el modelo avanzado de Anthropic (`claude-3-7-sonnet`).
 */
export const ANTHROPIC_SONNET_RATE_PER_MILLION_USD = 3.0;

/**
 * Determina si un nombre de modelo corresponde a la gama de razonamiento avanzado (*Pro*)
 * en cualquiera de los proveedores soportados (`gemini-2.5-pro`, `gpt-4o` o `claude-3-7-sonnet`).
 *
 * @param modelRouted Nombre del modelo LLM asignado.
 * @returns `true` si es un modelo avanzado/Pro, `false` si es un modelo rápido/económico.
 */
export function isAdvancedModel(modelRouted?: string): boolean {
  const normalized = (modelRouted ?? '').toLowerCase();
  return (
    normalized === 'gpt-4o' ||
    normalized === 'claude-3-7-sonnet' ||
    normalized.includes('pro')
  );
}

/**
 * Devuelve la etiqueta legible del proveedor de IA a partir de su código identificador.
 *
 * @param provider Código de proveedor (`'GOOGLE_GEMINI'`, `'OPENAI'`, `'ANTHROPIC'`).
 * @returns Nombre legible del proveedor (`'OpenAI'`, `'Anthropic Claude'` o `'Google Gemini'`).
 */
export function formatProviderLabel(provider?: string): string {
  const normalized = (provider ?? '').toUpperCase();
  if (normalized === 'OPENAI') {
    return 'OpenAI';
  }
  if (normalized === 'ANTHROPIC') {
    return 'Anthropic Claude';
  }
  return 'Google Gemini';
}

/**
 * Convierte un importe monetario expresado en dólares estadounidenses (USD)
 * a la divisa seleccionada por el usuario en el panel de control.
 *
 * @param amountUsd Importe original en dólares estadounidenses (USD) devuelto por el backend.
 * @param currency  Código de divisa de destino (`'EUR'` o `'USD'`).
 * @returns El importe numérico convertido a la divisa indicada (`amountUsd * 0.92` para `'EUR'`, o `amountUsd` para `'USD'`).
 */
export function convertFromUsd(amountUsd: number, currency: CurrencyCode): number {
  const safeAmount = Number.isFinite(amountUsd) ? amountUsd : 0;
  if (currency === 'EUR') {
    return safeAmount * USD_TO_EUR_RATE;
  }
  return safeAmount;
}

/**
 * Convierte un importe introducido por el usuario en la divisa activa (`'EUR'` o `'USD'`)
 * a dólares estadounidenses (USD) con precisión de 6 decimales para enviarlo al backend.
 *
 * @param amountInCurrency Importe expresado en la moneda activa de la interfaz.
 * @param currency         Código de divisa activa (`'EUR'` o `'USD'`).
 * @returns Importe equivalente en dólares estadounidenses (USD) redondeado a 6 decimales.
 */
export function convertToUsd(amountInCurrency: number, currency: CurrencyCode): number {
  const safeAmount = Number.isFinite(amountInCurrency) ? amountInCurrency : 0;
  if (currency === 'EUR') {
    return Number((safeAmount / USD_TO_EUR_RATE).toFixed(6));
  }
  return Number(safeAmount.toFixed(6));
}

/**
 * Formatea un importe originalmente en dólares estadounidenses (USD) a una cadena legible
 * con precisión de 6 decimales y el símbolo monetario correspondiente a la divisa activa.
 *
 * - Para `'EUR'`: aplica `convertFromUsd`, utiliza coma decimal española y sufijo `€` (ej. `0,506000 €`).
 * - Para `'USD'`: mantiene el valor en USD con punto decimal y prefijo `$` (ej. `$0.550000`).
 *
 * @param amountUsd Importe original en dólares estadounidenses (USD).
 * @param currency  Código de divisa activa (`'EUR'` o `'USD'`).
 * @returns Cadena formateada con 6 decimales y el símbolo de la moneda seleccionada.
 */
export function formatCurrency(amountUsd: number, currency: CurrencyCode): string {
  const converted = convertFromUsd(amountUsd, currency);
  if (currency === 'EUR') {
    return `${converted.toFixed(6).replace('.', ',')} €`;
  }
  return `$${converted.toFixed(6)}`;
}

/**
 * Calcula el coste real facturado en dólares estadounidenses (USD) de una petición individual
 * en función del modelo multi-proveedor asignado, los tokens estimados y si fue resuelta por la Caché Semántica.
 *
 * - Si `cacheHit === true` o `tokensEstimated <= 0`, devuelve `0`.
 * - OpenAI: `gpt-4o` (`$2.50/1M`), `gpt-4o-mini` (`$0.15/1M`).
 * - Anthropic: `claude-3-7-sonnet` (`$3.00/1M`), `claude-3-5-haiku` (`$0.80/1M`).
 * - Google Gemini: `gemini-2.5-pro` (`$1.25/1M`), `gemini-2.5-flash` (`$0.15/1M`).
 *
 * @param modelRouted     Nombre del modelo LLM asignado a la petición.
 * @param tokensEstimated Número de tokens estimados del prompt.
 * @param cacheHit        Indica si la petición fue un acierto de caché a coste cero.
 * @returns Coste real estimado en USD redondeado a 6 decimales.
 */
export function calculateLogCostUsd(
  modelRouted: string,
  tokensEstimated: number,
  cacheHit?: boolean,
): number {
  if (cacheHit === true || !Number.isFinite(tokensEstimated) || tokensEstimated <= 0) {
    return 0;
  }
  const normalized = (modelRouted ?? '').toLowerCase();
  let rate = FLASH_RATE_PER_MILLION_USD;

  if (normalized === 'gpt-4o') {
    rate = OPENAI_PRO_RATE_PER_MILLION_USD;
  } else if (normalized === 'gpt-4o-mini') {
    rate = OPENAI_MINI_RATE_PER_MILLION_USD;
  } else if (normalized === 'claude-3-7-sonnet') {
    rate = ANTHROPIC_SONNET_RATE_PER_MILLION_USD;
  } else if (normalized === 'claude-3-5-haiku') {
    rate = ANTHROPIC_HAIKU_RATE_PER_MILLION_USD;
  } else if (normalized.includes('pro')) {
    rate = PRO_RATE_PER_MILLION_USD;
  }

  return Number(((tokensEstimated * rate) / 1_000_000).toFixed(6));
}

/**
 * Calcula el coste base hipotético en dólares estadounidenses (USD) si la petición
 * se hubiera procesado íntegramente con el modelo avanzado (*Pro*) del proveedor correspondiente:
 * - OpenAI (`gpt-4o`): `$2.50 / 1M` tokens.
 * - Anthropic (`claude-3-7-sonnet`): `$3.00 / 1M` tokens.
 * - Google Gemini (`gemini-2.5-pro`, por defecto): `$1.25 / 1M` tokens.
 *
 * @param tokensEstimated Número de tokens estimados del prompt.
 * @param modelRouted     Modelo enrutado opcional para inferir la familia de proveedor.
 * @param provider        Código de proveedor opcional (`'GOOGLE_GEMINI'`, `'OPENAI'`, `'ANTHROPIC'`).
 * @returns Coste base Pro en USD redondeado a 6 decimales.
 */
export function calculateBaselineProCostUsd(
  tokensEstimated: number,
  modelRouted?: string,
  provider?: string,
): number {
  if (!Number.isFinite(tokensEstimated) || tokensEstimated <= 0) {
    return 0;
  }
  const normModel = (modelRouted ?? '').toLowerCase();
  const normProvider = (provider ?? '').toUpperCase();

  let proRate = PRO_RATE_PER_MILLION_USD;
  if (normModel.startsWith('gpt-') || normProvider === 'OPENAI') {
    proRate = OPENAI_PRO_RATE_PER_MILLION_USD;
  } else if (normModel.startsWith('claude-') || normProvider === 'ANTHROPIC') {
    proRate = ANTHROPIC_SONNET_RATE_PER_MILLION_USD;
  }

  return Number(((tokensEstimated * proRate) / 1_000_000).toFixed(6));
}
