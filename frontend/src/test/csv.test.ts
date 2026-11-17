import { describe, expect, it } from 'vitest';
import type { RouterResponse } from '../types/router';
import { buildLogsCsvContent, sanitizeCsvCell } from '../utils/csv';

/**
 * Suite de pruebas unitarias y de seguridad (`security-audit`) para la utilidad de exportación
 * CSV (`csv.ts`), verificando el escape RFC 4180, la mitigación contra **CSV Formula Injection (DDE)**
 * y la conversión monetaria por fila en `EUR` y `USD`.
 */
describe('Exportación CSV Segura (csv.ts)', () => {
  /**
   * Comprueba que `sanitizeCsvCell` envuelva textos normales entre comillas dobles
   * y duplique las comillas internas conforme a RFC 4180.
   */
  it('envuelve textos normales entre comillas dobles y escapa comillas internas como ""', () => {
    expect(sanitizeCsvCell('demo-user')).toBe('"demo-user"');
    expect(sanitizeCsvCell('Prompt con "comillas" internas')).toBe(
      '"Prompt con ""comillas"" internas"',
    );
    expect(sanitizeCsvCell(null)).toBe('""');
  });

  /**
   * Verifica la protección de seguridad (`security-audit` — CSV Formula Injection):
   * cualquier celda cuyo texto comience (directamente o tras espacios) con `=`, `+`, `-`, `@`, `\t` o `\r`
   * debe recibir el prefijo `'` para impedir la ejecución de comandos o fórmulas en hojas de cálculo.
   */
  it('neutraliza intentos de CSV Formula Injection prefijando comilla simple (\') ante =, +, -, @, \\t y \\r', () => {
    expect(sanitizeCsvCell('=1+1')).toBe('"\'=1+1"');
    expect(sanitizeCsvCell(' =CMD|\' /C calc\'!A0')).toBe('"\' =CMD|\' /C calc\'!A0"');
    expect(sanitizeCsvCell('+SUM(A1:A10)')).toBe('"\'+SUM(A1:A10)"');
    expect(sanitizeCsvCell('-2+3')).toBe('"\'-2+3"');
    expect(sanitizeCsvCell('@IMPORTXML("http://evil.local")')).toBe(
      '"\'@IMPORTXML(""http://evil.local"")"',
    );
    expect(sanitizeCsvCell('\t=HYPERLINK("http://evil.local")')).toBe(
      '"\'\t=HYPERLINK(""http://evil.local"")"',
    );
    expect(sanitizeCsvCell('\r=1+2')).toBe('"\'\r=1+2"');
  });

  /**
   * Verifica que `buildLogsCsvContent` genere las cabeceras dinámicas según la divisa activa (`EUR` o `USD`)
   * y calcule los importes individuales de coste facturado y ahorro de cada petición.
   */
  it('construye el contenido CSV con cabeceras e importes calculados en EUR y USD', () => {
    const sampleLogs: RouterResponse[] = [
      {
        id: 'log-flash-100k',
        userId: 'alice',
        promptContent: 'Resumen sencillo',
        modelRouted: 'gemini-2.5-flash',
        tokensEstimated: 100_000,
        simulatedResponse: 'Respuesta Flash',
        createdAt: '2026-10-20T22:30:00',
        cacheHit: false,
      },
      {
        id: 'log-cached-100k',
        userId: 'bob',
        promptContent: '=CMD|inyeccion',
        modelRouted: 'gemini-2.5-pro',
        tokensEstimated: 100_000,
        simulatedResponse: 'Respuesta desde caché',
        createdAt: '2026-10-20T22:35:00',
        cacheHit: true,
      },
    ];

    // En USD:
    // Fila 1 (Flash 100k tokens): Coste = 0.015000 USD, Base Pro = 0.125000 USD -> Ahorro = 0.110000 USD
    // Fila 2 (Cache Hit 100k tokens): Coste = 0.000000 USD, Base Pro = 0.125000 USD -> Ahorro = 0.125000 USD
    const csvUsd = buildLogsCsvContent(sampleLogs, 'USD');
    const linesUsd = csvUsd.split('\r\n');
    expect(linesUsd).toHaveLength(3);
    expect(linesUsd[0]).toContain('"Coste_Facturado_USD","Ahorro_USD"');
    expect(linesUsd[1]).toContain('"log-flash-100k"');
    expect(linesUsd[1]).toContain('"NO","100000","0.015000","0.110000"');
    expect(linesUsd[2]).toContain('"SI","100000","0.000000","0.125000"');
    expect(linesUsd[2]).toContain('"\'=CMD|inyeccion"');

    // En EUR (tasa 0.92):
    // Fila 1: Coste = 0.015 * 0.92 = 0.013800 EUR, Ahorro = 0.11 * 0.92 = 0.101200 EUR
    const csvEur = buildLogsCsvContent(sampleLogs, 'EUR');
    const linesEur = csvEur.split('\r\n');
    expect(linesEur[0]).toContain('"Coste_Facturado_EUR","Ahorro_EUR"');
    expect(linesEur[1]).toContain('"0.013800","0.101200"');
  });
});
