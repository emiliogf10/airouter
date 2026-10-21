import React, { useCallback, useEffect, useState } from 'react';
import { AlertCircle } from 'lucide-react';
import { Header } from './components/Header';
import { ModelDistributionChart } from './components/ModelDistributionChart';
import { PromptPlayground } from './components/PromptPlayground';
import { RequestLogsTable } from './components/RequestLogsTable';
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
 * Componente raíz del Dashboard Analítico **AI Cost Router**.
 * Coordina la gestión de estado global para:
 * - El selector interactivo de moneda (`EUR €` seleccionado por defecto o `USD $`).
 * - El filtrado de telemetría e historial por usuario (`userId`).
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

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      <Header
        currency={currency}
        onCurrencyChange={setCurrency}
        filterUserId={filterUserId}
        onFilterUserIdChange={setFilterUserId}
        onRefresh={() => void loadDashboardData(filterUserId)}
        isRefreshing={isLoading}
        isGatewayOnline={isGatewayOnline}
      />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
        {connectionWarning && (
          <div
            role="status"
            data-testid="connection-warning-banner"
            className="p-3.5 rounded-xl bg-amber-950/50 border border-amber-500/40 text-amber-200 text-xs flex items-center gap-2.5"
          >
            <AlertCircle className="w-4 h-4 text-amber-400 shrink-0" aria-hidden="true" />
            <span>{connectionWarning}</span>
          </div>
        )}

        {/* Tarjetas KPI con soporte multimoneda EUR/USD */}
        <StatsCards stats={stats} currency={currency} />

        {/* Sección intermedia: Simulador en vivo + Gráfica de distribución */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <PromptPlayground onPromptSuccess={handlePromptSuccess} />
          <ModelDistributionChart stats={stats} />
        </div>

        {/* Tabla de historial de peticiones */}
        <RequestLogsTable logs={logs} isLoading={isLoading} />
      </main>

      <footer className="border-t border-slate-900 py-4 text-center text-xs text-slate-500">
        AI Cost Router — Panel de Control de Optimización de Costes LLM (React 19 + Tailwind CSS v4)
      </footer>
    </div>
  );
};

export default App;
