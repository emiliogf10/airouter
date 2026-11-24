import { describe, expect, it } from 'vitest';
import {
  calculateBaselineProCostUsd,
  calculateLogCostUsd,
  convertFromUsd,
  convertToUsd,
  formatCurrency,
  formatProviderLabel,
  isAdvancedModel,
  USD_TO_EUR_RATE,
} from '../utils/currency';

/**
 * Suite de pruebas unitarias para las utilidades de conversión y formateo monetario (`currency.ts`).
 * Verifica el tipo de cambio fijo `1 USD = 0.92 EUR`, el manejo de micro-costes de tokens LLM,
 * casos borde numéricos y el formato visual en Euros (`€`) y Dólares (`$`).
 */
describe('Utilidades de Moneda (currency.ts)', () => {
  /**
   * Comprueba que la constante de tipo de cambio oficial del Dashboard sea exactamente 0.92.
   */
  it('define la tasa fija de conversión USD_TO_EUR_RATE en 0.92', () => {
    expect(USD_TO_EUR_RATE).toBe(0.92);
  });

  /**
   * Verifica la conversión matemática exacta desde USD hacia EUR y USD para importes cero,
   * micro-costes de tokens (Flash $0.15/1M y Pro $1.25/1M) y valores mayores.
   */
  it('convierte importes de USD a EUR (tasa 0.92) y mantiene el importe intacto en USD (tasa 1.0)', () => {
    expect(convertFromUsd(0, 'EUR')).toBe(0);
    expect(convertFromUsd(0, 'USD')).toBe(0);

    expect(convertFromUsd(1.0, 'EUR')).toBeCloseTo(0.92, 6);
    expect(convertFromUsd(1.0, 'USD')).toBeCloseTo(1.0, 6);

    // Micro-costes típicos de peticiones LLM (ej. 1.000 tokens en Flash = 0.000150 USD)
    expect(convertFromUsd(0.00015, 'EUR')).toBeCloseTo(0.000138, 6);
    expect(convertFromUsd(0.00015, 'USD')).toBeCloseTo(0.00015, 6);

    // 1.000 tokens en Pro = 0.001250 USD -> 0.001150 EUR
    expect(convertFromUsd(0.00125, 'EUR')).toBeCloseTo(0.00115, 6);
    expect(convertFromUsd(1000, 'EUR')).toBeCloseTo(920, 6);
  });

  /**
   * Verifica que valores no finitos (`NaN`, `Infinity`) se traten de forma segura devolviendo `0`.
   */
  it('devuelve 0 de forma segura cuando recibe valores no finitos (NaN o Infinity)', () => {
    expect(convertFromUsd(Number.NaN, 'EUR')).toBe(0);
    expect(convertFromUsd(Number.POSITIVE_INFINITY, 'USD')).toBe(0);
    expect(formatCurrency(Number.NaN, 'EUR')).toBe('0,000000 €');
    expect(formatCurrency(Number.NaN, 'USD')).toBe('$0.000000');
  });

  /**
   * Comprueba el formato de cadena de `formatCurrency` a 6 decimales con coma decimal y sufijo `€`
   * para Euros, y con punto decimal y prefijo `$` para Dólares estadounidenses.
   */
  it('formatea importes con 6 decimales y el símbolo monetario correcto para EUR (€) y USD ($)', () => {
    expect(formatCurrency(0, 'EUR')).toBe('0,000000 €');
    expect(formatCurrency(0, 'USD')).toBe('$0.000000');

    expect(formatCurrency(0.55, 'EUR')).toBe('0,506000 €');
    expect(formatCurrency(0.55, 'USD')).toBe('$0.550000');

    expect(formatCurrency(0.00015, 'EUR')).toBe('0,000138 €');
    expect(formatCurrency(0.00015, 'USD')).toBe('$0.000150');

    expect(formatCurrency(1.25, 'EUR')).toBe('1,150000 €');
    expect(formatCurrency(1.25, 'USD')).toBe('$1.250000');
  });

  /**
   * Verifica la función inversa `convertToUsd` que convierte importes introducidos por el usuario
   * en la divisa activa (`'EUR'` dividiendo por `0.92`, o `'USD'` manteniendo el importe) a USD con 6 decimales.
   */
  it('convierte importes de EUR a USD dividiendo por 0.92 y mantiene USD intacto con 6 decimales', () => {
    expect(convertToUsd(0.92, 'EUR')).toBe(1);
    expect(convertToUsd(0.00092, 'EUR')).toBe(0.001);
    expect(convertToUsd(1.25, 'USD')).toBe(1.25);
    expect(convertToUsd(Number.NaN, 'EUR')).toBe(0);
  });

  /**
   * Verifica `calculateLogCostUsd` y `calculateBaselineProCostUsd` para peticiones individuales:
   * - `gemini-2.5-flash` a `$0.15 / 1M` tokens.
   * - `gemini-2.5-pro` a `$1.25 / 1M` tokens.
   * - Coste `0` cuando `cacheHit === true` o `tokensEstimated <= 0`.
   */
  it('calcula el coste individual en USD según el modelo asignado, coste 0 en Caché Hit y el coste base Pro', () => {
    // 100.000 tokens en Flash = $0.015000 USD
    expect(calculateLogCostUsd('gemini-2.5-flash', 100_000, false)).toBe(0.015);
    // 100.000 tokens en Pro = $0.125000 USD
    expect(calculateLogCostUsd('gemini-2.5-pro', 100_000, false)).toBe(0.125);
    // Caché Hit siempre devuelve 0 USD independientemente del modelo o tokens
    expect(calculateLogCostUsd('gemini-2.5-pro', 100_000, true)).toBe(0);
    expect(calculateLogCostUsd('gemini-2.5-flash', 0, false)).toBe(0);

    // Coste base Pro hipotético para 100.000 tokens = $0.125000 USD
    expect(calculateBaselineProCostUsd(100_000)).toBe(0.125);
    expect(calculateBaselineProCostUsd(0)).toBe(0);
  });

  /**
   * Verifica las tarifas multi-proveedor (`OpenAI` y `Anthropic Claude`), la clasificación
   * de modelos avanzados (`isAdvancedModel`) y el formateo de etiquetas (`formatProviderLabel`).
   */
  it('calcula tarifas multi-proveedor para OpenAI y Anthropic y clasifica modelos avanzados', () => {
    // OpenAI: gpt-4o-mini ($0.15/1M) y gpt-4o ($2.50/1M)
    expect(calculateLogCostUsd('gpt-4o-mini', 100_000, false)).toBe(0.015);
    expect(calculateLogCostUsd('gpt-4o', 100_000, false)).toBe(0.25);
    expect(calculateBaselineProCostUsd(100_000, 'gpt-4o-mini', 'OPENAI')).toBe(0.25);

    // Anthropic: claude-3-5-haiku ($0.80/1M) y claude-3-7-sonnet ($3.00/1M)
    expect(calculateLogCostUsd('claude-3-5-haiku', 100_000, false)).toBe(0.08);
    expect(calculateLogCostUsd('claude-3-7-sonnet', 100_000, false)).toBe(0.3);
    expect(calculateBaselineProCostUsd(100_000, 'claude-3-5-haiku', 'ANTHROPIC')).toBe(0.3);

    // Clasificación de modelos Pro vs Económicos
    expect(isAdvancedModel('gemini-2.5-pro')).toBe(true);
    expect(isAdvancedModel('gpt-4o')).toBe(true);
    expect(isAdvancedModel('claude-3-7-sonnet')).toBe(true);
    expect(isAdvancedModel('gemini-2.5-flash')).toBe(false);
    expect(isAdvancedModel('gpt-4o-mini')).toBe(false);
    expect(isAdvancedModel('claude-3-5-haiku')).toBe(false);

    // Etiquetas de proveedor
    expect(formatProviderLabel('OPENAI')).toBe('OpenAI');
    expect(formatProviderLabel('ANTHROPIC')).toBe('Anthropic Claude');
    expect(formatProviderLabel('GOOGLE_GEMINI')).toBe('Google Gemini');
  });
});
