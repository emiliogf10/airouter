import React from 'react';
import { BarChart3, PieChart, Sparkles, Zap } from 'lucide-react';
import type { RouterStatsResponse } from '../types/router';

/**
 * Propiedades del componente analítico `ModelDistributionChart`.
 */
export interface ModelDistributionChartProps {
  /** Estadísticas agregadas de uso de modelos y tokens devueltas por el backend. */
  stats: RouterStatsResponse;
  /** Indica si las métricas se encuentran en estado de carga inicial. */
  isLoading?: boolean;
}

/**
 * Tarjeta analítica inspirada en Vercel Analytics (`ModelDistributionChart`) que muestra
 * la distribución de peticiones y tokens entre `gemini-2.5-flash` (económico) y
 * `gemini-2.5-pro` (razonamiento avanzado).
 * Construida íntegramente con clases de utilidad de Tailwind CSS v4 y geometría SVG nativa,
 * incluyendo Skeleton Loader durante la carga y un estado vacío cuando aún no hay tráfico.
 *
 * @param props Estadísticas actuales del Gateway e indicador de carga.
 * @returns Tarjeta analítica con comparativa de modelos y eficiencia de tokens.
 */
export const ModelDistributionChart: React.FC<ModelDistributionChartProps> = ({
  stats,
  isLoading = false,
}) => {
  const totalReq = stats.totalRequests || 0;
  const totalTok = stats.totalTokensEstimated || 0;

  const flashReqPercent = totalReq > 0 ? Math.round((stats.fastModelRequests / totalReq) * 100) : 0;
  const proReqPercent = totalReq > 0 ? 100 - flashReqPercent : 0;

  const flashTokPercent = totalTok > 0 ? Math.round((stats.fastModelTokens / totalTok) * 100) : 0;
  const proTokPercent = totalTok > 0 ? 100 - flashTokPercent : 0;

  return (
    <section
      aria-label="Distribución de Tráfico por Modelo LLM"
      className="rounded-xl bg-zinc-900/60 border border-white/10 p-6 shadow-sm flex flex-col justify-between"
    >
      <div>
        {/* Cabecera de la tarjeta */}
        <div className="flex items-start justify-between gap-4 mb-6">
          <div>
            <h2 className="text-xl font-semibold tracking-tight text-zinc-100 flex items-center gap-2.5">
              <BarChart3 className="w-5 h-5 text-indigo-400" aria-hidden="true" />
              Distribución por Modelo LLM
            </h2>
            <p className="text-sm text-zinc-400 mt-1">
              Desglose de tráfico y volumen de tokens entre Flash ($0.15/1M) y Pro ($1.25/1M).
            </p>
          </div>
          <span className="px-2.5 py-1 rounded-md text-xs font-medium bg-zinc-800/80 text-zinc-300 border border-white/10">
            Telemetría Activa
          </span>
        </div>

        {/* Estado de carga: Skeleton Loader */}
        {isLoading && totalReq === 0 ? (
          <div
            data-testid="chart-skeleton-loader"
            aria-busy="true"
            aria-label="Cargando distribución de modelos"
            className="space-y-5 py-4 animate-pulse"
          >
            <div className="grid grid-cols-2 gap-4">
              <div className="h-24 rounded-xl bg-zinc-800/70" />
              <div className="h-24 rounded-xl bg-zinc-800/70" />
            </div>
            <div className="space-y-3">
              <div className="h-4 w-48 rounded bg-zinc-800/80" />
              <div className="h-3 w-full rounded-full bg-zinc-800/60" />
              <div className="h-4 w-56 rounded bg-zinc-800/80" />
              <div className="h-3 w-full rounded-full bg-zinc-800/60" />
            </div>
          </div>
        ) : totalReq === 0 ? (
          /* Estado vacío cuando no existen peticiones */
          <div
            data-testid="chart-empty-state"
            className="py-12 px-4 rounded-xl bg-zinc-950/50 border border-white/5 flex flex-col items-center justify-center text-center"
          >
            <div className="w-11 h-11 rounded-xl bg-zinc-800/70 border border-white/10 flex items-center justify-center text-zinc-400 mb-3">
              <PieChart className="w-5 h-5" aria-hidden="true" />
            </div>
            <h3 className="text-sm font-semibold text-zinc-200">
              Sin datos de distribución disponibles
            </h3>
            <p className="text-xs text-zinc-400 max-w-xs mt-1">
              Las métricas comparativas entre Gemini 2.5 Flash y Gemini 2.5 Pro aparecerán automáticamente tras evaluar la primera consulta.
            </p>
          </div>
        ) : (
          /* Visualización analítica estilo Vercel Analytics */
          <div data-testid="model-distribution-chart" className="space-y-6">
            {/* Tarjetas de resumen por modelo */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="p-4 rounded-xl bg-zinc-950/70 border border-white/10">
                <div className="flex items-center justify-between text-xs font-medium text-emerald-400 mb-2">
                  <span className="inline-flex items-center gap-1.5">
                    <Zap className="w-3.5 h-3.5" aria-hidden="true" />
                    gemini-2.5-flash
                  </span>
                  <span className="px-2 py-0.5 rounded-full bg-emerald-500/10 border border-emerald-500/20 font-mono">
                    {flashReqPercent}%
                  </span>
                </div>
                <p className="text-2xl font-bold text-zinc-100 tracking-tight font-mono">
                  {stats.fastModelRequests.toLocaleString('es-ES')}{' '}
                  <span className="text-xs font-sans font-normal text-zinc-400">peticiones</span>
                </p>
                <p className="text-xs text-gray-500 mt-1 font-mono">
                  {stats.fastModelTokens.toLocaleString('es-ES')} tokens ({flashTokPercent}%)
                </p>
              </div>

              <div className="p-4 rounded-xl bg-zinc-950/70 border border-white/10">
                <div className="flex items-center justify-between text-xs font-medium text-indigo-400 mb-2">
                  <span className="inline-flex items-center gap-1.5">
                    <Sparkles className="w-3.5 h-3.5" aria-hidden="true" />
                    gemini-2.5-pro
                  </span>
                  <span className="px-2 py-0.5 rounded-full bg-indigo-500/10 border border-indigo-500/20 font-mono">
                    {proReqPercent}%
                  </span>
                </div>
                <p className="text-2xl font-bold text-zinc-100 tracking-tight font-mono">
                  {stats.proModelRequests.toLocaleString('es-ES')}{' '}
                  <span className="text-xs font-sans font-normal text-zinc-400">peticiones</span>
                </p>
                <p className="text-xs text-gray-500 mt-1 font-mono">
                  {stats.proModelTokens.toLocaleString('es-ES')} tokens ({proTokPercent}%)
                </p>
              </div>
            </div>

            {/* Desglose de barras comparativas por modelo (peticiones) */}
            <div className="space-y-4 p-4 rounded-xl bg-zinc-950/50 border border-white/5">
              <div className="space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-medium text-zinc-300">
                    Cuota de Peticiones Enrutadas
                  </span>
                  <span className="text-zinc-400 font-mono">
                    {flashReqPercent}% Flash · {proReqPercent}% Pro
                  </span>
                </div>
                <svg
                  viewBox="0 0 100 6"
                  preserveAspectRatio="none"
                  className="w-full h-2.5 rounded-full overflow-hidden bg-zinc-900 border border-white/10"
                  role="img"
                  aria-label={`Distribución de peticiones: ${flashReqPercent}% Flash, ${proReqPercent}% Pro`}
                >
                  <rect
                    x={0}
                    y={0}
                    width={flashReqPercent}
                    height={6}
                    className="fill-emerald-500 transition-all duration-500"
                  />
                  <rect
                    x={flashReqPercent}
                    y={0}
                    width={proReqPercent}
                    height={6}
                    className="fill-indigo-500 transition-all duration-500"
                  />
                </svg>
              </div>

              <div className="space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-medium text-zinc-300">
                    Volumen de Tokens Procesados
                  </span>
                  <span className="text-zinc-400 font-mono">
                    {flashTokPercent}% Flash · {proTokPercent}% Pro
                  </span>
                </div>
                <svg
                  viewBox="0 0 100 6"
                  preserveAspectRatio="none"
                  className="w-full h-2.5 rounded-full overflow-hidden bg-zinc-900 border border-white/10"
                  role="img"
                  aria-label={`Distribución de tokens: ${flashTokPercent}% Flash, ${proTokPercent}% Pro`}
                >
                  <rect
                    x={0}
                    y={0}
                    width={flashTokPercent}
                    height={6}
                    className="fill-emerald-500/80 transition-all duration-500"
                  />
                  <rect
                    x={flashTokPercent}
                    y={0}
                    width={proTokPercent}
                    height={6}
                    className="fill-indigo-500/80 transition-all duration-500"
                  />
                </svg>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Pie informativo de leyenda */}
      <div className="mt-6 pt-4 border-t border-white/5 flex flex-wrap items-center justify-between gap-2 text-xs text-zinc-400">
        <div className="flex items-center gap-4">
          <span className="inline-flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
            Flash (Baja latencia)
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-indigo-500" />
            Pro (Alta complejidad)
          </span>
        </div>
        <span className="text-gray-500">Umbral heurístico: 50 tokens</span>
      </div>
    </section>
  );
};
