import React, { useEffect, useState } from 'react';
import {
  AlertTriangle,
  Ban,
  CheckCircle2,
  Infinity as InfinityIcon,
  ShieldAlert,
  SlidersHorizontal,
  Trash2,
  Wallet,
} from 'lucide-react';
import { updateUserBudget } from '../services/api';
import type {
  BudgetStatus,
  CurrencyCode,
  UserBudgetResponse,
} from '../types/router';
import {
  convertFromUsd,
  convertToUsd,
  formatCurrency,
} from '../utils/currency';

/**
 * Propiedades del panel de control presupuestario FinOps (`BudgetControlCard`).
 */
export interface BudgetControlCardProps {
  /** Estado actual del presupuesto del usuario devuelto por el backend. */
  budget: UserBudgetResponse;
  /** Divisa activa (`'EUR'` por defecto o `'USD'`). */
  currency: CurrencyCode;
  /** Callback ejecutado cuando se actualiza o elimina el presupuesto para sincronizar el Dashboard. */
  onBudgetUpdated: (updated: UserBudgetResponse) => void;
}

/**
 * Devuelve la configuración visual y el texto descriptivo para cada estado del presupuesto (`BudgetStatus`).
 *
 * @param status Estado actual del cortafuegos presupuestario.
 * @returns Etiqueta en español, clases Tailwind del badge y color de la barra SVG.
 */
function getStatusPresentation(status: BudgetStatus): {
  label: string;
  badgeClasses: string;
  barFillClass: string;
} {
  switch (status) {
    case 'OK':
      return {
        label: 'Presupuesto Activo (OK)',
        badgeClasses: 'bg-emerald-500/10 text-emerald-300 border-emerald-500/25',
        barFillClass: 'fill-emerald-500',
      };
    case 'WARNING_80_DOWNGRADE':
      return {
        label: 'Modo Ahorro Forzado (>=80% → Flash)',
        badgeClasses: 'bg-amber-500/10 text-amber-300 border-amber-500/30',
        barFillClass: 'fill-amber-400',
      };
    case 'EXCEEDED_100_BLOCKED':
      return {
        label: 'Cortafuegos Activo (100% Bloqueado)',
        badgeClasses: 'bg-rose-500/15 text-rose-300 border-rose-500/35',
        barFillClass: 'fill-rose-500',
      };
    case 'UNLIMITED':
    default:
      return {
        label: 'Sin límite (Ilimitado)',
        badgeClasses: 'bg-zinc-800/80 text-zinc-300 border-white/10',
        barFillClass: 'fill-indigo-500',
      };
  }
}

/**
 * Componente de control presupuestario FinOps (`BudgetControlCard`) con diseño minimalista
 * estilo Vercel / shadcn/ui y construido íntegramente con clases de utilidad Tailwind CSS v4
 * y geometría SVG nativa.
 * Permite monitorizar el consumo respecto al presupuesto máximo del usuario (`0% - 80% - 100%`),
 * fijar límites en la divisa activa (`EUR €` o `USD $`), habilitar la degradación automática
 * a `gemini-2.5-flash` al alcanzar el 80% y eliminar el límite cuando se requiera.
 *
 * @param props Estado del presupuesto, divisa activa y callback de actualización.
 * @returns Sección interactiva de gobernanza FinOps por usuario.
 */
