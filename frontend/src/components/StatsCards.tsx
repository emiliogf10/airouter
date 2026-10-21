import React from 'react';
import { Activity, Coins, Hash, PiggyBank, Sparkles, Zap } from 'lucide-react';
import type { CurrencyCode, RouterStatsResponse } from '../types/router';
import { formatCurrency, USD_TO_EUR_RATE } from '../utils/currency';

/**
 * Propiedades del componente de tarjetas de indicadores clave (`StatsCards`).
 */
export interface StatsCardsProps {
  /** Objeto con las métricas agregadas devueltas por el backend (`GET /api/v1/router/stats`). */
  stats: RouterStatsResponse;
  /** Divisa activa (`'EUR'` o `'USD'`) para formatear los costes y el ahorro económico. */
  currency: CurrencyCode;
}

/**
 * Panel de 4 tarjetas KPI (Key Performance Indicators) que resume el tráfico del Gateway:
 * 1. Peticiones Totales y desglose entre `gemini-2.5-flash` y `gemini-2.5-pro`.
 * 2. Tokens Estimados Totales y desglose por modelo.
 * 3. Coste Real vs Coste Base Pro (convertido dinámicamente a `EUR €` o `USD $`).
 * 4. Ahorro Económico Generado (importe ahorrado en la moneda activa y porcentaje `savingsPercentage%`).
 *
 * @param props Métricas actuales y moneda seleccionada.
 * @returns Cuadrícula responsiva con las 4 tarjetas de analítica financiera y operativa.
 */
export const StatsCards: React.FC<StatsCardsProps> = ({ stats, currency }) => {
  const formattedActualCost = formatCurrency(stats.estimatedActualCostUsd, currency);
  const formattedBaselineCost = formatCurrency(stats.estimatedBaselineProCostUsd, currency);
  const formattedSavings = formatCurrency(stats.estimatedSavingsUsd, currency);

  return (
    <section aria-label="Indicadores clave de rendimiento" className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      {/* 1. Peticiones Totales */}
      <article
        data-testid="kpi-total-requests"
        className="rounded-xl bg-slate-900/90 border border-slate-800 p-5 shadow-lg flex flex-col justify-between"
      >
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Peticiones Totales
          </span>
          <div className="p-2 rounded-lg bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
            <Activity className="w-4 h-4" aria-hidden="true" />
          </div>
        </div>

        <div className="my-3">
          <p className="text-3xl font-extrabold text-white tracking-tight">
            {stats.totalRequests.toLocaleString('es-ES')}
          </p>
        </div>

        <div className="flex items-center justify-between text-xs pt-3 border-t border-slate-800/80">
          <span className="inline-flex items-center gap-1 text-emerald-400 font-medium">
            <Zap className="w-3.5 h-3.5" aria-hidden="true" />
            Flash: {stats.fastModelRequests}
          </span>
          <span className="inline-flex items-center gap-1 text-purple-400 font-medium">
            <Sparkles className="w-3.5 h-3.5" aria-hidden="true" />
            Pro: {stats.proModelRequests}
          </span>
        </div>
      </article>

      {/* 2. Tokens Estimados Totales */}
      <article
        data-testid="kpi-total-tokens"
        className="rounded-xl bg-slate-900/90 border border-slate-800 p-5 shadow-lg flex flex-col justify-between"
      >
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Tokens Estimados Totales
          </span>
          <div className="p-2 rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
            <Hash className="w-4 h-4" aria-hidden="true" />
          </div>
        </div>

        <div className="my-3">
          <p className="text-3xl font-extrabold text-white tracking-tight">
            {stats.totalTokensEstimated.toLocaleString('es-ES')}
          </p>
        </div>

        <div className="flex items-center justify-between text-xs pt-3 border-t border-slate-800/80 text-slate-400">
          <span>Flash: {stats.fastModelTokens.toLocaleString('es-ES')} tok.</span>
          <span>Pro: {stats.proModelTokens.toLocaleString('es-ES')} tok.</span>
        </div>
      </article>

      {/* 3. Coste Real vs Coste Base Pro */}
      <article
        data-testid="kpi-actual-cost"
        className="rounded-xl bg-slate-900/90 border border-slate-800 p-5 shadow-lg flex flex-col justify-between"
      >
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Coste Real vs Coste Base Pro
          </span>
          <div className="p-2 rounded-lg bg-amber-500/10 text-amber-400 border border-amber-500/20">
            <Coins className="w-4 h-4" aria-hidden="true" />
          </div>
        </div>

        <div className="my-3">
          <p
            data-testid="actual-cost-value"
            className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight font-mono"
          >
            {formattedActualCost}
          </p>
        </div>

        <div className="flex items-center justify-between text-xs pt-3 border-t border-slate-800/80 text-slate-400">
          <span>
            Base Pro:{' '}
            <strong data-testid="baseline-cost-value" className="text-slate-200 font-mono">
              {formattedBaselineCost}
            </strong>
          </span>
          <span className="text-[11px] text-slate-500">
            {currency === 'EUR' ? `(1$ = ${USD_TO_EUR_RATE}€)` : '(Nativo USD)'}
          </span>
        </div>
      </article>

      {/* 4. Ahorro Económico Generado */}
      <article
        data-testid="kpi-savings"
        className="rounded-xl bg-gradient-to-br from-emerald-950/60 via-slate-900 to-slate-900 border border-emerald-500/30 p-5 shadow-lg flex flex-col justify-between"
      >
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-emerald-300">
            Ahorro Económico Generado
          </span>
          <div className="p-2 rounded-lg bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
            <PiggyBank className="w-4 h-4" aria-hidden="true" />
          </div>
        </div>

        <div className="my-3 flex items-baseline gap-2.5 flex-wrap">
          <p
            data-testid="savings-value"
            className="text-2xl sm:text-3xl font-extrabold text-emerald-400 tracking-tight font-mono"
          >
            {formattedSavings}
          </p>
          <span
            data-testid="savings-percentage-badge"
            className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/40"
          >
            {stats.savingsPercentage.toFixed(1)}%
          </span>
        </div>

        <div className="text-xs pt-3 border-t border-emerald-500/20 text-emerald-200/70">
          Optimización frente a enrutar 100% a Gemini 2.5 Pro
        </div>
      </article>
    </section>
  );
};
