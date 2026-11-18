import React, { useCallback, useEffect, useState } from 'react';
import { AlertCircle } from 'lucide-react';
import { ApiKeyManagerCard } from './components/ApiKeyManagerCard';
import { BudgetControlCard } from './components/BudgetControlCard';
import { Header } from './components/Header';
import { LogInspectorDrawer } from './components/LogInspectorDrawer';
import { ModelDistributionChart } from './components/ModelDistributionChart';
import { PromptPlayground } from './components/PromptPlayground';
import { RecentLogsTable } from './components/RecentLogsTable';
import { StatsCards } from './components/StatsCards';
import { WalletBillingCard } from './components/WalletBillingCard';
import {
  createDefaultUserBudget,
  createDefaultUserWallet,
  fetchLogs,
  fetchStats,
  fetchUserApiKeys,
  fetchUserBudget,
  fetchUserWallet,
} from './services/api';
import type {
  ApiKeyResponse,
  CurrencyCode,
  RouterResponse,
  RouterStatsResponse,
  UserBudgetResponse,
  UserWalletResponse,
} from './types/router';

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
 * directrices estéticas minimalistas de Vercel / shadcn/ui y los estándares de
 * `modern-web-guidance`.
 * Coordina la gestión de estado global para:
 * - El selector interactivo de moneda (`EUR €` seleccionado por defecto o `USD $`).
 * - El filtrado reactivo de telemetría e historial por usuario (`userId`).
 * - El panel de control de Cortafuegos Presupuestario FinOps (`BudgetControlCard`).
 * - La Billetera Prepago y Pasarela de Pagos Stripe/PayPal (`WalletBillingCard`).
 * - El panel gestor de Claves API persistidas con hash SHA-256 (`ApiKeyManagerCard`).
 * - El visor modal de auditoría detallada (`LogInspectorDrawer`) y exportación CSV segura.
 * - La sincronización automática de estadísticas (`GET /api/v1/router/stats`) y registros (`GET /api/v1/router/logs`).
 *
 * @returns Interfaz completa del panel de control de AI Cost Router.
 */
