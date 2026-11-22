import React, { useEffect, useRef, useState } from 'react';
import {
  Check,
  Clock,
  Coins,
  Copy,
  Database,
  FileText,
  Hash,
  PiggyBank,
  ShieldCheck,
  Sparkles,
  User,
  X,
  Zap,
} from 'lucide-react';
import type { CurrencyCode, RouterResponse } from '../types/router';
import {
  calculateBaselineProCostUsd,
  calculateLogCostUsd,
  formatCurrency,
} from '../utils/currency';

/**
 * Propiedades del visor modal de inspección de peticiones (`LogInspectorDrawer`).
 */
export interface LogInspectorDrawerProps {
  /** Registro seleccionado para inspeccionar en detalle (`null` cuando el modal está cerrado). */
  log: RouterResponse | null;
  /** Divisa activa (`'EUR'` o `'USD'`) para mostrar el desglose financiero individual. */
  currency: CurrencyCode;
  /** Callback ejecutado al cerrar el visor modal. */
  onClose: () => void;
}

/**
 * Variable de alcance de módulo para recordar el último texto anunciado en las regiones
 * `role="status"` de copiado, añadiendo un espacio de no separación (`\u00A0`) cuando se
 * repite el mismo mensaje consecutivamente para que VoiceOver y lectores de pantalla lo anuncien.
 */
let lastAnnouncedCopyMessage = '';

/**
 * Respaldo síncrono de copiado mediante `document.execCommand('copy')` para entornos
 * donde `navigator.clipboard` no esté disponible o sea rechazado.
 *
 * @param text Texto a copiar al portapapeles.
 * @returns `true` si el comando de copiado tuvo éxito, `false` en caso contrario.
 */
function legacyCopyFallback(text: string): boolean {
  const field = Object.assign(document.createElement('textarea'), { value: text });
  field.setAttribute('readonly', '');
  field.className = 'fixed top-0 opacity-0 pointer-events-none';
  document.body.append(field);
  field.select();
  try {
    return typeof document.execCommand === 'function' ? document.execCommand('copy') : false;
  } catch {
    return false;
  } finally {
    field.remove();
  }
}

/**
 * Formatea una cadena de fecha ISO a un formato legible en español (`dd/mm/aaaa hh:mm:ss`).
 *
 * @param isoDate Cadena de fecha en formato ISO-8601 devuelta por el backend.
 * @returns Cadena de fecha y hora formateada.
 */
