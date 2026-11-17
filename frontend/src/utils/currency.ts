import type { CurrencyCode } from '../types/router';

/**
 * Tipo de cambio fijo de referencia utilizado en el Dashboard para convertir
 * importes expresados en dólares estadounidenses (USD) a euros (EUR).
 * Equivalencia: 1.00 USD = 0.92 EUR.
 */
export const USD_TO_EUR_RATE = 0.92;

/**
 * Tarifa oficial en dólares estadounidenses (USD) por cada 1.000.000 de tokens
 * procesados en el modelo rápido y económico (`gemini-2.5-flash`).
 */
export const FLASH_RATE_PER_MILLION_USD = 0.15;

/**
 * Tarifa oficial en dólares estadounidenses (USD) por cada 1.000.000 de tokens
 * procesados en el modelo de razonamiento avanzado (`gemini-2.5-pro`).
 */
export const PRO_RATE_PER_MILLION_USD = 1.25;

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
 * en función del modelo asignado, los tokens estimados y si fue resuelta por la Caché Semántica.
 *
 * - Si `cacheHit === true` o `tokensEstimated <= 0`, devuelve `0`.
 * - Si el modelo incluye `'pro'`, aplica `$1.25 / 1M` tokens.
 * - En caso contrario (`gemini-2.5-flash`), aplica `$0.15 / 1M` tokens.
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
  const isPro = (modelRouted ?? '').toLowerCase().includes('pro');
  const rate = isPro ? PRO_RATE_PER_MILLION_USD : FLASH_RATE_PER_MILLION_USD;
  return Number(((tokensEstimated * rate) / 1_000_000).toFixed(6));
}

/**
 * Calcula el coste base hipotético en dólares estadounidenses (USD) si la petición
 * se hubiera procesado íntegramente con el modelo avanzado (`gemini-2.5-pro` a `$1.25 / 1M` tokens).
 *
 * @param tokensEstimated Número de tokens estimados del prompt.
 * @returns Coste base Pro en USD redondeado a 6 decimales.
 */
export function calculateBaselineProCostUsd(tokensEstimated: number): number {
  if (!Number.isFinite(tokensEstimated) || tokensEstimated <= 0) {
    return 0;
  }
  return Number(((tokensEstimated * PRO_RATE_PER_MILLION_USD) / 1_000_000).toFixed(6));
}
