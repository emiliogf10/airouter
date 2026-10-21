import React from 'react';
import { Activity, Coins, Hash, PiggyBank, Sparkles, Zap } from 'lucide-react';
import type { CurrencyCode, RouterStatsResponse } from '../types/router';
import { formatCurrency, USD_TO_EUR_RATE } from '../utils/currency';
import { MetricCard } from './MetricCard';

/**
 * Propiedades del componente de cuadrícula de indicadores clave (`StatsCards`).
 */
export interface StatsCardsProps {
  /** Objeto con las métricas agregadas devueltas por el backend (`GET /api/v1/router/stats`). */
  stats: RouterStatsResponse;
  /** Divisa activa (`'EUR'` o `'USD'`) para formatear los costes y el ahorro económico. */
  currency: CurrencyCode;
  /** Indica si las métricas están en proceso de carga inicial. */
  isLoading?: boolean;
}

/**
 * Cuadrícula superior de 4 tarjetas KPI (`StatsCards`) construida sobre el subcomponente
 * modular `MetricCard` con diseño minimalista estilo Vercel / shadcn/ui:
 * 1. Peticiones Totales y desglose entre `gemini-2.5-flash` y `gemini-2.5-pro`.
 * 2. Tokens Estimados Totales y desglose por modelo.
 * 3. Coste Real vs Coste Base Pro (en `EUR €` o `USD $`).
 * 4. Ahorro Económico Generado (importe ahorrado y badge de porcentaje `savingsPercentage%`).
 *
 * @param props Métricas actuales, divisa seleccionada y estado de carga.
 * @returns Cuadrícula responsiva (`grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6`).
 */
export const StatsCards: React.FC<StatsCardsProps> = ({
  stats,
  currency,
  isLoading = false,
}) => {
  const formattedActualCost = formatCurrency(stats.estimatedActualCostUsd, currency);
  const formattedBaselineCost = formatCurrency(stats.estimatedBaselineProCostUsd, currency);
  const formattedSavings = formatCurrency(stats.estimatedSavingsUsd, currency);
  const showSkeleton = isLoading && stats.totalRequests === 0 && stats.totalTokensEstimated === 0;

  return (
    <section
      aria-label="Indicadores clave de rendimiento"
      className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6"
    >
      {/* 1. Peticiones Totales */}
      <MetricCard
        title="Peticiones Totales"
        value={stats.totalRequests.toLocaleString('es-ES')}
        icon={Activity}
        accent="indigo"
        isLoading={showSkeleton}
        testId="kpi-total-requests"
        footer={
          <div className="flex items-center justify-between text-xs">
            <span className="inline-flex items-center gap-1 text-emerald-400 font-medium">
              <Zap className="w-3.5 h-3.5" aria-hidden="true" />
              Flash: {stats.fastModelRequests}
            </span>
            <span className="inline-flex items-center gap-1 text-indigo-400 font-medium">
              <Sparkles className="w-3.5 h-3.5" aria-hidden="true" />
              Pro: {stats.proModelRequests}
            </span>
          </div>
        }
      />

      {/* 2. Tokens Estimados Totales */}
      <MetricCard
        title="Tokens Estimados Totales"
        value={stats.totalTokensEstimated.toLocaleString('es-ES')}
        icon={Hash}
        accent="zinc"
        isLoading={showSkeleton}
        testId="kpi-total-tokens"
        footer={
          <div className="flex items-center justify-between text-xs text-zinc-400">
            <span>Flash: {stats.fastModelTokens.toLocaleString('es-ES')} tok.</span>
            <span>Pro: {stats.proModelTokens.toLocaleString('es-ES')} tok.</span>
          </div>
        }
      />

      {/* 3. Coste Real vs Coste Base Pro */}
      <MetricCard
        title="Coste Real Acumulado"
        value={formattedActualCost}
        icon={Coins}
        accent="zinc"
        isLoading={showSkeleton}
        testId="kpi-actual-cost"
        valueTestId="actual-cost-value"
        footer={
          <div className="flex items-center justify-between text-xs text-zinc-400">
            <span data-testid="kpi-baseline-pro-cost">
              Base Pro:{' '}
              <strong data-testid="baseline-cost-value" className="text-zinc-200 font-mono">
                {formattedBaselineCost}
              </strong>
            </span>
            <span className="text-gray-500">
              {currency === 'EUR' ? `1$ = ${USD_TO_EUR_RATE}€` : 'USD nativo'}
            </span>
          </div>
        }
      />

      {/* 4. Ahorro Económico Generado */}
      <MetricCard
        title="Ahorro Económico Generado"
        value={formattedSavings}
        icon={PiggyBank}
        accent="emerald"
        isLoading={showSkeleton}
        testId="kpi-savings"
        aliasTestId="kpi-estimated-savings"
        valueTestId="savings-value"
        badge={
          <span
            data-testid="savings-percentage-badge"
            className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
          >
            <span data-testid="kpi-savings-badge">{stats.savingsPercentage.toFixed(1)}%</span>
          </span>
        }
        footer={
          <span className="text-xs text-zinc-400">
            Optimización frente a enrutar 100% a Gemini 2.5 Pro
          </span>
        }
      />
    </section>
  );
};
