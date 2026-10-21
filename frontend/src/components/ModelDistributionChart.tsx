import React from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { BarChart3, Sparkles, Zap } from 'lucide-react';
import type { RouterStatsResponse } from '../types/router';

/**
 * Propiedades del componente gráfico `ModelDistributionChart`.
 */
export interface ModelDistributionChartProps {
  /** Estadísticas agregadas de uso de modelos y tokens devueltas por el backend. */
  stats: RouterStatsResponse;
}

/**
 * Componente analítico que visualiza mediante `recharts` y barras de progreso comparativas
 * la proporción de peticiones y tokens procesados entre el modelo económico (`gemini-2.5-flash`)
 * y el modelo de razonamiento avanzado (`gemini-2.5-pro`).
 *
 * @param props Estadísticas actuales del Gateway.
 * @returns Tarjeta con gráfica de barras e indicadores porcentuales por modelo.
 */
export const ModelDistributionChart: React.FC<ModelDistributionChartProps> = ({ stats }) => {
  const totalReq = stats.totalRequests || 0;
  const flashPercent = totalReq > 0 ? Math.round((stats.fastModelRequests / totalReq) * 100) : 0;
  const proPercent = totalReq > 0 ? Math.round((stats.proModelRequests / totalReq) * 100) : 0;

  const chartData = [
    {
      name: 'gemini-2.5-flash',
      label: 'Flash (Económico)',
      peticiones: stats.fastModelRequests,
      tokens: stats.fastModelTokens,
      color: '#10b981',
    },
    {
      name: 'gemini-2.5-pro',
      label: 'Pro (Avanzado)',
      peticiones: stats.proModelRequests,
      tokens: stats.proModelTokens,
      color: '#a855f7',
    },
  ];

  return (
    <section
      aria-label="Distribución de Tráfico por Modelo LLM"
      className="rounded-xl bg-slate-900/90 border border-slate-800 p-6 shadow-lg flex flex-col justify-between"
    >
      <div>
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-lg font-bold text-white flex items-center gap-2">
              <BarChart3 className="w-5 h-5 text-cyan-400" aria-hidden="true" />
              Distribución por Modelo LLM
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Comparativa de carga de trabajo y consumo de tokens entre Flash ($0.15/1M) y Pro ($1.25/1M).
            </p>
          </div>
        </div>

        {/* Resumen visual de proporción */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-5">
          <div className="p-3.5 rounded-xl bg-slate-950/80 border border-emerald-500/20">
            <div className="flex items-center justify-between text-xs font-semibold text-emerald-400 mb-1">
              <span className="inline-flex items-center gap-1.5">
                <Zap className="w-3.5 h-3.5" aria-hidden="true" />
                gemini-2.5-flash
              </span>
              <span>{flashPercent}%</span>
            </div>
            <p className="text-lg font-bold text-white">
              {stats.fastModelRequests} pet.{' '}
              <span className="text-xs font-normal text-slate-400">
                ({stats.fastModelTokens.toLocaleString('es-ES')} tokens)
              </span>
            </p>
          </div>

          <div className="p-3.5 rounded-xl bg-slate-950/80 border border-purple-500/20">
            <div className="flex items-center justify-between text-xs font-semibold text-purple-400 mb-1">
              <span className="inline-flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5" aria-hidden="true" />
                gemini-2.5-pro
              </span>
              <span>{proPercent}%</span>
            </div>
            <p className="text-lg font-bold text-white">
              {stats.proModelRequests} pet.{' '}
              <span className="text-xs font-normal text-slate-400">
                ({stats.proModelTokens.toLocaleString('es-ES')} tokens)
              </span>
            </p>
          </div>
        </div>

        {/* Gráfica de barras con Recharts */}
        <div className="h-56 w-full" data-testid="model-distribution-chart">
          <ResponsiveContainer width="100%" height="100%" minWidth={200} minHeight={180}>
            <BarChart data={chartData} margin={{ top: 10, right: 16, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
              <XAxis dataKey="label" stroke="#94a3b8" fontSize={12} />
              <YAxis stroke="#94a3b8" fontSize={12} allowDecimals={false} />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#0f172a',
                  borderColor: '#334155',
                  borderRadius: '0.5rem',
                  color: '#f8fafc',
                  fontSize: '12px',
                }}
              />
              <Bar dataKey="peticiones" name="Peticiones" radius={[6, 6, 0, 0]}>
                {chartData.map((entry) => (
                  <Cell key={entry.name} fill={entry.color} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Barra de proporción combinada */}
      <div className="mt-4 pt-3 border-t border-slate-800/80">
        <div className="flex justify-between text-xs text-slate-400 mb-1.5">
          <span>Ratio de Eficiencia Flash vs Pro</span>
          <span>
            {flashPercent}% Flash / {proPercent}% Pro
          </span>
        </div>
        <div className="w-full h-2.5 bg-slate-950 rounded-full overflow-hidden flex border border-slate-800">
          <div
            style={{ width: `${flashPercent}%` }}
            className="bg-emerald-500 transition-all duration-500"
            title={`gemini-2.5-flash: ${flashPercent}%`}
          />
          <div
            style={{ width: `${proPercent}%` }}
            className="bg-purple-500 transition-all duration-500"
            title={`gemini-2.5-pro: ${proPercent}%`}
          />
        </div>
      </div>
    </section>
  );
};
