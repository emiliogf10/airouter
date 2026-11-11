import React, { useEffect, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  Cpu,
  KeyRound,
  Send,
  Sparkles,
  Terminal,
  User,
  Zap,
} from 'lucide-react';
import { ApiError, sendPrompt } from '../services/api';
import type { RouterResponse } from '../types/router';

/**
 * Propiedades del simulador interactivo de prompts (`PromptPlayground`).
 */
export interface PromptPlaygroundProps {
  /** Callback ejecutado tras procesar un prompt exitosamente para refrescar KPIs e historial. */
  onPromptSuccess: (response: RouterResponse) => void;
  /** Contador o señal externa opcional para cargar automáticamente un prompt de ejemplo desde otros componentes. */
  quickTrySignal?: number;
  /** Callback opcional invocado cuando cambia el usuario del simulador o se produce un bloqueo presupuestario (402). */
  onUserOrBudgetActivity?: (userId: string) => void;
}

/** Ejemplo predefinido de baja complejidad para activar el enrutamiento hacia `gemini-2.5-flash`. */
export const SIMPLE_PROMPT_EXAMPLE =
  'Hola, ¿puedes darme tres consejos breves para organizar mi agenda diaria?';

/** Ejemplo predefinido de alta complejidad técnica para activar el enrutamiento hacia `gemini-2.5-pro`. */
export const COMPLEX_PROMPT_EXAMPLE =
  'Necesito diseñar una arquitectura de microservicios resiliente y analizar en profundidad cómo optimizar y refactorizar el balanceo de carga distribuido.';

/** Prefijo estándar que identifica una respuesta generada por el motor de simulación local. */
const SIMULATION_RESPONSE_PREFIX = '[Simulación AI Cost Router]';

/**
 * Consola interactiva (`PromptPlayground`) con diseño minimalista inspirado en Vercel / shadcn/ui
 * para evaluar en vivo las reglas de negocio del enrutador inteligente (`POST /api/v1/router/prompt`).
 * Soporta **Modo Híbrido (BYOK)** y manejo específico de alertas para validación (HTTP 400),
 * **Cortafuegos Presupuestario FinOps (HTTP 402 Payment Required)** y Rate Limiting (HTTP 429).
 *
 * @param props Propiedades del componente.
 * @returns Tarjeta interactiva con formulario de envío y panel de resultado/error.
 */