function formatInspectorDateTime(isoDate: string): string {
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
 * Visor modal de auditoría detallada (`LogInspectorDrawer`) implementado siguiendo las guías
 * oficiales `dialog` y `copy-button` de `modern-web-guidance`:
 * - Utiliza el elemento semántico nativo `<dialog>` abierto con `showModal()` y `closedby="any"`,
 *   sin añadir atributos redundantes `role="dialog"` ni `aria-modal`.
 * - Incluye fallback de cierre ligero (*light-dismiss*) por coordenadas de puntero (`pointerdown` + `pointerup`)
 *   cuando el navegador no soporta nativamente `HTMLDialogElement.prototype.closedBy`.
 * - Incorpora botones de copiado accesibles para el prompt íntegro y la respuesta generada,
 *   con regiones vivas `<span role="status">` situadas fuera de cada `<button>`, ocultas mediante
 *   `opacity` y sin deshabilitar los botones durante la confirmación.
 *
 * @param props Registro seleccionado, divisa activa y callback de cierre.
 * @returns Elemento `<dialog>` nativo con el desglose técnico y financiero de la petición.
 */
export const LogInspectorDrawer: React.FC<LogInspectorDrawerProps> = ({
  log,
  currency,
  onClose,
}) => {
  const dialogRef = useRef<HTMLDialogElement | null>(null);
  const promptTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const responseTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [promptCopied, setPromptCopied] = useState<boolean>(false);
  const [promptStatusText, setPromptStatusText] = useState<string>('');
  const [responseCopied, setResponseCopied] = useState<boolean>(false);
  const [responseStatusText, setResponseStatusText] = useState<string>('');

  // Sincronización de apertura/cierre con la API nativa de <dialog>
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }

    if (log !== null) {
      if (!dialog.open) {
        if (typeof dialog.showModal === 'function') {
          try {
            dialog.showModal();
          } catch {
            dialog.setAttribute('open', '');
          }
        }
        if (!dialog.open) {
          dialog.setAttribute('open', '');
        }
      }
    } else if (dialog.open) {
      if (typeof dialog.close === 'function') {
        try {
          dialog.close();
        } catch {
          dialog.removeAttribute('open');
        }
      }
      dialog.removeAttribute('open');
    }
  }, [log]);

  // Suscripción al evento nativo `close` y fallback de light-dismiss por coordenadas de puntero
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }

    const handleNativeClose = () => {
      dialog.returnValue = '';
      onClose();
    };

    dialog.addEventListener('close', handleNativeClose);

    let cleanupLightDismiss: (() => void) | undefined;
    if (
      typeof HTMLDialogElement !== 'undefined' &&
      !('closedBy' in HTMLDialogElement.prototype)
    ) {
      let pressedOutside = false;
      const isOutside = (event: PointerEvent): boolean => {
        const rect = dialog.getBoundingClientRect();
        if (rect.width === 0 && rect.height === 0) {
          return false;
        }
        return (
          event.clientX < rect.left ||
          event.clientX > rect.right ||
          event.clientY < rect.top ||
          event.clientY > rect.bottom
        );
      };

      const onPointerDown = (event: PointerEvent) => {
        pressedOutside = isOutside(event);
      };

      const onPointerUp = (event: PointerEvent) => {
        if (pressedOutside && isOutside(event)) {
          if (typeof dialog.close === 'function') {
            dialog.close();
          } else {
            dialog.removeAttribute('open');
            onClose();
          }
        }
      };

      dialog.addEventListener('pointerdown', onPointerDown);
      dialog.addEventListener('pointerup', onPointerUp);
      cleanupLightDismiss = () => {
        dialog.removeEventListener('pointerdown', onPointerDown);
        dialog.removeEventListener('pointerup', onPointerUp);
      };
    }

    return () => {
      dialog.removeEventListener('close', handleNativeClose);
      cleanupLightDismiss?.();
    };
  }, [onClose]);

  // Limpieza de temporizadores de feedback al desmontar o cambiar de registro
  useEffect(() => {
    setPromptCopied(false);
    setPromptStatusText('');
    setResponseCopied(false);
    setResponseStatusText('');
    return () => {
      if (promptTimerRef.current) clearTimeout(promptTimerRef.current);
      if (responseTimerRef.current) clearTimeout(responseTimerRef.current);
    };
  }, [log]);

  /**
   * Cierra el diálogo invocando el método nativo `close()` y notificando al componente padre.
   */
  const handleCloseButton = () => {
    const dialog = dialogRef.current;
    if (dialog && dialog.open && typeof dialog.close === 'function') {
      try {
        dialog.close();
      } catch {
        dialog.removeAttribute('open');
        onClose();
      }
      if (dialog.open) {
        dialog.removeAttribute('open');
        onClose();
      }
    } else {
      dialog?.removeAttribute('open');
      onClose();
    }
  };

  /**
   * Copia el texto indicado al portapapeles utilizando la API asíncrona `navigator.clipboard.writeText`
   * (con respaldo síncrono `legacyCopyFallback`) y actualiza la región viva `role="status"` correspondiente.
   *
   * @param targetKind Identificador del bloque copiado (`'prompt'` o `'response'`).
   * @param textToCopy Contenido textual íntegro a copiar.
   */
  const handleCopyContent = async (
    targetKind: 'prompt' | 'response',
    textToCopy: string,
  ) => {
    if (!textToCopy) {
      return;
    }

    let copySucceeded = false;
    try {
      if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
        await navigator.clipboard.writeText(textToCopy);
        copySucceeded = true;
      } else {
        copySucceeded = legacyCopyFallback(textToCopy);
      }
    } catch {
      copySucceeded = legacyCopyFallback(textToCopy);
    }

    let message = copySucceeded ? 'Copiado' : 'Error al copiar';
    if (message === lastAnnouncedCopyMessage) {
      message += '\u00A0';
    }
    lastAnnouncedCopyMessage = message;

    if (targetKind === 'prompt') {
      if (promptTimerRef.current) clearTimeout(promptTimerRef.current);
      setPromptCopied(copySucceeded);
      setPromptStatusText(message);
      promptTimerRef.current = setTimeout(() => {
        setPromptCopied(false);
        setPromptStatusText('');
      }, 2000);
    } else {
      if (responseTimerRef.current) clearTimeout(responseTimerRef.current);
      setResponseCopied(copySucceeded);
      setResponseStatusText(message);
      responseTimerRef.current = setTimeout(() => {
        setResponseCopied(false);
        setResponseStatusText('');
      }, 2000);
    }
  };

  const actualCostUsd = log
    ? calculateLogCostUsd(log.modelRouted, log.tokensEstimated, log.cacheHit)
    : 0;
  const baselineProCostUsd = log ? calculateBaselineProCostUsd(log.tokensEstimated) : 0;
  const savedCostUsd = Math.max(0, Number((baselineProCostUsd - actualCostUsd).toFixed(6)));
  const isProModel = log?.modelRouted === 'gemini-2.5-pro';

  return (
    <dialog
      ref={dialogRef}
      id="log-inspector-dialog"
      aria-labelledby="inspector-dialog-title"
      data-testid="log-inspector-drawer"
      {...({ closedby: 'any' } as Record<string, string>)}
      className="m-auto w-full max-w-2xl rounded-2xl bg-zinc-900 text-zinc-100 border border-white/15 p-0 shadow-2xl open:flex open:flex-col max-h-[88vh]"
    >
      {log && (
        <div className="flex flex-col overflow-hidden max-h-[88vh]">
          {/* Cabecera del Inspector */}
          <div className="p-6 border-b border-white/10 flex items-start justify-between gap-4 bg-zinc-950/60">
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <FileText className="w-5 h-5 text-indigo-400" aria-hidden="true" />
                <h2
                  id="inspector-dialog-title"
                  className="text-lg font-semibold tracking-tight text-zinc-100"
                >
                  Detalle de Petición Enrutada
                </h2>
              </div>
              <p className="text-xs font-mono text-zinc-400 mt-1 break-all">
                ID: {log.id}
              </p>
            </div>

            <button
              type="button"
              data-testid="inspector-close-button"
              onClick={handleCloseButton}
              autoFocus
              aria-label="Cerrar inspector de petición"
              className="min-w-8 min-h-8 p-1.5 rounded-lg bg-zinc-800/80 hover:bg-zinc-800 text-zinc-300 border border-white/10 transition cursor-pointer inline-flex items-center justify-center"
            >
              <X className="w-4 h-4" aria-hidden="true" />
            </button>
          </div>

          {/* Cuerpo desplazable del Inspector */}
          <div className="p-6 overflow-y-auto space-y-6">
            {/* Grid de metadatos operativos y financieros */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
              {/* Usuario */}
              <div className="p-3.5 rounded-xl bg-zinc-950/70 border border-white/10">
                <span className="text-[11px] font-medium text-zinc-400 flex items-center gap-1.5">
                  <User className="w-3.5 h-3.5 text-zinc-500" aria-hidden="true" />
                  Usuario
                </span>
                <p className="text-sm font-semibold text-zinc-100 mt-1 truncate">
                  {log.userId}
                </p>
              </div>

              {/* Fecha y Hora */}
              <div className="p-3.5 rounded-xl bg-zinc-950/70 border border-white/10">
                <span className="text-[11px] font-medium text-zinc-400 flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 text-zinc-500" aria-hidden="true" />
                  Fecha de Registro
                </span>
                <p className="text-sm font-mono text-zinc-200 mt-1">
                  {formatInspectorDateTime(log.createdAt)}
                </p>
              </div>

              {/* Modelo y Estado de Caché */}
              <div className="p-3.5 rounded-xl bg-zinc-950/70 border border-white/10">
                <span className="text-[11px] font-medium text-zinc-400 flex items-center gap-1.5">
                  {isProModel ? (
                    <Sparkles className="w-3.5 h-3.5 text-indigo-400" aria-hidden="true" />
                  ) : (
                    <Zap className="w-3.5 h-3.5 text-emerald-400" aria-hidden="true" />
                  )}
                  Modelo y Caché
                </span>
                <div className="flex flex-wrap items-center gap-1.5 mt-1">
                  <span className="text-xs font-semibold text-zinc-100">
                    {log.modelRouted}
                  </span>
                  {log.cacheHit ? (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                      <Database className="w-3 h-3" aria-hidden="true" />
                      Caché Hit (0 €)
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 rounded-full text-[11px] bg-zinc-800 text-zinc-300">
                      Tráfico Estándar
                    </span>
                  )}
                </div>
              </div>

              {/* Tokens Estimados */}
              <div className="p-3.5 rounded-xl bg-zinc-950/70 border border-white/10">
                <span className="text-[11px] font-medium text-zinc-400 flex items-center gap-1.5">
                  <Hash className="w-3.5 h-3.5 text-zinc-500" aria-hidden="true" />
                  Tokens Estimados
                </span>
                <p className="text-base font-bold font-mono text-zinc-100 mt-1">
                  {log.tokensEstimated.toLocaleString('es-ES')} tokens
                </p>
              </div>

              {/* Coste Individual Facturado */}
              <div className="p-3.5 rounded-xl bg-zinc-950/70 border border-white/10">
                <span className="text-[11px] font-medium text-zinc-400 flex items-center gap-1.5">
                  <Coins className="w-3.5 h-3.5 text-amber-400" aria-hidden="true" />
                  Coste Facturado
                </span>
                <p
                  data-testid="inspector-actual-cost"
                  className="text-base font-bold font-mono text-zinc-100 mt-1"
                >
                  {formatCurrency(actualCostUsd, currency)}
                </p>
              </div>

              {/* Ahorro Individual Generado */}
              <div className="p-3.5 rounded-xl bg-zinc-950/70 border border-emerald-500/20">
                <span className="text-[11px] font-medium text-emerald-400 flex items-center gap-1.5">
                  <PiggyBank className="w-3.5 h-3.5 text-emerald-400" aria-hidden="true" />
                  Ahorro vs Base Pro
                </span>
                <p
                  data-testid="inspector-saved-cost"
                  className="text-base font-bold font-mono text-emerald-400 mt-1"
                >
                  {formatCurrency(savedCostUsd, currency)}
                </p>
              </div>

              {/* Latencia de Inferencia */}
              <div className="p-3.5 rounded-xl bg-zinc-950/70 border border-white/10">
                <span className="text-[11px] font-medium text-zinc-400 flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 text-indigo-400" aria-hidden="true" />
                  Latencia de Inferencia
                </span>
                <p
                  data-testid="inspector-latency"
                  className="text-base font-bold font-mono text-zinc-100 mt-1"
                >
                  {log.latencyMs ?? 0} ms
                </p>
              </div>

              {/* Escudo DLP de Privacidad */}
              <div className="p-3.5 rounded-xl bg-zinc-950/70 border border-white/10 sm:col-span-2">
                <span className="text-[11px] font-medium text-amber-400 flex items-center gap-1.5">
                  <ShieldCheck className="w-3.5 h-3.5 text-amber-400" aria-hidden="true" />
                  Escudo DLP de Privacidad
                </span>
                <p
                  data-testid="inspector-dlp-status"
                  className="text-sm font-semibold text-zinc-100 mt-1"
                >
                  {(log.redactedItemsCount ?? 0) > 0
                    ? `${log.redactedItemsCount} elementos PII/secretos anonimizados`
                    : 'Sin datos sensibles detectados'}
                </p>
              </div>
            </div>

            {/* Bloque del Prompt Íntegro con Copy Button accesible */}
            <div className="space-y-2">
              <div className="flex items-center justify-between gap-2 flex-wrap">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-zinc-400">
                  Prompt Íntegro Evaluado
                </h3>
                <span className="inline-flex items-center gap-2">
                  <button
                    type="button"
                    data-testid="inspector-copy-prompt-btn"
                    data-copied={promptCopied ? '' : undefined}
                    onClick={() => void handleCopyContent('prompt', log.promptContent)}
                    className="min-w-6 min-h-6 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium bg-zinc-800/90 hover:bg-zinc-800 text-zinc-200 border border-white/10 transition cursor-pointer"
                  >
                    {promptCopied ? (
                      <Check className="w-3.5 h-3.5 text-emerald-400" aria-hidden="true" />
                    ) : (
                      <Copy className="w-3.5 h-3.5 text-zinc-400" aria-hidden="true" />
                    )}
                    <span>Copiar prompt</span>
                  </button>
                  <span
                    role="status"
                    data-testid="inspector-copy-prompt-status"
                    className={`text-xs font-medium text-emerald-400 transition-opacity ${
                      promptStatusText ? 'opacity-100' : 'opacity-0'
                    }`}
                  >
                    {promptStatusText}
                  </span>
                </span>
              </div>

              <pre
                id="inspector-prompt-pre"
                data-testid="inspector-full-prompt"
                className="p-4 rounded-xl bg-zinc-950 border border-white/10 text-xs font-mono text-zinc-200 whitespace-pre-wrap break-words leading-relaxed max-h-48 overflow-y-auto"
              >
                {log.promptContent}
              </pre>
            </div>

            {/* Bloque de la Respuesta Íntegra con Copy Button accesible */}
            <div className="space-y-2">
              <div className="flex items-center justify-between gap-2 flex-wrap">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-zinc-400">
                  Respuesta Generada por el Gateway / LLM
                </h3>
                <span className="inline-flex items-center gap-2">
                  <button
                    type="button"
                    data-testid="inspector-copy-response-btn"
                    data-copied={responseCopied ? '' : undefined}
                    onClick={() =>
                      void handleCopyContent(
                        'response',
                        log.simulatedResponse ?? 'Sin respuesta registrada',
                      )
                    }
                    className="min-w-6 min-h-6 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium bg-zinc-800/90 hover:bg-zinc-800 text-zinc-200 border border-white/10 transition cursor-pointer"
                  >
                    {responseCopied ? (
                      <Check className="w-3.5 h-3.5 text-emerald-400" aria-hidden="true" />
                    ) : (
                      <Copy className="w-3.5 h-3.5 text-zinc-400" aria-hidden="true" />
                    )}
                    <span>Copiar respuesta</span>
                  </button>
                  <span
                    role="status"
                    data-testid="inspector-copy-response-status"
                    className={`text-xs font-medium text-emerald-400 transition-opacity ${
                      responseStatusText ? 'opacity-100' : 'opacity-0'
                    }`}
                  >
                    {responseStatusText}
                  </span>
                </span>
              </div>

              <pre
                id="inspector-response-pre"
                data-testid="inspector-full-response"
                className="p-4 rounded-xl bg-zinc-950 border border-white/10 text-xs font-mono text-zinc-200 whitespace-pre-wrap break-words leading-relaxed max-h-56 overflow-y-auto"
              >
                {log.simulatedResponse ?? 'Sin respuesta registrada'}
              </pre>
            </div>
          </div>
        </div>
      )}
    </dialog>
  );
};
