import { describe, expect, it } from 'vitest';
import { convertFromUsd, formatCurrency, USD_TO_EUR_RATE } from '../utils/currency';

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
});
