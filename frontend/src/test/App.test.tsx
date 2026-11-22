import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import { SIMPLE_PROMPT_EXAMPLE } from '../components/PromptPlayground';
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
 * Datos simulados de estadísticas vacías cuando aún no se han procesado peticiones.
 */
const MOCK_EMPTY_STATS: RouterStatsResponse = {
  totalRequests: 0,
  totalTokensEstimated: 0,
  fastModelRequests: 0,
  proModelRequests: 0,
  fastModelTokens: 0,
  proModelTokens: 0,
  estimatedActualCostUsd: 0,
  estimatedBaselineProCostUsd: 0,
  estimatedSavingsUsd: 0,
  savingsPercentage: 0,
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
 * Verifica el rediseño minimalista estilo Vercel / shadcn/ui: Skeleton Loaders, estados vacíos
 * con CTA interactivo, selector de divisa EUR/USD (con EUR por defecto), tarjetas `MetricCard`,
 * simulador `PromptPlayground`, manejo de errores HTTP 400 y HTTP 429 (`Retry-After`),
 * filtrado reactivo por usuario y tabla `RecentLogsTable`.
 */
describe('AI Cost Router Dashboard - Pruebas de Integración', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  /**
   * Verifica que mientras las peticiones iniciales a `/stats` y `/logs` están en curso,
   * se muestren los Skeleton Loaders elegantes en tarjetas KPI, gráfica y tabla,
   * y desaparezcan al completarse la carga.
   */
  it('muestra Skeleton Loaders durante la carga inicial y los reemplaza por los datos reales al resolver', async () => {
    let resolveStats!: (value: Response) => void;
    let resolveLogs!: (value: Response) => void;

    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.includes('/stats')) {
        return new Promise<Response>((resolve) => {
          resolveStats = resolve;
        });
      }
      return new Promise<Response>((resolve) => {
        resolveLogs = resolve;
      });
    });

    render(<App />);

    // Mientras las promesas están pendientes, deben visualizarse los Skeleton Loaders
    expect(screen.getByTestId('kpi-total-requests-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('kpi-total-tokens-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('kpi-actual-cost-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('kpi-savings-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('chart-skeleton-loader')).toBeInTheDocument();
    expect(screen.getByTestId('logs-skeleton-loader')).toBeInTheDocument();

    // Resolver las respuestas del servidor
    resolveStats(
      new Response(JSON.stringify(MOCK_GLOBAL_STATS), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    resolveLogs(
      new Response(JSON.stringify(MOCK_INITIAL_LOGS), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await waitFor(() => {
      expect(screen.queryByTestId('kpi-total-requests-skeleton')).not.toBeInTheDocument();
      expect(screen.queryByTestId('logs-skeleton-loader')).not.toBeInTheDocument();
      expect(screen.queryByTestId('chart-skeleton-loader')).not.toBeInTheDocument();
      expect(screen.getByTestId('model-distribution-chart')).toBeInTheDocument();
      expect(screen.getByTestId('request-logs-table')).toBeInTheDocument();
    });
  });

  /**
   * Verifica que cuando el backend devuelve 0 peticiones y una lista de logs vacía,
   * se presenten los estados vacíos (`EmptyState`) en la gráfica y en la tabla, y que el
   * botón CTA "Probar un prompt de ejemplo" rellene automáticamente el simulador.
   */
  it('renderiza los estados vacíos cuando no hay registros y rellena el simulador al pulsar el CTA rápido', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.includes('/stats')) {
        return new Response(JSON.stringify(MOCK_EMPTY_STATS), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }
      return new Response(JSON.stringify([]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('logs-empty-state')).toBeInTheDocument();
      expect(screen.getByTestId('chart-empty-state')).toBeInTheDocument();
      expect(screen.getByTestId('empty-logs-message')).toBeInTheDocument();
    });

    const quickTryButton = screen.getByTestId('quick-try-prompt-button');
    await userEvent.click(quickTryButton);

    const promptTextarea = screen.getByTestId('playground-prompt-textarea');
    expect(promptTextarea).toHaveValue(SIMPLE_PROMPT_EXAMPLE);
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
      expect(screen.getByTestId('routed-execution-mode-badge')).toHaveTextContent('Modo Simulación');
      expect(screen.getByTestId('routed-tokens-badge')).toHaveTextContent('12 tokens');
      expect(screen.getByTestId('kpi-total-requests')).toHaveTextContent('3');
    });
  });

  /**
   * Verifica que el badge `routed-execution-mode-badge` muestre "Gemini API Real" cuando el usuario
   * introduce una clave en `playground-apikey-input` (enviándola en la cabecera `X-API-Key`) y el backend
   * devuelve una respuesta real generada por Google Gemini API.
   */
  it('muestra el badge Gemini API Real y envía la cabecera X-API-Key cuando se provee una clave BYOK', async () => {
    let capturedHeaders: HeadersInit | undefined;

    const realGeminiResponse: RouterResponse = {
      id: 'd4e5f6a7-b8c9-0123-def0-234567890123',
      userId: 'demo-user',
      promptContent: 'Explica qué es un API Gateway de IA',
      modelRouted: 'gemini-2.5-flash',
      tokensEstimated: 9,
      simulatedResponse:
        'Un API Gateway de IA centraliza el enrutamiento, control de costes y seguridad entre múltiples modelos LLM.',
      createdAt: '2026-10-20T22:42:00',
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url.includes('/prompt') && init?.method === 'POST') {
        capturedHeaders = init.headers;
        return new Response(JSON.stringify(realGeminiResponse), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      if (url.includes('/stats')) {
        return new Response(JSON.stringify(MOCK_GLOBAL_STATS), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      return new Response(JSON.stringify([realGeminiResponse, ...MOCK_INITIAL_LOGS]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('kpi-total-requests')).toHaveTextContent('2');
    });

    const apiKeyInput = screen.getByTestId('playground-apikey-input');
    await userEvent.type(apiKeyInput, 'AIzaSyTestByokKey123456');

    const promptTextarea = screen.getByLabelText(/Contenido del Prompt/i);
    await userEvent.clear(promptTextarea);
    await userEvent.type(promptTextarea, 'Explica qué es un API Gateway de IA');

    await userEvent.click(screen.getByTestId('submit-prompt-button'));

    await waitFor(() => {
      expect(screen.getByTestId('playground-result-card')).toBeInTheDocument();
      expect(screen.getByTestId('routed-execution-mode-badge')).toHaveTextContent('Gemini API Real');
      expect(screen.getByTestId('routed-model-badge')).toHaveTextContent('gemini-2.5-flash');
    });

    expect(capturedHeaders).toMatchObject({
      'Content-Type': 'application/json',
      'X-API-Key': 'AIzaSyTestByokKey123456',
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

  /**
   * Verifica la interacción con `BudgetControlCard` (`data-testid="budget-control-card"`):
   * visualización del estado inicial ilimitado, introducción de un nuevo límite en
   * `data-testid="budget-limit-input"` y guardado con `data-testid="budget-save-button"`
   * invocando `PUT /api/v1/router/budget/demo-user` y actualizando `data-testid="budget-status-badge"`.
   */
  it('permite configurar un límite presupuestario FinOps en BudgetControlCard y actualiza el estado del usuario', async () => {
    let putUrlCalled = '';
    let putBodyParsed: { maxBudgetUsd: number | null; autoDowngradeEnabled: boolean } | null = null;

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url.includes('/budget/') && init?.method === 'PUT') {
        putUrlCalled = url;
        putBodyParsed = JSON.parse(String(init.body)) as {
          maxBudgetUsd: number | null;
          autoDowngradeEnabled: boolean;
        };
        return new Response(
          JSON.stringify({
            userId: 'demo-user',
            maxBudgetUsd: 0.001,
            currentSpendUsd: 0.00085,
            remainingBudgetUsd: 0.00015,
            usagePercentage: 85.0,
            autoDowngradeEnabled: true,
            status: 'WARNING_80_DOWNGRADE',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }

      if (url.includes('/budget/')) {
        return new Response(
          JSON.stringify({
            userId: 'demo-user',
            maxBudgetUsd: null,
            currentSpendUsd: 0.00014,
            remainingBudgetUsd: null,
            usagePercentage: 0.0,
            autoDowngradeEnabled: true,
            status: 'UNLIMITED',
          }),
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
      expect(screen.getByTestId('budget-control-card')).toBeInTheDocument();
      expect(screen.getByTestId('budget-status-badge')).toHaveTextContent('Sin límite (Ilimitado)');
    });

    const limitInput = screen.getByTestId('budget-limit-input');
    await userEvent.clear(limitInput);
    await userEvent.type(limitInput, '0.00092');

    await userEvent.click(screen.getByTestId('budget-save-button'));

    await waitFor(() => {
      expect(putUrlCalled).toBe('/api/v1/router/budget/demo-user');
      expect(putBodyParsed).toEqual({
        maxBudgetUsd: 0.001,
        autoDowngradeEnabled: true,
      });
      expect(screen.getByTestId('budget-status-badge')).toHaveTextContent(
        'Modo Ahorro Forzado (>=80% → Flash)',
      );
      expect(screen.getByTestId('budget-warning-banner')).toBeInTheDocument();
    });
  });

  /**
   * Verifica que cuando `POST /api/v1/router/prompt` responde con HTTP 402 Payment Required,
   * `PromptPlayground` muestra la alerta `data-testid="playground-error-alert"` indicando
   * `"Cortafuegos Presupuestario Activado (HTTP 402 Payment Required)"`.
   */
  it('muestra la alerta del Cortafuegos Presupuestario cuando POST /prompt devuelve HTTP 402 Payment Required', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url.includes('/prompt') && init?.method === 'POST') {
        return new Response(
          JSON.stringify({
            message:
              "Cortafuegos presupuestario activado: el usuario 'demo-user' ha alcanzado el 100% de su presupuesto máximo ($0.001000 USD consumidos de $0.001000 USD).",
            timestamp: '2026-10-20T23:00:00',
          }),
          {
            status: 402,
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

    await userEvent.click(screen.getByTestId('preset-pro-button'));
    await userEvent.click(screen.getByTestId('submit-prompt-button'));

    await waitFor(() => {
      const alertBox = screen.getByTestId('playground-error-alert');
      expect(alertBox).toBeInTheDocument();
      expect(alertBox).toHaveTextContent(
        'Cortafuegos Presupuestario Activado (HTTP 402 Payment Required)',
      );
      expect(alertBox).toHaveTextContent(
        "Cortafuegos presupuestario activado: el usuario 'demo-user' ha alcanzado el 100% de su presupuesto máximo",
      );
    });
  });

  /**
   * Verifica la visualización de la Caché Semántica de Coste Cero (`Caché Hit (0 €)`):
   * comprueba que cuando `/api/v1/router/prompt` y `/api/v1/router/logs` devuelven un registro con
   * `cacheHit: true` y `/api/v1/router/stats` incluye `cacheHits: 1`, se renderizan:
   * - El badge `data-testid="routed-cache-hit-badge"` en `PromptPlayground`.
   * - El badge `data-testid="log-cache-hit-..."` en `RecentLogsTable`.
   * - El contador `data-testid="kpi-cache-hits-count"` (`Caché Hit (0 €): 1`) en `StatsCards`.
   */
  it('renderiza los badges Caché Hit (0 €) en el simulador, tabla de historial y tarjeta de ahorro cuando cacheHit es true', async () => {
    const cachedResponse: RouterResponse = {
      id: 'cache-hit-uuid-9999',
      userId: 'demo-user',
      promptContent: '¿Qué día es hoy?',
      modelRouted: 'gemini-2.5-flash',
      tokensEstimated: 5,
      simulatedResponse:
        "[Simulación AI Cost Router] Petición procesada exitosamente por el modelo 'gemini-2.5-flash' (5 tokens estimados).",
      createdAt: '2026-10-20T23:10:00',
      cacheHit: true,
    };

    const statsWithCacheHit: RouterStatsResponse = {
      ...MOCK_GLOBAL_STATS,
      totalRequests: 3,
      cacheHits: 1,
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url.includes('/prompt') && init?.method === 'POST') {
        return new Response(JSON.stringify(cachedResponse), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      if (url.includes('/stats')) {
        return new Response(JSON.stringify(statsWithCacheHit), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      return new Response(JSON.stringify([cachedResponse, ...MOCK_INITIAL_LOGS]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('kpi-cache-hits-count')).toHaveTextContent('Caché Hit (0 €): 1');
      expect(screen.getByTestId('log-cache-hit-cache-hit-uuid-9999')).toHaveTextContent(
        'Caché Hit (0 €)',
      );
    });

    const promptTextarea = screen.getByLabelText(/Contenido del Prompt/i);
    await userEvent.clear(promptTextarea);
    await userEvent.type(promptTextarea, '¿Qué día es hoy?');
    await userEvent.click(screen.getByTestId('submit-prompt-button'));

    await waitFor(() => {
      expect(screen.getByTestId('playground-result-card')).toBeInTheDocument();
      expect(screen.getByTestId('routed-cache-hit-badge')).toHaveTextContent('Caché Hit (0 €)');
    });
  });

  /**
   * Verifica el visor modal `LogInspectorDrawer` (`modern-web-guidance` — `dialog` y `copy-button`):
   * - Al pulsar `data-testid="inspect-log-btn-..."` en una fila de `RecentLogsTable`, se abre
   *   `data-testid="log-inspector-drawer"` mostrando el prompt íntegro (`inspector-full-prompt`),
   *   la respuesta íntegra (`inspector-full-response`), el coste individual (`inspector-actual-cost`)
   *   y el ahorro individual (`inspector-saved-cost`).
   * - Al pulsar `data-testid="inspector-copy-prompt-btn"`, se copia el prompt al portapapeles y la
   *   región viva externa `<span role="status" data-testid="inspector-copy-prompt-status">` anuncia `"Copiado"`.
   * - Al pulsar `data-testid="inspector-close-button"`, se cierra el visor modal.
   */
  it('abre el visor modal LogInspectorDrawer al inspeccionar un registro, copia el prompt anunciando "Copiado" y permite cerrarlo', async () => {
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

    const writeTextMock = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: writeTextMock },
      configurable: true,
    });

    render(<App />);

    const inspectButton = await screen.findByTestId(
      'inspect-log-btn-a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    );
    expect(screen.getByTestId('export-csv-button')).toBeEnabled();

    await userEvent.click(inspectButton);

    const dialog = screen.getByTestId('log-inspector-drawer');
    expect(dialog).toHaveAttribute('open');
    expect(screen.getByTestId('inspector-full-prompt')).toHaveTextContent(
      'Hola, ¿qué día es hoy?',
    );
    expect(screen.getByTestId('inspector-full-response')).toHaveTextContent(
      '[Simulación AI Cost Router] Petición procesada exitosamente.',
    );
    expect(screen.getByTestId('inspector-actual-cost')).toHaveTextContent('0,000001 €');
    expect(screen.getByTestId('inspector-saved-cost')).toHaveTextContent('0,000006 €');

    // Copiar prompt y verificar anuncio en <span role="status">
    await userEvent.click(screen.getByTestId('inspector-copy-prompt-btn'));

    await waitFor(() => {
      expect(writeTextMock).toHaveBeenCalledWith('Hola, ¿qué día es hoy?');
      expect(screen.getByTestId('inspector-copy-prompt-status')).toHaveTextContent('Copiado');
    });

    // Cerrar el visor modal
    await userEvent.click(screen.getByTestId('inspector-close-button'));

    await waitFor(() => {
      expect(dialog).not.toHaveAttribute('open');
    });
  });

  /**
   * Verifica el conmutador de Tema Claro/Oscuro (`data-testid="theme-toggle-button"`):
   * comprueba que al hacer clic alterna el atributo `data-theme` en `document.documentElement`
   * y la etiqueta `<meta name="color-scheme">` entre `'dark'` y `'light'`.
   */
  it('conmuta entre Tema Oscuro (dark) y Tema Claro (light) al pulsar theme-toggle-button', async () => {
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

    const themeButton = await screen.findByTestId('theme-toggle-button');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.querySelector('meta[name="color-scheme"]')?.getAttribute('content')).toBe(
      'dark',
    );

    await userEvent.click(themeButton);

    await waitFor(() => {
      expect(document.documentElement.getAttribute('data-theme')).toBe('light');
      expect(document.querySelector('meta[name="color-scheme"]')?.getAttribute('content')).toBe(
        'light',
      );
    });

    await userEvent.click(themeButton);

    await waitFor(() => {
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
      expect(document.querySelector('meta[name="color-scheme"]')?.getAttribute('content')).toBe(
        'dark',
      );
    });
  });

  /**
   * Verifica el gestor de Claves API (`ApiKeyManagerCard`, `data-testid="api-key-manager-card"`):
   * - Creación de una nueva clave introduciendo un nombre en `data-testid="apikey-name-input"` y pulsando
   *   `data-testid="apikey-create-button"`, mostrando el banner de visualización única (`data-testid="newly-created-apikey-banner"`)
   *   con el `rawApiKey` (`data-testid="newly-created-apikey-raw"`).
   * - Inyección rápida en el simulador al pulsar `data-testid="use-apikey-in-playground-button"`, rellenando
   *   automáticamente `data-testid="playground-apikey-input"`.
   * - Alerta `data-testid="playground-error-alert"` con `"Clave API Inválida o Revocada (HTTP 401 Unauthorized)"`
   *   cuando `POST /api/v1/router/prompt` responde con HTTP 401.
   */
  it('permite crear una clave API en ApiKeyManagerCard, inyectarla en el simulador y muestra alerta ante HTTP 401 Unauthorized', async () => {
    const generatedRawKey = 'air_live_9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c';

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url.includes('/keys') && init?.method === 'POST') {
        return new Response(
          JSON.stringify({
            id: 'key-uuid-1234',
            userId: 'demo-user',
            keyName: 'Producción App Web',
            keyPrefix: 'air_live_9f8e...',
            rawApiKey: generatedRawKey,
            active: true,
            createdAt: '2026-10-20T23:20:00',
          }),
          { status: 201, headers: { 'Content-Type': 'application/json' } },
        );
      }

      if (url.includes('/keys/')) {
        return new Response(
          JSON.stringify([
            {
              id: 'key-uuid-1234',
              userId: 'demo-user',
              keyName: 'Producción App Web',
              keyPrefix: 'air_live_9f8e...',
              active: true,
              createdAt: '2026-10-20T23:20:00',
            },
          ]),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }

      if (url.includes('/prompt') && init?.method === 'POST') {
        return new Response(
          JSON.stringify({
            message:
              "La clave API proporcionada ('air_live_9f8e...') es inválida o ha sido revocada.",
            timestamp: '2026-10-20T23:21:00',
          }),
          { status: 401, headers: { 'Content-Type': 'application/json' } },
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
      expect(screen.getByTestId('api-key-manager-card')).toBeInTheDocument();
    });

    // 1. Crear una nueva clave API
    const nameInput = screen.getByTestId('apikey-name-input');
    await userEvent.type(nameInput, 'Producción App Web');
    await userEvent.click(screen.getByTestId('apikey-create-button'));

    await waitFor(() => {
      expect(screen.getByTestId('newly-created-apikey-banner')).toBeInTheDocument();
      expect(screen.getByTestId('newly-created-apikey-raw')).toHaveTextContent(generatedRawKey);
    });

    // 2. Pulsar "Usar en Simulador" y verificar que rellena playground-apikey-input
    await userEvent.click(screen.getByTestId('use-apikey-in-playground-button'));
    expect(screen.getByTestId('playground-apikey-input')).toHaveValue(generatedRawKey);

    // 3. Enviar prompt y verificar alerta HTTP 401 Unauthorized
    await userEvent.click(screen.getByTestId('preset-flash-button'));
    await userEvent.click(screen.getByTestId('submit-prompt-button'));

    await waitFor(() => {
      const alertBox = screen.getByTestId('playground-error-alert');
      expect(alertBox).toBeInTheDocument();
      expect(alertBox).toHaveTextContent(
        'Clave API Inválida o Revocada (HTTP 401 Unauthorized)',
      );
      expect(alertBox).toHaveTextContent(
        "La clave API proporcionada ('air_live_9f8e...') es inválida o ha sido revocada.",
      );
    });
  });

  /**
   * Verifica la Billetera Prepago y Pasarela de Pagos (`WalletBillingCard`, `data-testid="wallet-billing-card"`):
   * - Visualización del estado inicial (`Modo Pospago (Sin prepago)` en `data-testid="wallet-status-badge"`).
   * - Selección de pasarela `data-testid="payment-method-paypal"` y recarga pulsando `data-testid="wallet-topup-5"`.
   * - Invocación de `POST /api/v1/router/wallet/demo-user/topup` y actualización de `data-testid="wallet-balance-value"`,
   *   `data-testid="wallet-status-badge"` (`Prepago Activo (Con Saldo)`) y `data-testid="wallet-last-method"` (`PayPal`).
   */
  it('permite recargar créditos en WalletBillingCard seleccionando PayPal y actualiza el saldo y estado prepago', async () => {
    let topUpUrlCalled = '';
    let topUpBodyParsed: { amountUsd: number; paymentMethod: string } | null = null;

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url.includes('/wallet/') && url.includes('/topup') && init?.method === 'POST') {
        topUpUrlCalled = url;
        topUpBodyParsed = JSON.parse(String(init.body)) as {
          amountUsd: number;
          paymentMethod: string;
        };
        return new Response(
          JSON.stringify({
            userId: 'demo-user',
            balanceUsd: 5.434783,
            totalToppedUpUsd: 5.434783,
            walletEnforced: true,
            lastPaymentMethod: 'PAYPAL',
            updatedAt: '2026-10-20T23:30:00',
            status: 'PREPAID_ACTIVE',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }

      if (url.includes('/wallet/')) {
        return new Response(
          JSON.stringify({
            userId: 'demo-user',
            balanceUsd: 0,
            totalToppedUpUsd: 0,
            walletEnforced: false,
            lastPaymentMethod: null,
            updatedAt: null,
            status: 'POSTPAID_UNLIMITED',
          }),
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
      expect(screen.getByTestId('wallet-billing-card')).toBeInTheDocument();
      expect(screen.getByTestId('wallet-status-badge')).toHaveTextContent(
        'Modo Pospago (Sin prepago)',
      );
      expect(screen.getByTestId('wallet-balance-value')).toHaveTextContent('0,000000 €');
    });

    // Seleccionar PayPal y pulsar recarga rápida de + 5,00 €
    await userEvent.click(screen.getByTestId('payment-method-paypal'));
    await userEvent.click(screen.getByTestId('wallet-topup-5'));

    await waitFor(() => {
      expect(topUpUrlCalled).toBe('/api/v1/router/wallet/demo-user/topup');
      expect(topUpBodyParsed).toEqual({
        amountUsd: 5.434783,
        paymentMethod: 'PAYPAL',
      });
      expect(screen.getByTestId('wallet-status-badge')).toHaveTextContent(
        'Prepago Activo (Con Saldo)',
      );
      expect(screen.getByTestId('wallet-balance-value')).toHaveTextContent('5,000000 €');
      expect(screen.getByTestId('wallet-last-method')).toHaveTextContent('PayPal');
    });
  });

  /**
   * Verifica la visualización de las métricas del Escudo DLP de Privacidad y Telemetría de Latencia:
   * - `data-testid="kpi-avg-latency-badge"` (`~18 ms`) y `data-testid="kpi-dlp-redacted-badge"` (`DLP: 3 protegidos`) en `StatsCards`.
   * - `data-testid="routed-dlp-badge"` (`DLP: 2 datos protegidos`) y `data-testid="routed-latency-badge"` (`12 ms`) en `PromptPlayground`.
   */
  it('renderiza los badges de Telemetría de Latencia y Escudo DLP en las tarjetas KPI y en el simulador de prompts', async () => {
    const dlpRoutedResponse: RouterResponse = {
      id: 'dlp-uuid-7777',
      userId: 'demo-user',
      promptContent: 'Contacto: [EMAIL_REDACTED] con token [SECRET_REDACTED]',
      modelRouted: 'gemini-2.5-flash',
      tokensEstimated: 10,
      simulatedResponse:
        "[Simulación AI Cost Router] Petición procesada exitosamente por el modelo 'gemini-2.5-flash' (10 tokens estimados).",
      createdAt: '2026-10-20T23:45:00',
      cacheHit: false,
      latencyMs: 12,
      redactedItemsCount: 2,
    };

    const statsWithDlpAndLatency: RouterStatsResponse = {
      ...MOCK_GLOBAL_STATS,
      totalRequests: 3,
      averageLatencyMs: 18,
      totalRedactedItems: 3,
    };

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url.includes('/prompt') && init?.method === 'POST') {
        return new Response(JSON.stringify(dlpRoutedResponse), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      if (url.includes('/stats')) {
        return new Response(JSON.stringify(statsWithDlpAndLatency), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      return new Response(JSON.stringify([dlpRoutedResponse, ...MOCK_INITIAL_LOGS]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('kpi-avg-latency-badge')).toHaveTextContent('~18 ms');
      expect(screen.getByTestId('kpi-dlp-redacted-badge')).toHaveTextContent('DLP: 3 protegidos');
    });

    const promptTextarea = screen.getByLabelText(/Contenido del Prompt/i);
    await userEvent.clear(promptTextarea);
    await userEvent.type(
      promptTextarea,
      'Contacto: user@empresa.com con token AIzaSySecretKey123456789',
    );
    await userEvent.click(screen.getByTestId('submit-prompt-button'));

    await waitFor(() => {
      expect(screen.getByTestId('playground-result-card')).toBeInTheDocument();
      expect(screen.getByTestId('routed-dlp-badge')).toHaveTextContent('DLP: 2 datos protegidos');
      expect(screen.getByTestId('routed-latency-badge')).toHaveTextContent('12 ms');
    });
  });
});
