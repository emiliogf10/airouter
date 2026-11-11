import React, { useCallback, useEffect, useState } from 'react';
import { AlertCircle } from 'lucide-react';
import { Header } from './components/Header';
import { ModelDistributionChart } from './components/ModelDistributionChart';
import { PromptPlayground } from './components/PromptPlayground';
import { RecentLogsTable } from './components/RecentLogsTable';
import { StatsCards } from './components/StatsCards';
import { fetchLogs, fetchStats } from './services/api';
import type { CurrencyCode, RouterResponse, RouterStatsResponse } from './types/router';

/**
 * Estado inicial vacío de métricas cuando aún no se han cargado datos del servidor.
 */
const INITIAL_STATS: RouterStatsResponse = {
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
 * Componente raíz del Dashboard Analítico **AI Cost Router** rediseñado bajo las
 * directrices estéticas minimalistas de Vercel / shadcn/ui.
 * Coordina la gestión de estado global para:
 * - El selector interactivo de moneda (`EUR €` seleccionado por defecto o `USD $`).
 * - El filtrado reactivo de telemetría e historial por usuario (`userId`).
 * - La sincronización automática de estadísticas (`GET /api/v1/router/stats`) y registros (`GET /api/v1/router/logs`).
 *
 * @returns Interfaz completa del panel de control de AI Cost Router.
 */
export const App: React.FC = () => {
  /** Divisa activa del Dashboard; por requerimiento funcional arranca en Euros (`'EUR'`). */
  const [currency, setCurrency] = useState<CurrencyCode>('EUR');
  /** Filtro opcional por identificador de usuario (`userId`). */
  const [filterUserId, setFilterUserId] = useState<string>('');
  /** Métricas agregadas de consumo de tokens y ahorro financiero. */
  const [stats, setStats] = useState<RouterStatsResponse>(INITIAL_STATS);
  /** Historial reciente de peticiones evaluadas por el Gateway. */
  const [logs, setLogs] = useState<RouterResponse[]>([]);
  /** Indica si se están cargando o refrescando los datos desde el backend. */
  const [isLoading, setIsLoading] = useState<boolean>(true);
  /** Indica si el backend respondió correctamente en la última sincronización. */
  const [isGatewayOnline, setIsGatewayOnline] = useState<boolean>(true);
  /** Mensaje de aviso si la carga de estadísticas o logs falla. */
  const [connectionWarning, setConnectionWarning] = useState<string | null>(null);
  /** Señal incremental para cargar un prompt rápido desde el estado vacío de la tabla. */
  const [quickTrySignal, setQuickTrySignal] = useState<number>(0);

  /**
   * Consulta en paralelo las métricas (`/stats`) y el historial (`/logs`) del backend,
   * aplicando el filtro `filterUserId` cuando está definido.
   *
   * @param userFilter Identificador de usuario opcional para filtrar los datos.
   */
  const loadDashboardData = useCallback(async (userFilter?: string) => {
    setIsLoading(true);
    setConnectionWarning(null);
    try {
      const [statsData, logsData] = await Promise.all([
        fetchStats(userFilter),
        fetchLogs(userFilter),
      ]);
      setStats(statsData);
      setLogs(logsData);
      setIsGatewayOnline(true);
    } catch (err) {
      setIsGatewayOnline(false);
      const detail =
        err instanceof Error ? err.message : 'No se pudo conectar con el servidor Backend.';
      setConnectionWarning(
        `No se pudieron sincronizar los datos con el Gateway (${detail}). Verifica que el backend esté activo en el puerto 8080.`,
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDashboardData(filterUserId);
  }, [filterUserId, loadDashboardData]);

  /**
   * Callback ejecutado cuando el simulador procesa un nuevo prompt con éxito,
   * recargando las estadísticas y la tabla de historial automáticamente.
   */
  const handlePromptSuccess = useCallback(() => {
    void loadDashboardData(filterUserId);
  }, [filterUserId, loadDashboardData]);

  /**
   * Carga un prompt de ejemplo en el simulador y desplaza suavemente el foco al área de texto.
   */
  const handleQuickTryFromEmptyState = useCallback(() => {
    setQuickTrySignal((prev) => prev + 1);
    const textarea = document.getElementById('playground-prompt');
    textarea?.focus();
  }, []);

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col">
      <Header
        currency={currency}
        onCurrencyChange={setCurrency}
        filterUserId={filterUserId}
        onFilterUserIdChange={setFilterUserId}
        onRefresh={() => void loadDashboardData(filterUserId)}
        isRefreshing={isLoading}
        isGatewayOnline={isGatewayOnline}
      />

      <main className="flex-1 max-w-7xl w-full mx-auto p-6 space-y-6">
        {connectionWarning && (
          <div
            role="status"
            data-testid="connection-warning-banner"
            className="p-4 rounded-xl bg-zinc-900/80 border border-white/10 text-zinc-300 text-xs flex items-center gap-3"
          >
            <AlertCircle className="w-4 h-4 text-amber-400 shrink-0" aria-hidden="true" />
            <span>{connectionWarning}</span>
          </div>
        )}

        {/* Cuadrícula superior de métricas clave con MetricCard */}
        <StatsCards stats={stats} currency={currency} isLoading={isLoading} />

        {/* Sección central: Simulador interactivo + Distribución por modelo */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <PromptPlayground
            onPromptSuccess={handlePromptSuccess}
            quickTrySignal={quickTrySignal}
          />
          <ModelDistributionChart stats={stats} isLoading={isLoading} />
        </div>

        {/* Sección inferior: Historial de peticiones recientes */}
        <RecentLogsTable
          logs={logs}
          isLoading={isLoading}
          onQuickTry={handleQuickTryFromEmptyState}
        />
      </main>

      <footer className="border-t border-white/10 py-6 text-center text-xs text-gray-500">
        AI Cost Router — Plataforma de Enrutamiento Inteligente y Optimización de Costes LLM
      </footer>
    </div>
  );
};

export default App;
