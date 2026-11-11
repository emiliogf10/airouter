import React from 'react';
import { ArrowUpRight, Clock, Inbox, Sparkles, User, Zap } from 'lucide-react';
import type { RouterResponse } from '../types/router';

/**
 * Propiedades del componente de tabla de historial reciente (`RecentLogsTable`).
 */
export interface RecentLogsTableProps {
  /** Lista de peticiones recientes obtenidas desde `GET /api/v1/router/logs`. */
  logs: RouterResponse[];
  /** Indica si los registros se están cargando actualmente desde el servidor. */
  isLoading: boolean;
  /** Callback opcional para disparar una prueba rápida en el simulador desde el estado vacío. */
  onQuickTry?: () => void;
}

/**
 * Formatea una cadena de fecha ISO a un formato legible en español (`dd/mm/aaaa hh:mm:ss`).
 *
 * @param isoDate Cadena de fecha en formato ISO-8601 devuelta por el backend.
 * @returns Cadena de fecha y hora formateada o el valor original si no es parseable.
 */
function formatDateTime(isoDate: string): string {
  const parsed = new Date(isoDate);
  if (Number.isNaN(parsed.getTime())) {
    return isoDate;
  }
  return parsed.toLocaleString('es-ES', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

/**
 * Tabla minimalista de registros recientes (`RecentLogsTable`) inspirada en Vercel / shadcn/ui.
 * Incluye:
 * - **Skeleton Loaders** animados (`animate-pulse`) durante la carga inicial.
 * - **Estado Vacío (`EmptyState`)** limpio con icono de `lucide-react` y botón de llamada a la acción
 *   cuando aún no existen peticiones registradas.
 * - Filas con transiciones suaves, badges sutiles (`emerald` para Flash e `indigo` para Pro) y tipografía clara.
 *
 * @param props Lista de logs, indicador de carga y acción rápida opcional.
 * @returns Sección de tabla responsiva con el historial de peticiones.
 */
export const RecentLogsTable: React.FC<RecentLogsTableProps> = ({
  logs,
  isLoading,
  onQuickTry,
}) => {
  return (
    <section
      aria-label="Historial de Peticiones Evaluadas"
      className="rounded-xl bg-zinc-900/60 border border-white/10 shadow-sm overflow-hidden"
    >
      {/* Cabecera de la sección */}
      <div className="p-6 border-b border-white/10 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-zinc-100">
            Historial de Peticiones Recientes
          </h2>
          <p className="text-sm text-zinc-400 mt-1">
            Registro de auditoría en tiempo real de prompts evaluados, tokens estimados y modelo asignado.
          </p>
        </div>
        <span
          data-testid="logs-count-badge"
          className="px-3 py-1 rounded-full text-xs font-medium bg-zinc-800/80 text-zinc-300 border border-white/10"
        >
          {logs.length} {logs.length === 1 ? 'registro' : 'registros'}
        </span>
      </div>

      {/* Estado de carga: Skeleton Loader */}
      {isLoading && logs.length === 0 ? (
        <div
          data-testid="logs-skeleton-loader"
          aria-busy="true"
          aria-label="Cargando historial de peticiones"
          className="p-6 space-y-4 animate-pulse"
        >
          {[1, 2, 3].map((rowIndex) => (
            <div
              key={rowIndex}
              className="grid grid-cols-12 gap-4 items-center py-3 border-b border-white/5 last:border-0"
            >
              <div className="col-span-2 h-4 rounded bg-zinc-800/90" />
              <div className="col-span-5 h-4 rounded bg-zinc-800/70" />
              <div className="col-span-2 h-6 rounded-full bg-zinc-800/80" />
              <div className="col-span-1 h-4 rounded bg-zinc-800/70" />
              <div className="col-span-2 h-4 rounded bg-zinc-800/60" />
            </div>
          ))}
        </div>
      ) : logs.length === 0 ? (
        /* Estado Vacío (EmptyState) con icono y CTA */
        <div
          data-testid="logs-empty-state"
          className="p-12 flex flex-col items-center justify-center text-center"
        >
          <div className="w-12 h-12 rounded-xl bg-zinc-800/70 border border-white/10 flex items-center justify-center text-zinc-400 mb-4">
            <Inbox className="w-6 h-6" aria-hidden="true" />
          </div>
          <h3 className="text-base font-semibold text-zinc-200">
            Sin peticiones registradas todavía
          </h3>
          <p
            data-testid="empty-logs-message"
            className="text-sm text-zinc-400 max-w-md mt-1"
          >
            No se encontraron peticiones registradas para los criterios actuales. Envía una consulta desde el simulador para evaluar su coste y modelo óptimo.
          </p>
          {onQuickTry && (
            <button
              type="button"
              onClick={onQuickTry}
              data-testid="quick-try-prompt-button"
              className="mt-5 inline-flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-medium bg-zinc-100 hover:bg-white text-zinc-950 transition cursor-pointer shadow-sm"
            >
              <span>Probar un prompt de ejemplo</span>
              <ArrowUpRight className="w-3.5 h-3.5" aria-hidden="true" />
            </button>
          )}
        </div>
      ) : (
        /* Tabla de datos */
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse" data-testid="request-logs-table">
            <thead>
              <tr className="border-b border-white/10 bg-zinc-950/40 text-xs font-medium text-zinc-400">
                <th className="py-3.5 px-6">Usuario</th>
                <th className="py-3.5 px-6">Fragmento del Prompt</th>
                <th className="py-3.5 px-6">Modelo Asignado</th>
                <th className="py-3.5 px-6 text-right">Tokens Estimados</th>
                <th className="py-3.5 px-6 text-right">Fecha y Hora</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5 text-sm">
              {logs.map((log) => {
                const isPro = log.modelRouted === 'gemini-2.5-pro';
                return (
                  <tr
                    key={log.id}
                    data-testid={`log-row-${log.id}`}
                    className="hover:bg-zinc-800/30 transition-colors"
                  >
                    <td className="py-4 px-6 whitespace-nowrap font-medium text-zinc-200">
                      <span className="inline-flex items-center gap-2">
                        <User className="w-3.5 h-3.5 text-zinc-500" aria-hidden="true" />
                        {log.userId}
                      </span>
                    </td>
                    <td className="py-4 px-6 max-w-md">
                      <p className="truncate text-zinc-300" title={log.promptContent}>
                        {log.promptContent}
                      </p>
                    </td>
                    <td className="py-4 px-6 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border ${
                          isPro
                            ? 'bg-indigo-500/10 text-indigo-300 border-indigo-500/20'
                            : 'bg-emerald-500/10 text-emerald-300 border-emerald-500/20'
                        }`}
                      >
                        {isPro ? (
                          <Sparkles className="w-3 h-3" aria-hidden="true" />
                        ) : (
                          <Zap className="w-3 h-3" aria-hidden="true" />
                        )}
                        {log.modelRouted}
                      </span>
                    </td>
                    <td className="py-4 px-6 whitespace-nowrap text-right font-mono text-zinc-200">
                      {log.tokensEstimated.toLocaleString('es-ES')}
                    </td>
                    <td className="py-4 px-6 whitespace-nowrap text-right text-xs text-zinc-400">
                      <span className="inline-flex items-center gap-1.5 justify-end">
                        <Clock className="w-3.5 h-3.5 text-zinc-500" aria-hidden="true" />
                        {formatDateTime(log.createdAt)}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
};
