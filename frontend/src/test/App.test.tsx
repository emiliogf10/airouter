import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import type { RouterResponse, RouterStatsResponse } from '../types/router';

/**
 * Datos simulados de estadísticas globales devueltos por `GET /api/v1/router/stats`.
 */
const MOCK_GLOBAL_STATS: RouterStatsResponse = {
  totalRequests: 2,
  totalTokensEstimated: 1000000,
  fastModelRequests: 1,
  proModelRequests: 1,
  fastModelTokens: 500000,
  proModelTokens: 500000,
  estimatedActualCostUsd: 0.7,
  estimatedBaselineProCostUsd: 1.25,
  estimatedSavingsUsd: 0.55,
  savingsPercentage: 44.0,
};

/**
 * Datos simulados de historial de peticiones devueltos por `GET /api/v1/router/logs`.
 */
const MOCK_INITIAL_LOGS: RouterResponse[] = [
  {
    id: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    userId: 'demo-user',
    promptContent: 'Hola, ¿qué día es hoy?',
    modelRouted: 'gemini-2.5-flash',
    tokensEstimated: 6,
    simulatedResponse: '[Simulación AI Cost Router] Petición procesada exitosamente.',
    createdAt: '2026-10-20T22:30:00',
  },
  {
    id: 'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    userId: 'architect-user',
    promptContent: 'Necesito refactorizar la arquitectura de microservicios',
    modelRouted: 'gemini-2.5-pro',
    tokensEstimated: 14,
    simulatedResponse: '[Simulación AI Cost Router] Petición procesada por gemini-2.5-pro.',
    createdAt: '2026-10-20T22:35:00',
  },
];

/**
 * Suite de pruebas de integración para el Dashboard Frontend de **AI Cost Router** (`App.tsx`).
 * Verifica el selector de divisa EUR/USD (con EUR por defecto), las tarjetas KPI, el simulador
 * de prompts (`PromptPlayground`), el manejo de errores HTTP 400 y HTTP 429 (`Retry-After`),
 * el filtrado reactivo por usuario y la tabla de historial.
 */
