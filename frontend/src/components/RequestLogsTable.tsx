import React from 'react';
import { Clock, FileText, Sparkles, User, Zap } from 'lucide-react';
import type { RouterResponse } from '../types/router';

/**
 * Propiedades de la tabla de historial de peticiones (`RequestLogsTable`).
 */
export interface RequestLogsTableProps {
  /** Lista de peticiones recientes obtenidas desde `GET /api/v1/router/logs`. */
  logs: RouterResponse[];
  /** Indica si los registros se están cargando actualmente. */
  isLoading: boolean;
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
 * Tabla de auditoría e historial en tiempo real que lista las peticiones interceptadas por
 * el AI Cost Router, mostrando usuario, fragmento del prompt, badge de color según el modelo
 * asignado (`gemini-2.5-flash` o `gemini-2.5-pro`), tokens estimados y marca temporal.
 *
 * @param props Lista de logs de peticiones y estado de carga.
 * @returns Sección de tabla responsiva con los registros de auditoría.
 */
export const RequestLogsTable: React.FC<RequestLogsTableProps> = ({ logs, isLoading }) => {
  return (
    <section
      aria-label="Historial de Peticiones Evaluadas"
      className="rounded-xl bg-slate-900/90 border border-slate-800 shadow-lg overflow-hidden"
    >
      <div className="p-6 border-b border-slate-800 flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <FileText className="w-5 h-5 text-emerald-400" aria-hidden="true" />
            Historial de Peticiones en Tiempo Real
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            Registro cronológico de prompts evaluados, tokens calculados y decisiones de enrutamiento.
          </p>
        </div>
        <span
          data-testid="logs-count-badge"
          className="px-3 py-1 rounded-full text-xs font-semibold bg-slate-800 text-slate-300 border border-slate-700"
        >
          {logs.length} {logs.length === 1 ? 'registro' : 'registros'}
        </span>
      </div>

      {isLoading && logs.length === 0 ? (
        <div className="p-10 text-center text-sm text-slate-400">
          Cargando historial de peticiones...
        </div>
      ) : logs.length === 0 ? (
        <div
          data-testid="empty-logs-message"
          className="p-10 text-center text-sm text-slate-400"
        >
          No se encontraron peticiones registradas. Utiliza el simulador para enviar tu primer prompt.
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse" data-testid="request-logs-table">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/60 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                <th className="py-3.5 px-4">Usuario</th>
                <th className="py-3.5 px-4">Fragmento del Prompt</th>
                <th className="py-3.5 px-4">Modelo Asignado</th>
                <th className="py-3.5 px-4 text-right">Tokens Estimados</th>
                <th className="py-3.5 px-4 text-right">Fecha y Hora</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/70 text-sm">
              {logs.map((log) => {
                const isPro = log.modelRouted === 'gemini-2.5-pro';
                return (
                  <tr
                    key={log.id}
                    className="hover:bg-slate-800/40 transition-colors"
                  >
                    <td className="py-3.5 px-4 whitespace-nowrap font-medium text-slate-200">
                      <span className="inline-flex items-center gap-1.5">
                        <User className="w-3.5 h-3.5 text-slate-400" aria-hidden="true" />
                        {log.userId}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 max-w-md">
                      <p
                        className="truncate text-slate-300"
                        title={log.promptContent}
                      >
                        {log.promptContent}
                      </p>
                    </td>
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
                          isPro
                            ? 'bg-purple-500/15 text-purple-300 border-purple-500/30'
                            : 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30'
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
                    <td className="py-3.5 px-4 whitespace-nowrap text-right font-mono text-slate-200">
                      {log.tokensEstimated.toLocaleString('es-ES')}
                    </td>
                    <td className="py-3.5 px-4 whitespace-nowrap text-right text-xs text-slate-400">
                      <span className="inline-flex items-center gap-1 justify-end">
                        <Clock className="w-3 h-3 text-slate-500" aria-hidden="true" />
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