export const BudgetControlCard: React.FC<BudgetControlCardProps> = ({
  budget,
  currency,
  onBudgetUpdated,
}) => {
  const [limitInput, setLimitInput] = useState<string>('');
  const [autoDowngrade, setAutoDowngrade] = useState<boolean>(budget.autoDowngradeEnabled);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [feedbackError, setFeedbackError] = useState<string | null>(null);

  useEffect(() => {
    setAutoDowngrade(budget.autoDowngradeEnabled);
    if (budget.maxBudgetUsd !== null && budget.maxBudgetUsd > 0) {
      const converted = convertFromUsd(budget.maxBudgetUsd, currency);
      setLimitInput(converted.toFixed(6));
    } else {
      setLimitInput('');
    }
  }, [budget.maxBudgetUsd, budget.autoDowngradeEnabled, budget.userId, currency]);

  const presentation = getStatusPresentation(budget.status);
  const clampedPercentage = Math.min(100, Math.max(0, budget.usagePercentage || 0));

  /**
   * Envía al backend la actualización del presupuesto convirtiendo el importe desde la divisa activa a USD.
   *
   * @param amountInActiveCurrency Límite en la divisa activa (`null` para quitar el límite).
   * @param downgradeFlag          Estado del interruptor de auto-downgrade al 80%.
   */
  const submitBudgetChange = async (
    amountInActiveCurrency: number | null,
    downgradeFlag: boolean,
  ) => {
    setFeedbackError(null);
    setIsSaving(true);
    try {
      const maxBudgetUsd =
        amountInActiveCurrency !== null && amountInActiveCurrency > 0
          ? convertToUsd(amountInActiveCurrency, currency)
          : null;

      const updated = await updateUserBudget(budget.userId, {
        maxBudgetUsd,
        autoDowngradeEnabled: downgradeFlag,
      });
      onBudgetUpdated(updated);
    } catch (err) {
      setFeedbackError(
        err instanceof Error
          ? err.message
          : 'No se pudo actualizar el presupuesto del usuario.',
      );
    } finally {
      setIsSaving(false);
    }
  };

  /**
   * Gestiona el guardado del formulario de límite presupuestario.
   *
   * @param event Evento de envío del formulario.
   */
  const handleFormSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const normalized = limitInput.trim().replace(',', '.');
    if (normalized === '') {
      await submitBudgetChange(null, autoDowngrade);
      return;
    }

    const parsed = Number.parseFloat(normalized);
    if (Number.isNaN(parsed) || parsed < 0) {
      setFeedbackError('Introduce un importe numérico válido mayor o igual a 0.');
      return;
    }

    await submitBudgetChange(parsed > 0 ? parsed : null, autoDowngrade);
  };

  return (
    <section
      aria-label="Control de Presupuesto FinOps por Usuario"
      data-testid="budget-control-card"
      className="rounded-xl bg-zinc-900/60 border border-white/10 p-6 shadow-sm space-y-5"
    >
      {/* Cabecera: Título, usuario gestionado y Badge de Estado */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <div className="flex items-center gap-2.5 flex-wrap">
            <Wallet className="w-5 h-5 text-emerald-400" aria-hidden="true" />
            <h2 className="text-xl font-semibold tracking-tight text-zinc-100">
              Cortafuegos Presupuestario FinOps
            </h2>
            <span className="px-2.5 py-0.5 rounded-md text-xs font-mono bg-zinc-800/90 text-zinc-300 border border-white/10">
              Usuario: <strong data-testid="budget-active-user">{budget.userId}</strong>
            </span>
          </div>
          <p className="text-sm text-zinc-400 mt-1">
            Controla el techo de gasto por usuario: degradación preventiva al 80% y bloqueo estricto (HTTP 402) al 100%.
          </p>
        </div>

        <span
          data-testid="budget-status-badge"
          className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium border self-start sm:self-auto ${presentation.badgeClasses}`}
        >
          {budget.status === 'UNLIMITED' && (
            <InfinityIcon className="w-3.5 h-3.5" aria-hidden="true" />
          )}
          {budget.status === 'OK' && (
            <CheckCircle2 className="w-3.5 h-3.5" aria-hidden="true" />
          )}
          {budget.status === 'WARNING_80_DOWNGRADE' && (
            <AlertTriangle className="w-3.5 h-3.5" aria-hidden="true" />
          )}
          {budget.status === 'EXCEEDED_100_BLOCKED' && (
            <Ban className="w-3.5 h-3.5" aria-hidden="true" />
          )}
          {presentation.label}
        </span>
      </div>

      {/* Métricas de consumo y barra de progreso SVG 0% - 80% - 100% */}
      <div className="p-4 rounded-xl bg-zinc-950/60 border border-white/5 space-y-3">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-sm">
          <div>
            <span className="text-xs text-zinc-400 block">Gasto Acumulado</span>
            <p
              data-testid="budget-current-spend"
              className="text-lg font-bold font-mono text-zinc-100 mt-0.5"
            >
              {formatCurrency(budget.currentSpendUsd, currency)}
            </p>
          </div>

          <div>
            <span className="text-xs text-zinc-400 block">Límite Máximo</span>
            <p
              data-testid="budget-max-limit"
              className="text-lg font-bold font-mono text-zinc-100 mt-0.5"
            >
              {budget.maxBudgetUsd !== null && budget.maxBudgetUsd > 0
                ? formatCurrency(budget.maxBudgetUsd, currency)
                : 'Sin límite (Ilimitado)'}
            </p>
          </div>

          <div className="sm:text-right">
            <span className="text-xs text-zinc-400 block">Consumo de Cuota</span>
            <p
              data-testid="budget-usage-percentage"
              className="text-lg font-bold font-mono text-zinc-100 mt-0.5"
            >
              {budget.maxBudgetUsd !== null && budget.maxBudgetUsd > 0
                ? `${budget.usagePercentage.toFixed(1)}%`
                : '0.0%'}
            </p>
          </div>
        </div>

        {/* Barra de progreso SVG con marcador del 80% */}
        <div className="space-y-1.5">
          <svg
            viewBox="0 0 100 6"
            preserveAspectRatio="none"
            className="w-full h-2.5 rounded-full overflow-hidden bg-zinc-900 border border-white/10"
            role="img"
            aria-label={`Uso de presupuesto: ${budget.usagePercentage.toFixed(1)}%`}
          >
            <rect
              x={0}
              y={0}
              width={budget.maxBudgetUsd ? clampedPercentage : 0}
              height={6}
              className={`${presentation.barFillClass} transition-all duration-500`}
            />
            <rect x={80} y={0} width={0.6} height={6} className="fill-zinc-400/70" />
          </svg>
          <div className="flex items-center justify-between text-[11px] text-gray-500 font-mono">
            <span>0%</span>
            <span>80% (Umbral Auto-Downgrade a Flash)</span>
            <span>100% (Hard Cap HTTP 402)</span>
          </div>
        </div>
      </div>

      {/* Avisos contextuales en estados críticos */}
      {budget.status === 'WARNING_80_DOWNGRADE' && (
        <div
          data-testid="budget-warning-banner"
          className="p-3.5 rounded-xl bg-amber-950/30 border border-amber-500/30 text-amber-200 text-xs flex items-center gap-2.5"
        >
          <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0" aria-hidden="true" />
          <span>
            El usuario ha superado el <strong>80%</strong> de su presupuesto. Todas las peticiones complejas se desvían automáticamente a <code className="font-mono text-amber-300">gemini-2.5-flash</code> para proteger su saldo.
          </span>
        </div>
      )}

      {budget.status === 'EXCEEDED_100_BLOCKED' && (
        <div
          data-testid="budget-blocked-banner"
          className="p-3.5 rounded-xl bg-rose-950/40 border border-rose-500/35 text-rose-200 text-xs flex items-center gap-2.5"
        >
          <ShieldAlert className="w-4 h-4 text-rose-400 shrink-0" aria-hidden="true" />
          <span>
            Presupuesto agotado al <strong>100%</strong>. El cortafuegos rechaza nuevas consultas con estado <strong>HTTP 402 Payment Required</strong> hasta ampliar o eliminar el límite.
          </span>
        </div>
      )}

      {/* Controles de configuración del presupuesto */}
      <form
        onSubmit={handleFormSubmit}
        className="pt-2 border-t border-white/5 flex flex-col lg:flex-row lg:items-end justify-between gap-4"
        noValidate
      >
        <div className="flex-1 grid grid-cols-1 sm:grid-cols-2 gap-4 items-center">
          <div>
            <label
              htmlFor="budget-limit-input"
              className="block text-xs font-medium text-zinc-300 mb-1.5"
            >
              Fijar Límite Máximo ({currency === 'EUR' ? 'EUR €' : 'USD $'})
            </label>
            <div className="relative">
              <SlidersHorizontal
                className="w-4 h-4 text-zinc-500 absolute left-3 top-1/2 -translate-y-1/2"
                aria-hidden="true"
              />
              <input
                id="budget-limit-input"
                data-testid="budget-limit-input"
                type="number"
                step="0.000001"
                min="0"
                value={limitInput}
                onChange={(e) => setLimitInput(e.target.value)}
                placeholder={currency === 'EUR' ? '0.001000 € (vacío = ilimitado)' : '$0.001000'}
                className="w-full pl-9 pr-3 py-1.5 text-sm font-mono bg-zinc-950/80 border border-white/10 rounded-lg text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/40 transition"
              />
            </div>
          </div>

          <label
            htmlFor="budget-autodowngrade-checkbox"
            className="inline-flex items-center gap-2.5 text-xs text-zinc-300 cursor-pointer select-none pt-2 sm:pt-5"
          >
            <input
              id="budget-autodowngrade-checkbox"
              data-testid="budget-autodowngrade-checkbox"
              type="checkbox"
              checked={autoDowngrade}
              onChange={(e) => setAutoDowngrade(e.target.checked)}
              className="w-4 h-4 rounded border-white/20 bg-zinc-950 text-emerald-500 focus:ring-emerald-500/40"
            />
            <span>Degradación automática al 80% (Auto-Downgrade a Flash)</span>
          </label>
        </div>

        {/* Botones de acción y presets */}
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            data-testid="budget-preset-micro"
            disabled={isSaving}
            onClick={() => void submitBudgetChange(0.001, autoDowngrade)}
            className="px-2.5 py-1.5 rounded-lg text-xs font-medium bg-zinc-800/80 hover:bg-zinc-800 text-zinc-300 border border-white/10 transition cursor-pointer disabled:opacity-50"
          >
            {currency === 'EUR' ? 'Micro (0,001000 €)' : 'Micro ($0.001000)'}
          </button>

          <button
            type="button"
            data-testid="budget-preset-standard"
            disabled={isSaving}
            onClick={() => void submitBudgetChange(1.0, autoDowngrade)}
            className="px-2.5 py-1.5 rounded-lg text-xs font-medium bg-zinc-800/80 hover:bg-zinc-800 text-zinc-300 border border-white/10 transition cursor-pointer disabled:opacity-50"
          >
            {currency === 'EUR' ? 'Estándar (1,00 €)' : 'Estándar ($1.00)'}
          </button>

          <button
            type="button"
            data-testid="budget-clear-button"
            disabled={isSaving}
            onClick={() => {
              setLimitInput('');
              void submitBudgetChange(null, autoDowngrade);
            }}
            className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs font-medium bg-zinc-900 hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 border border-white/10 transition cursor-pointer disabled:opacity-50"
          >
            <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
            Quitar límite
          </button>

          <button
            type="submit"
            data-testid="budget-save-button"
            disabled={isSaving}
            className="px-3.5 py-1.5 rounded-lg text-xs font-medium bg-zinc-100 hover:bg-white text-zinc-950 shadow-sm transition cursor-pointer disabled:opacity-60"
          >
            {isSaving ? 'Guardando...' : 'Guardar límite'}
          </button>
        </div>
      </form>

      {feedbackError && (
        <p role="alert" className="text-xs text-rose-400">
          {feedbackError}
        </p>
      )}
    </section>
  );
};