describe('AI Cost Router Dashboard - Pruebas de Integración', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  /**
   * Verifica que al cargar el Dashboard la divisa por defecto sea Euros (`EUR €`) y que al
   * alternar entre `USD $` y `EUR €` todas las tarjetas KPI actualicen sus importes dinámicamente.
   */
  it('renderiza el Dashboard con EUR (€) activo por defecto y permite conmutar entre USD ($) y EUR (€)', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.includes('/stats')) {
        return new Response(JSON.stringify(MOCK_GLOBAL_STATS), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }
      return new Response(JSON.stringify(MOCK_INITIAL_LOGS), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    render(<App />);

    expect(screen.getByText('AI Cost Router')).toBeInTheDocument();

    const eurButton = screen.getByTestId('currency-toggle-eur');
    const usdButton = screen.getByTestId('currency-toggle-usd');

    // Por defecto debe iniciar en EUR (€)
    expect(eurButton).toHaveAttribute('aria-pressed', 'true');
    expect(usdButton).toHaveAttribute('aria-pressed', 'false');

    await waitFor(() => {
      // 0.55 USD * 0.92 = 0,506000 € | 0.70 USD * 0.92 = 0,644000 € | 1.25 USD * 0.92 = 1,150000 €
      expect(screen.getByTestId('savings-value')).toHaveTextContent('0,506000 €');
      expect(screen.getByTestId('actual-cost-value')).toHaveTextContent('0,644000 €');
      expect(screen.getByTestId('baseline-cost-value')).toHaveTextContent('1,150000 €');
      expect(screen.getByTestId('savings-percentage-badge')).toHaveTextContent('44.0%');
    });

    // Cambiar a USD ($)
    await userEvent.click(usdButton);

    expect(usdButton).toHaveAttribute('aria-pressed', 'true');
    expect(eurButton).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByTestId('savings-value')).toHaveTextContent('$0.550000');
    expect(screen.getByTestId('actual-cost-value')).toHaveTextContent('$0.700000');
    expect(screen.getByTestId('baseline-cost-value')).toHaveTextContent('$1.250000');

    // Volver a EUR (€)
    await userEvent.click(eurButton);

    expect(eurButton).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByTestId('savings-value')).toHaveTextContent('0,506000 €');
    expect(screen.getByTestId('actual-cost-value')).toHaveTextContent('0,644000 €');
  });

  /**
   * Verifica que el simulador (`PromptPlayground`) permita enviar un prompt, muestre la tarjeta
   * con el modelo asignado y los tokens estimados, y refresque automáticamente las métricas del Dashboard.
   */
  it('envía un prompt desde PromptPlayground, muestra el resultado de enrutamiento y refresca los KPIs', async () => {
    let statsCallCount = 0;

    const updatedStats: RouterStatsResponse = {
      ...MOCK_GLOBAL_STATS,
      totalRequests: 3,
      proModelRequests: 2,
      totalTokensEstimated: 1000025,
    };

    const routedResponse: RouterResponse = {
      id: 'c3d4e5f6-a7b8-9012-cdef-123456789012',
      userId: 'demo-user',
      promptContent: 'Necesito optimizar la arquitectura del backend',
      modelRouted: 'gemini-2.5-pro',
      tokensEstimated: 12,
      simulatedResponse:
        "[Simulación AI Cost Router] Petición procesada exitosamente por el modelo 'gemini-2.5-pro' (12 tokens estimados).",
      createdAt: '2026-10-20T22:40:00',
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url.includes('/prompt') && init?.method === 'POST') {
        return new Response(JSON.stringify(routedResponse), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      if (url.includes('/stats')) {
        statsCallCount += 1;
        const data = statsCallCount === 1 ? MOCK_GLOBAL_STATS : updatedStats;
        return new Response(JSON.stringify(data), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      return new Response(JSON.stringify([routedResponse, ...MOCK_INITIAL_LOGS]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('kpi-total-requests')).toHaveTextContent('2');
    });

    // Cargar ejemplo Pro o escribir en el textarea y enviar
    const promptTextarea = screen.getByLabelText(/Contenido del Prompt/i);
    await userEvent.clear(promptTextarea);
    await userEvent.type(promptTextarea, 'Necesito optimizar la arquitectura del backend');

    const submitButton = screen.getByTestId('submit-prompt-button');
    await userEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByTestId('playground-result-card')).toBeInTheDocument();
      expect(screen.getByTestId('routed-model-badge')).toHaveTextContent('gemini-2.5-pro');
      expect(screen.getByTestId('routed-tokens-badge')).toHaveTextContent('12 tokens');
      expect(screen.getByTestId('kpi-total-requests')).toHaveTextContent('3');
    });
  });

  /**
   * Verifica el manejo de errores en `PromptPlayground` tanto para errores de validación (HTTP 400)
   * como para bloqueos del filtro de seguridad por exceso de cuota (HTTP 429 con `Retry-After`).
   */
  it('muestra alertas detalladas ante errores HTTP 400 Bad Request y HTTP 429 Too Many Requests con Retry-After', async () => {
    let shouldReturn429 = false;

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url.includes('/prompt') && init?.method === 'POST') {
        if (shouldReturn429) {
          return new Response(
            JSON.stringify({
              message: 'Límite de peticiones excedido. Por favor, inténtelo de nuevo más tarde.',
              timestamp: '2026-10-20T22:45:00',
            }),
            {
              status: 429,
              headers: {
                'Content-Type': 'application/json',
                'Retry-After': '42',
              },
            },
          );
        }

        return new Response(
          JSON.stringify({
            message: 'El identificador de usuario (userId) no puede superar los 255 caracteres.',
            timestamp: '2026-10-20T22:44:00',
          }),
          {
            status: 400,
            headers: { 'Content-Type': 'application/json' },
          },
        );
      }

      if (url.includes('/stats')) {
        return new Response(JSON.stringify(MOCK_GLOBAL_STATS), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      return new Response(JSON.stringify(MOCK_INITIAL_LOGS), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('request-logs-table')).toBeInTheDocument();
    });

    // 1. Probar error HTTP 400 devuelto por el backend
    await userEvent.click(screen.getByTestId('preset-flash-button'));
    await userEvent.click(screen.getByTestId('submit-prompt-button'));

    await waitFor(() => {
      const alertBox = screen.getByTestId('playground-error-alert');
      expect(alertBox).toBeInTheDocument();
      expect(alertBox).toHaveTextContent(
        'El identificador de usuario (userId) no puede superar los 255 caracteres.',
      );
    });

    // 2. Probar error HTTP 429 Too Many Requests con cabecera Retry-After: 42
    shouldReturn429 = true;
    await userEvent.click(screen.getByTestId('submit-prompt-button'));

    await waitFor(() => {
      const alertBox = screen.getByTestId('playground-error-alert');
      expect(alertBox).toHaveTextContent(
        'Límite de Peticiones Excedido (HTTP 429 Too Many Requests)',
      );
      expect(screen.getByTestId('retry-after-indicator')).toHaveTextContent(
        'Tiempo de espera (Retry-After): 42 segundos',
      );
    });
  });

  /**
   * Verifica que el buscador de usuario (`user-filter-input`) invoque a los endpoints `/stats` y `/logs`
   * con el parámetro `?userId=...` y actualice la tabla de historial en tiempo real.
   */
  it('filtra las estadísticas y la tabla de historial al introducir un userId en la cabecera', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url.includes('userId=alice')) {
        if (url.includes('/stats')) {
          return new Response(
            JSON.stringify({
              ...MOCK_GLOBAL_STATS,
              totalRequests: 1,
              fastModelRequests: 1,
              proModelRequests: 0,
              savingsPercentage: 88.0,
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          );
        }
        return new Response(
          JSON.stringify([
            {
              id: 'f1e2d3c4-b5a6-7890-1234-567890abcdef',
              userId: 'alice',
              promptContent: 'Consulta filtrada exclusiva de Alice',
              modelRouted: 'gemini-2.5-flash',
              tokensEstimated: 9,
              simulatedResponse: 'Respuesta para Alice',
              createdAt: '2026-10-20T22:50:00',
            },
          ]),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }

      if (url.includes('/stats')) {
        return new Response(JSON.stringify(MOCK_GLOBAL_STATS), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      return new Response(JSON.stringify(MOCK_INITIAL_LOGS), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('logs-count-badge')).toHaveTextContent('2 registros');
    });

    const filterInput = screen.getByTestId('user-filter-input');
    await userEvent.type(filterInput, 'alice');

    await waitFor(() => {
      expect(fetchSpy).toHaveBeenCalledWith('/api/v1/router/stats?userId=alice');
      expect(fetchSpy).toHaveBeenCalledWith('/api/v1/router/logs?userId=alice');
      expect(screen.getByTestId('logs-count-badge')).toHaveTextContent('1 registro');
      expect(screen.getByText('Consulta filtrada exclusiva de Alice')).toBeInTheDocument();
      expect(screen.getByTestId('savings-percentage-badge')).toHaveTextContent('88.0%');
    });
  });
});
