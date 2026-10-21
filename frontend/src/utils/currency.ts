import type { CurrencyCode } from '../types/router';

/**
 * Tipo de cambio fijo de referencia utilizado en el Dashboard para convertir
 * importes expresados en dólares estadounidenses (USD) a euros (EUR).
 * Equivalencia: 1.00 USD = 0.92 EUR.
 */
export const USD_TO_EUR_RATE = 0.92;

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
