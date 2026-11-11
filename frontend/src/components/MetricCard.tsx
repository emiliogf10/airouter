import React from 'react';
import type { LucideIcon } from 'lucide-react';

/**
 * Propiedades del componente modular `MetricCard` (inspirado en Vercel / shadcn/ui).
 */
export interface MetricCardProps {
  /** Título descriptivo de la métrica (ej. "Peticiones Totales"). */
  title: string;
  /** Valor principal formateado de la métrica (se muestra con jerarquía `text-3xl font-bold`). */
  value: string;
  /** Icono de `lucide-react` representativo del indicador. */
  icon: LucideIcon;
  /** Contenido secundario o comparativa inferior (nodo React o texto explicativo). */
  footer?: React.ReactNode;
  /** Badge opcional junto al valor principal (ej. porcentaje de ahorro). */
  badge?: React.ReactNode;
  /** Indica si los datos están cargando para mostrar un Skeleton Loader elegante (`animate-pulse`). */
  isLoading?: boolean;
  /** Variante sutil de acento para el icono (`'zinc'`, `'emerald'` o `'indigo'`). */
  accent?: 'zinc' | 'emerald' | 'indigo';
  /** Identificador `data-testid` para el contenedor de la tarjeta. */
  testId?: string;
  /** Identificador `data-testid` alternativo en un contenedor interno para compatibilidad de pruebas. */
  aliasTestId?: string;
  /** Identificador `data-testid` para el elemento que muestra el valor principal. */
  valueTestId?: string;
}

/**
 * Mapa de estilos Tailwind CSS v4 para el contenedor del icono según el acento cromático.
 */
const ACCENT_ICON_CLASSES: Record<NonNullable<MetricCardProps['accent']>, string> = {
  zinc: 'bg-zinc-800/80 text-zinc-300 border-white/10',
  emerald: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
  indigo: 'bg-indigo-500/10 text-indigo-400 border-indigo-500/20',
};

/**
 * Tarjeta modular reutilizable (`MetricCard`) con estética minimalista estilo Vercel / shadcn/ui.
 * Presenta una métrica clave (KPI) con jerarquía tipográfica clara (`text-3xl font-bold`),
 * bordes sutiles (`border-white/10`), espaciado amplio (`p-6`) y soporte nativo para estado
 * de carga mediante Skeleton Loaders (`animate-pulse`).
 *
 * @param props Propiedades visuales y de datos de la tarjeta KPI.
 * @returns Elemento JSX con la tarjeta de métrica renderizada.
 */
export const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  icon: Icon,
  footer,
  badge,
  isLoading = false,
  accent = 'zinc',
  testId,
  aliasTestId,
  valueTestId,
}) => {
  return (
    <article
      data-testid={testId}
      className="rounded-xl bg-zinc-900/60 border border-white/10 p-6 shadow-sm hover:border-white/15 transition-colors flex flex-col justify-between"
    >
      <div data-testid={aliasTestId} className="flex flex-col justify-between h-full gap-4">
        {/* Cabecera de la tarjeta: Título + Icono */}
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-sm font-medium text-zinc-400 tracking-tight">{title}</h3>
          <div
            className={`p-2 rounded-lg border ${ACCENT_ICON_CLASSES[accent]} shrink-0`}
          >
            <Icon className="w-4 h-4" aria-hidden="true" />
          </div>
        </div>

        {/* Cuerpo: Valor principal o Skeleton Loader */}
        {isLoading ? (
          <div
            data-testid={testId ? `${testId}-skeleton` : 'metric-card-skeleton'}
            className="space-y-3 py-1 animate-pulse"
            aria-busy="true"
            aria-label={`Cargando ${title}`}
          >
            <div className="h-8 w-36 rounded-md bg-zinc-800/90" />
            <div className="h-4 w-48 rounded-md bg-zinc-800/60" />
          </div>
        ) : (
          <>
            <div className="flex items-baseline gap-2.5 flex-wrap">
              <p
                data-testid={valueTestId}
                className="text-3xl font-bold tracking-tight text-zinc-50 font-mono"
              >
                {value}
              </p>
              {badge}
            </div>

            {footer && (
              <div className="pt-3 border-t border-white/5 text-sm text-zinc-400">
                {footer}
              </div>
            )}
          </>
        )}
      </div>
    </article>
  );
};
