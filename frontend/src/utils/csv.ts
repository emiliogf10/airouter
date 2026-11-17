import type { CurrencyCode, RouterResponse } from '../types/router';
import {
  calculateBaselineProCostUsd,
  calculateLogCostUsd,
  convertFromUsd,
} from './currency';

/**
 * Expresión regular que detecta si el contenido de una celda CSV comienza (directamente
 * o tras espacios iniciales) con caracteres de control de fórmulas (`=`, `+`, `-`, `@`, `\t`, `\r`)
 * susceptibles de ataques de **CSV Formula Injection (DDE)**.
 */
const CSV_FORMULA_TRIGGER_REGEX = /^[\s]*[=+\-@\t\r]/;

/**
 * Sanitiza y escapa el valor de una celda para exportación CSV conforme a RFC 4180
 * y a las directrices de auditoría de seguridad (`security-audit` — prevención de CSV Injection).
 *
 * - Si el texto comienza con `=`, `+`, `-`, `@`, `\t` o `\r`, antepone una comilla simple `'`
 *   para neutralizar la ejecución automática de fórmulas en Microsoft Excel, LibreOffice Calc o Google Sheets.
 * - Duplica cualquier comilla doble interna (`"` -> `""`) y envuelve la celda entre comillas dobles `"..."`.
 *
 * @param value Valor arbitrario de la celda a serializar.
 * @returns Celda CSV escapada y protegida entre comillas dobles.
 */
export function sanitizeCsvCell(value: unknown): string {
  const raw = String(value ?? '');
  const neutralized = CSV_FORMULA_TRIGGER_REGEX.test(raw) ? `'${raw}` : raw;
  const escaped = neutralized.replace(/"/g, '""');
  return `"${escaped}"`;
}

/**
 * Construye el contenido textual completo en formato CSV a partir del listado de peticiones
 * registradas, convirtiendo los costes y ahorros individuales a la divisa activa (`EUR` o `USD`).
 *
 * @param logs     Lista de registros de auditoría (`RouterResponse[]`) a exportar.
 * @param currency Divisa seleccionada actualmente en el Dashboard (`'EUR'` o `'USD'`).
 * @returns Cadena en formato CSV con cabeceras y filas separadas por salto de línea (`\r\n`).
 */
export function buildLogsCsvContent(
  logs: RouterResponse[],
  currency: CurrencyCode,
): string {
  const headers = [
    'ID',
    'Fecha',
    'Usuario',
    'Modelo',
    'Cache_Hit',
    'Tokens_Estimados',
    `Coste_Facturado_${currency}`,
    `Ahorro_${currency}`,
    'Prompt',
    'Respuesta',
  ].map(sanitizeCsvCell);

  const rows = logs.map((log) => {
    const actualUsd = calculateLogCostUsd(log.modelRouted, log.tokensEstimated, log.cacheHit);
    const baselineUsd = calculateBaselineProCostUsd(log.tokensEstimated);
    const savedUsd = Math.max(0, Number((baselineUsd - actualUsd).toFixed(6)));

    const actualInCurrency = convertFromUsd(actualUsd, currency).toFixed(6);
    const savedInCurrency = convertFromUsd(savedUsd, currency).toFixed(6);

    return [
      log.id,
      log.createdAt,
      log.userId,
      log.modelRouted,
      log.cacheHit ? 'SI' : 'NO',
      log.tokensEstimated,
      actualInCurrency,
      savedInCurrency,
      log.promptContent,
      log.simulatedResponse ?? '',
    ]
      .map(sanitizeCsvCell)
      .join(',');
  });

  return [headers.join(','), ...rows].join('\r\n');
}

/**
 * Genera y descarga en el navegador un archivo `.csv` con codificación UTF-8 + BOM (`\uFEFF`)
 * para garantizar la correcta visualización de tildes y del símbolo `€` en hojas de cálculo.
 *
 * @param logs     Lista de registros de auditoría a exportar.
 * @param currency Divisa activa para expresar las columnas de coste y ahorro.
 */
export function exportLogsToCsv(
  logs: RouterResponse[],
  currency: CurrencyCode,
): void {
  if (!logs || logs.length === 0) {
    return;
  }

  const csvBody = buildLogsCsvContent(logs, currency);
  const bomContent = `\uFEFF${csvBody}`;
  const blob = new Blob([bomContent], { type: 'text/csv;charset=utf-8;' });

  if (typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function') {
    return;
  }

  const downloadUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = downloadUrl;
  anchor.download = `ai-cost-router-logs-${currency.toLowerCase()}.csv`;
  anchor.className = 'hidden';
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);

  if (typeof URL.revokeObjectURL === 'function') {
    URL.revokeObjectURL(downloadUrl);
  }
}
