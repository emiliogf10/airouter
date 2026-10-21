import React from 'react';
import { Cpu, RefreshCw, Search, ShieldCheck, X } from 'lucide-react';
import type { CurrencyCode } from '../types/router';
import { USD_TO_EUR_RATE } from '../utils/currency';

/**
 * Propiedades del componente de cabecera `Header`.
 */
export interface HeaderProps {
  /** Moneda activa seleccionada para la visualización de costes (`'EUR'` por defecto o `'USD'`). */
  currency: CurrencyCode;
  /** Función callback para cambiar la divisa activa en tiempo real. */
  onCurrencyChange: (currency: CurrencyCode) => void;
  /** Valor actual del filtro de usuario (`userId`) aplicado a logs y estadísticas. */
  filterUserId: string;
  /** Función callback que se invoca al modificar el filtro de `userId`. */
  onFilterUserIdChange: (userId: string) => void;
  /** Función callback para forzar la recarga manual de estadísticas e historial desde el backend. */
  onRefresh: () => void;
  /** Indica si hay una petición de actualización de datos en curso. */
  isRefreshing: boolean;
  /** Indica si la conexión con el backend del Gateway está operativa. */
  isGatewayOnline: boolean;
}

/**
 * Cabecera principal del panel de control analítico **AI Cost Router**.
 * Presenta el título de la plataforma, el indicador de estado del API Gateway,
 * el buscador para filtrar métricas por `userId`, el conmutador interactivo de divisa
 * (`EUR €` / `USD $`) y el botón de actualización en tiempo real.
 *
 * @param props Propiedades de configuración e interacción de la cabecera.
 * @returns Elemento JSX con la barra de navegación superior del Dashboard.
 */
export const Header: React.FC<HeaderProps> = ({
  currency,
  onCurrencyChange,
  filterUserId,
  onFilterUserIdChange,
  onRefresh,
  isRefreshing,
  isGatewayOnline,
}) => {
  return (
    <header className="border-b border-slate-800/80 bg-slate-900/90 backdrop-blur-md sticky top-0 z-30">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
        {/* Identidad de marca y estado del Gateway */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-gradient-to-br from-emerald-500/20 to-cyan-500/20 border border-emerald-500/30 text-emerald-400 shadow-inner">
              <Cpu className="w-6 h-6" aria-hidden="true" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h1 className="text-xl font-bold tracking-tight text-white">
                  AI Cost Router
                </h1>
                <span
                  className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border ${
                    isGatewayOnline
                      ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                      : 'bg-amber-500/10 text-amber-400 border-amber-500/30'
                  }`}
                >
                  <span
                    className={`w-1.5 h-1.5 rounded-full ${
                      isGatewayOnline ? 'bg-emerald-400 animate-pulse' : 'bg-amber-400'
                    }`}
                  />
                  {isGatewayOnline ? 'Gateway Activo' : 'Modo Sin Conexión'}
                </span>
              </div>
              <p className="text-xs text-slate-400 flex items-center gap-1.5 mt-0.5">
                <ShieldCheck className="w-3.5 h-3.5 text-cyan-400" aria-hidden="true" />
                Enrutamiento inteligente de LLMs, control de tokens y ahorro en tiempo real
              </p>
            </div>
          </div>
        </div>

        {/* Controles: Filtro por usuario, Selector de divisa EUR/USD y Refresco */}
        <div className="flex flex-wrap items-center gap-3">
          {/* Filtro por userId */}
          <div className="relative flex-1 sm:flex-initial sm:w-64">
            <Search
              className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none"
              aria-hidden="true"
            />
            <input
              type="text"
              value={filterUserId}
              onChange={(e) => onFilterUserIdChange(e.target.value)}
              placeholder="Filtrar por userId (global si vacío)..."
              aria-label="Filtrar por usuario"
              data-testid="user-filter-input"
              className="w-full pl-9 pr-8 py-1.5 text-sm bg-slate-950/90 border border-slate-700/80 rounded-lg text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500 transition"
            />
            {filterUserId && (
              <button
                type="button"
                onClick={() => onFilterUserIdChange('')}
                title="Limpiar filtro de usuario"
                aria-label="Limpiar filtro de usuario"
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-200"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          {/* Conmutador de Divisa (EUR € por defecto | USD $) */}
          <div
            className="inline-flex items-center bg-slate-950 p-1 rounded-lg border border-slate-800"
            role="group"
            aria-label="Selector de moneda"
            title={`Tasa aplicada: 1 USD = ${USD_TO_EUR_RATE} EUR`}
          >
            <button
              type="button"
              data-testid="currency-toggle-eur"
              aria-pressed={currency === 'EUR'}
              onClick={() => onCurrencyChange('EUR')}
              className={`px-3 py-1 rounded-md text-xs font-semibold transition-all cursor-pointer ${
                currency === 'EUR'
                  ? 'bg-emerald-500 text-slate-950 shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              EUR €
            </button>
            <button
              type="button"
              data-testid="currency-toggle-usd"
              aria-pressed={currency === 'USD'}
              onClick={() => onCurrencyChange('USD')}
              className={`px-3 py-1 rounded-md text-xs font-semibold transition-all cursor-pointer ${
                currency === 'USD'
                  ? 'bg-emerald-500 text-slate-950 shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              USD $
            </button>
          </div>

          {/* Botón Actualizar datos */}
          <button
            type="button"
            onClick={onRefresh}
            disabled={isRefreshing}
            data-testid="refresh-button"
            className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-slate-100 border border-slate-700 transition cursor-pointer disabled:opacity-60"
          >
            <RefreshCw
              className={`w-3.5 h-3.5 text-emerald-400 ${isRefreshing ? 'animate-spin' : ''}`}
              aria-hidden="true"
            />
            Actualizar datos
          </button>
        </div>
      </div>
    </header>
  );
};