export const App: React.FC = () => {
  /** Divisa activa del Dashboard; por requerimiento funcional arranca en Euros (`'EUR'`). */
  const [currency, setCurrency] = useState<CurrencyCode>('EUR');
  /** Filtro opcional por identificador de usuario (`userId`). */
  const [filterUserId, setFilterUserId] = useState<string>('');
  /** Usuario actualmente inspeccionado en los paneles FinOps, billetera y claves (`demo-user` por defecto). */
  const [budgetUserId, setBudgetUserId] = useState<string>('demo-user');
  /** Estado del presupuesto FinOps del usuario gestionado. */
  const [userBudget, setUserBudget] = useState<UserBudgetResponse>(() =>
    createDefaultUserBudget('demo-user'),
  );
  /** Estado de la billetera prepago (`Billing`) del usuario gestionado. */
  const [userWallet, setUserWallet] = useState<UserWalletResponse>(() =>
    createDefaultUserWallet('demo-user'),
  );
  /** Lista de claves API asociadas al usuario activo. */
  const [userApiKeys, setUserApiKeys] = useState<ApiKeyResponse[]>([]);
  /** Clave API seleccionada desde el gestor para inyectarse en el simulador. */
  const [playgroundExternalApiKey, setPlaygroundExternalApiKey] = useState<string>('');
  /** Señal incremental para sincronizar la inyección de clave API en el simulador. */
  const [playgroundApiKeySignal, setPlaygroundApiKeySignal] = useState<number>(0);
  /** Métricas agregadas de consumo de tokens y ahorro financiero. */
  const [stats, setStats] = useState<RouterStatsResponse>(INITIAL_STATS);
  /** Historial reciente de peticiones evaluadas por el Gateway. */
  const [logs, setLogs] = useState<RouterResponse[]>([]);
  /** Registro actualmente seleccionado para inspección en el visor modal `<dialog>`. */
  const [selectedLog, setSelectedLog] = useState<RouterResponse | null>(null);
  /** Indica si se están cargando o refrescando los datos desde el backend. */
  const [isLoading, setIsLoading] = useState<boolean>(true);
  /** Indica si el backend respondió correctamente en la última sincronización. */
  const [isGatewayOnline, setIsGatewayOnline] = useState<boolean>(true);
  /** Mensaje de aviso si la carga de estadísticas o logs falla. */
  const [connectionWarning, setConnectionWarning] = useState<string | null>(null);
  /** Señal incremental para cargar un prompt rápido desde el estado vacío de la tabla. */
  const [quickTrySignal, setQuickTrySignal] = useState<number>(0);

  /**
   * Sincroniza de forma aislada el presupuesto FinOps de un usuario específico
   * sin bloquear ni interferir con la carga principal de métricas e historial.
   *
   * @param targetUser Identificador del usuario cuyo presupuesto se desea cargar.
   */
  const loadBudgetForUser = useCallback(async (targetUser: string) => {
    const effectiveUser = targetUser.trim() || 'demo-user';
    const budgetData = await fetchUserBudget(effectiveUser);
    setUserBudget(budgetData);
  }, []);

  /**
   * Sincroniza de forma aislada el saldo de la billetera prepago (`Billing`) de un usuario.
   *
   * @param targetUser Identificador del usuario cuya billetera se desea cargar.
   */
  const loadWalletForUser = useCallback(async (targetUser: string) => {
    const effectiveUser = targetUser.trim() || 'demo-user';
    const walletData = await fetchUserWallet(effectiveUser);
    setUserWallet(walletData);
  }, []);

  /**
   * Sincroniza de forma aislada las claves API de un usuario específico.
   *
   * @param targetUser Identificador del usuario cuyas claves se desean cargar.
   */
  const loadApiKeysForUser = useCallback(async (targetUser: string) => {
    const effectiveUser = targetUser.trim() || 'demo-user';
    const keysData = await fetchUserApiKeys(effectiveUser);
    setUserApiKeys(keysData);
  }, []);

  /**
   * Consulta en paralelo las métricas (`/stats`) y el historial (`/logs`) del backend,
   * aplicando el filtro `filterUserId` cuando está definido, y posteriormente actualiza
   * el estado del presupuesto FinOps, la billetera prepago y las claves API del usuario activo.
   *
   * @param userFilter Identificador de usuario opcional para filtrar los datos.
   */
  const loadDashboardData = useCallback(
    async (userFilter?: string) => {
      setIsLoading(true);
      setConnectionWarning(null);
      const effectiveBudgetUser = userFilter?.trim() || budgetUserId || 'demo-user';
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

      void loadBudgetForUser(effectiveBudgetUser);
      void loadWalletForUser(effectiveBudgetUser);
      void loadApiKeysForUser(effectiveBudgetUser);
    },
    [budgetUserId, loadApiKeysForUser, loadBudgetForUser, loadWalletForUser],
  );

  useEffect(() => {
    if (filterUserId.trim() !== '') {
      setBudgetUserId(filterUserId.trim());
    }
    void loadDashboardData(filterUserId);
  }, [filterUserId, loadDashboardData]);

  /**
   * Callback ejecutado cuando el simulador procesa un nuevo prompt con éxito,
   * recargando las estadísticas, el historial, el presupuesto y el saldo prepago automáticamente.
   */
  const handlePromptSuccess = useCallback(
    (response: RouterResponse) => {
      const targetUser = filterUserId.trim() || response.userId || 'demo-user';
      setBudgetUserId(targetUser);
      void loadDashboardData(filterUserId);
      void loadBudgetForUser(targetUser);
      void loadWalletForUser(targetUser);
      void loadApiKeysForUser(targetUser);
    },
    [
      filterUserId,
      loadApiKeysForUser,
      loadBudgetForUser,
      loadDashboardData,
      loadWalletForUser,
    ],
  );

  /**
   * Callback invocado cuando ocurre actividad de presupuesto/billetera o bloqueo HTTP 402 en el simulador.
   */
  const handleBudgetActivity = useCallback(
    (targetUser: string) => {
      const cleanUser = targetUser.trim() || 'demo-user';
      setBudgetUserId(cleanUser);
      void loadBudgetForUser(cleanUser);
      void loadWalletForUser(cleanUser);
      void loadApiKeysForUser(cleanUser);
    },
    [loadApiKeysForUser, loadBudgetForUser, loadWalletForUser],
  );

  /**
   * Inyecta una clave recién generada en el campo `X-API-Key` del simulador y enfoca el input.
   *
   * @param rawApiKey Token secreto completo (`air_live_...`).
   */
  const handleUseKeyInPlayground = useCallback((rawApiKey: string) => {
    setPlaygroundExternalApiKey(rawApiKey);
    setPlaygroundApiKeySignal((prev) => prev + 1);
    const input = document.getElementById('playground-api-key');
    input?.focus();
  }, []);

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

        {/* Gobernanza FinOps y Seguridad por Usuario: Presupuesto, Billetera Prepago y Claves API */}
        <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
          <BudgetControlCard
            budget={userBudget}
            currency={currency}
            onBudgetUpdated={setUserBudget}
          />
          <WalletBillingCard
            wallet={userWallet}
            currency={currency}
            onWalletUpdated={setUserWallet}
          />
        </div>

        <ApiKeyManagerCard
          userId={budgetUserId}
          apiKeys={userApiKeys}
          onKeysChanged={() => void loadApiKeysForUser(budgetUserId)}
          onUseKeyInPlayground={handleUseKeyInPlayground}
        />

        {/* Sección central: Simulador interactivo + Distribución por modelo */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <PromptPlayground
            onPromptSuccess={handlePromptSuccess}
            quickTrySignal={quickTrySignal}
            onUserOrBudgetActivity={handleBudgetActivity}
            externalApiKey={playgroundExternalApiKey}
            externalApiKeySignal={playgroundApiKeySignal}
          />
          <ModelDistributionChart stats={stats} isLoading={isLoading} />
        </div>

        {/* Sección inferior: Historial de peticiones recientes + Exportación CSV */}
        <RecentLogsTable
          logs={logs}
          isLoading={isLoading}
          onQuickTry={handleQuickTryFromEmptyState}
          onSelectLog={setSelectedLog}
          currency={currency}
        />
      </main>

      {/* Visor Modal Nativo (<dialog>) de Detalle de Petición */}
      <LogInspectorDrawer
        log={selectedLog}
        currency={currency}
        onClose={() => setSelectedLog(null)}
      />

      <footer className="border-t border-white/10 py-6 text-center text-xs text-gray-500">
        AI Cost Router — Plataforma de Enrutamiento Inteligente y Optimización de Costes LLM
      </footer>
    </div>
  );
};

export default App;