export const PromptPlayground: React.FC<PromptPlaygroundProps> = ({
  onPromptSuccess,
  quickTrySignal = 0,
  onUserOrBudgetActivity,
}) => {
  const [userId, setUserId] = useState<string>('demo-user');
  const [apiKey, setApiKey] = useState<string>('');
  const [prompt, setPrompt] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [lastResult, setLastResult] = useState<RouterResponse | null>(null);
  const [errorState, setErrorState] = useState<{
    message: string;
    status?: number;
    retryAfterSeconds?: number;
  } | null>(null);

  useEffect(() => {
    if (quickTrySignal > 0) {
      setPrompt(SIMPLE_PROMPT_EXAMPLE);
      setErrorState(null);
    }
  }, [quickTrySignal]);

  /**
   * Gestiona el envío del formulario de evaluación de prompt hacia el backend.
   *
   * @param event Evento de envío del formulario HTML.
   */
  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setErrorState(null);

    const trimmedUser = userId.trim();
    const trimmedPrompt = prompt.trim();

    if (!trimmedUser || !trimmedPrompt) {
      setErrorState({
        message: 'Por favor, completa tanto el identificador de usuario como el contenido del prompt.',
        status: 400,
      });
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await sendPrompt(
        { userId: trimmedUser, prompt: trimmedPrompt },
        apiKey,
      );
      setLastResult(result);
      onPromptSuccess(result);
      onUserOrBudgetActivity?.(trimmedUser);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorState({
          message: err.message,
          status: err.status,
          retryAfterSeconds: err.retryAfterSeconds,
        });
        if (err.status === 402) {
          onUserOrBudgetActivity?.(trimmedUser);
        }
      } else if (err instanceof Error) {
        setErrorState({
          message: err.message,
        });
      } else {
        setErrorState({
          message: 'Ocurrió un error inesperado al procesar la petición.',
        });
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  /**
   * Determina el título de la alerta de error según el código de estado HTTP devuelto.
   *
   * @param status Código de estado HTTP opcional.
   * @returns Título descriptivo en español para la alerta.
   */
  const getErrorAlertTitle = (status?: number): string => {
    if (status === 402) {
      return 'Cortafuegos Presupuestario Activado (HTTP 402 Payment Required)';
    }
    if (status === 429) {
      return 'Límite de Peticiones Excedido (HTTP 429 Too Many Requests)';
    }
    return 'Error en la Petición';
  };

  const isProModel = lastResult?.modelRouted === 'gemini-2.5-pro';
  const isSimulatedMode =
    !lastResult?.simulatedResponse ||
    lastResult.simulatedResponse.startsWith(SIMULATION_RESPONSE_PREFIX);

  return (
    <section
      aria-label="Simulador de Enrutamiento de Prompts"
      className="rounded-xl bg-zinc-900/60 border border-white/10 p-6 shadow-sm flex flex-col justify-between"
    >
      <div>
        <div className="flex items-start justify-between gap-4 mb-6">
          <div>
            <h2 className="text-xl font-semibold tracking-tight text-zinc-100 flex items-center gap-2.5">
              <Terminal className="w-5 h-5 text-emerald-400" aria-hidden="true" />
              Simulador de Enrutamiento en Vivo
            </h2>
            <p className="text-sm text-zinc-400 mt-1">
              Envía un prompt al Gateway para evaluar su complejidad y verificar el modelo asignado.
            </p>
          </div>
        </div>

        <form
          onSubmit={handleSubmit}
          data-testid="prompt-playground-form"
          className="space-y-4"
          noValidate
        >
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Campo userId */}
            <div>
              <label
                htmlFor="playground-user-id"
                className="block text-xs font-medium text-zinc-300 mb-1.5"
              >
                Identificador de Usuario (<code className="text-emerald-400">userId</code>)
              </label>
              <div className="relative">
                <User
                  className="w-4 h-4 text-zinc-500 absolute left-3 top-1/2 -translate-y-1/2"
                  aria-hidden="true"
                />
                <input
                  id="playground-user-id"
                  data-testid="playground-user-input"
                  type="text"
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  placeholder="demo-user"
                  required
                  className="w-full pl-9 pr-3 py-2 text-sm bg-zinc-950/80 border border-white/10 rounded-lg text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/40 focus:border-indigo-500/50 transition"
                />
              </div>
            </div>

            {/* Campo opcional X-API-Key (Modo Híbrido BYOK) */}
            <div>
              <label
                htmlFor="playground-api-key"
                className="block text-xs font-medium text-zinc-300 mb-1.5"
              >
                Cabecera <code className="text-indigo-400">X-API-Key</code>{' '}
                <span className="text-gray-500">(Modo Híbrido BYOK)</span>
              </label>
              <div className="relative">
                <KeyRound
                  className="w-4 h-4 text-zinc-500 absolute left-3 top-1/2 -translate-y-1/2"
                  aria-hidden="true"
                />
                <input
                  id="playground-api-key"
                  data-testid="playground-apikey-input"
                  type="password"
                  value={apiKey}
                  onChange={(e) => setApiKey(e.target.value)}
                  placeholder="AIzaSy... (Google AI Studio) o vacío para Simulación"
                  className="w-full pl-9 pr-3 py-2 text-sm bg-zinc-950/80 border border-white/10 rounded-lg text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/40 focus:border-indigo-500/50 transition"
                />
              </div>
              <p className="text-[11px] text-zinc-400 mt-1 leading-snug">
                Introduce tu clave <code className="text-zinc-300">AIzaSy...</code> para invocar{' '}
                <span className="text-zinc-300 font-medium">Google Gemini real</span>, o déjalo vacío para{' '}
                <span className="text-zinc-300 font-medium">Modo Simulación</span> sin coste.
              </p>
            </div>
          </div>

          {/* Botones rápidos de plantilla */}
          <div>
            <div className="flex flex-wrap items-center justify-between gap-2 mb-1.5">
              <label
                htmlFor="playground-prompt"
                className="block text-xs font-medium text-zinc-300"
              >
                Contenido del Prompt
              </label>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  data-testid="preset-flash-button"
                  onClick={() => setPrompt(SIMPLE_PROMPT_EXAMPLE)}
                  className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium bg-emerald-500/10 text-emerald-300 border border-emerald-500/20 hover:bg-emerald-500/15 transition cursor-pointer"
                >
                  <Zap className="w-3 h-3" aria-hidden="true" />
                  Ejemplo Flash (Simple)
                </button>
                <button
                  type="button"
                  data-testid="preset-pro-button"
                  onClick={() => setPrompt(COMPLEX_PROMPT_EXAMPLE)}
                  className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium bg-indigo-500/10 text-indigo-300 border border-indigo-500/20 hover:bg-indigo-500/15 transition cursor-pointer"
                >
                  <Sparkles className="w-3 h-3" aria-hidden="true" />
                  Ejemplo Pro (Arquitectura)
                </button>
              </div>
            </div>

            <textarea
              id="playground-prompt"
              data-testid="playground-prompt-textarea"
              rows={4}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="Escribe aquí tu consulta para el LLM o selecciona un botón de ejemplo..."
              required
              className="w-full p-3 text-sm bg-zinc-950/80 border border-white/10 rounded-lg text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/40 focus:border-indigo-500/50 transition resize-y"
            />
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            data-testid="submit-prompt-button"
            className="w-full inline-flex items-center justify-center gap-2 py-2.5 px-4 rounded-lg text-sm font-medium bg-zinc-100 hover:bg-white text-zinc-950 shadow-sm transition cursor-pointer disabled:opacity-60"
          >
            <Send className="w-4 h-4" aria-hidden="true" />
            <span data-testid="playground-submit-button">
              {isSubmitting ? 'Evaluando y enrutando...' : 'Evaluar y Enrutar Prompt'}
            </span>
          </button>
        </form>
      </div>

      {/* Alerta de error (HTTP 400 / HTTP 402 Budget Cap / HTTP 429 Rate Limit) */}
      {errorState && (
        <div
          role="alert"
          data-testid="playground-error-alert"
          className="mt-5 p-4 rounded-xl bg-rose-950/40 border border-rose-500/30 text-rose-200 text-xs space-y-2"
        >
          <div className="flex items-center gap-2 font-semibold text-rose-300 text-sm">
            <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" aria-hidden="true" />
            <span>{getErrorAlertTitle(errorState.status)}</span>
          </div>
          <p className="text-rose-200/90 leading-relaxed">{errorState.message}</p>
          {errorState.retryAfterSeconds !== undefined && (
            <p
              data-testid="retry-after-indicator"
              className="inline-block px-2.5 py-1 rounded-md bg-rose-500/15 border border-rose-500/30 font-medium text-rose-300"
            >
              Tiempo de espera (Retry-After): {errorState.retryAfterSeconds} segundos
            </p>
          )}
        </div>
      )}

      {/* Tarjeta con el resultado del último enrutamiento */}
      {lastResult && !errorState && (
        <div
          data-testid="playground-result-card"
          className="mt-5 p-4 rounded-xl bg-zinc-950/80 border border-white/10 space-y-3"
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-400">
              <CheckCircle2 className="w-4 h-4" aria-hidden="true" />
              Enrutamiento Completado
            </span>
            <div className="flex flex-wrap items-center gap-2">
              <span
                data-testid="routed-execution-mode-badge"
                className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium border ${
                  isSimulatedMode
                    ? 'bg-zinc-800/80 text-zinc-300 border-white/10'
                    : 'bg-emerald-500/10 text-emerald-300 border-emerald-500/25'
                }`}
              >
                {!isSimulatedMode && <Cpu className="w-3 h-3" aria-hidden="true" />}
                {isSimulatedMode ? 'Modo Simulación' : 'Gemini API Real'}
              </span>
              <span
                data-testid="routed-model-badge"
                className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium border ${
                  isProModel
                    ? 'bg-indigo-500/10 text-indigo-300 border-indigo-500/25'
                    : 'bg-emerald-500/10 text-emerald-300 border-emerald-500/25'
                }`}
              >
                {isProModel ? (
                  <Sparkles className="w-3 h-3" aria-hidden="true" />
                ) : (
                  <Zap className="w-3 h-3" aria-hidden="true" />
                )}
                {lastResult.modelRouted}
              </span>
              <span
                data-testid="routed-tokens-badge"
                className="text-xs font-mono px-2 py-0.5 rounded bg-zinc-800/90 text-zinc-300 border border-white/5"
              >
                {lastResult.tokensEstimated} tokens
              </span>
            </div>
          </div>

          {lastResult.simulatedResponse && (
            <p
              data-testid="routed-simulated-response"
              className="text-xs text-zinc-300 bg-zinc-900/90 p-3 rounded-lg border border-white/5 font-mono leading-relaxed"
            >
              {lastResult.simulatedResponse}
            </p>
          )}
        </div>
      )}
    </section>
  );
};
