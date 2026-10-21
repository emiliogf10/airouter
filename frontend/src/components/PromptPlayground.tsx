import React, { useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  KeyRound,
  Send,
  Sparkles,
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
}

/** Ejemplo predefinido de baja complejidad para activar el enrutamiento hacia `gemini-2.5-flash`. */
const SIMPLE_PROMPT_EXAMPLE =
  'Hola, ¿puedes darme tres consejos breves para organizar mi agenda diaria?';

/** Ejemplo predefinido de alta complejidad técnica para activar el enrutamiento hacia `gemini-2.5-pro`. */
const COMPLEX_PROMPT_EXAMPLE =
  'Necesito diseñar una arquitectura de microservicios resiliente y analizar en profundidad cómo optimizar y refactorizar el balanceo de carga distribuido.';

/**
 * Simulador interactivo (`PromptPlayground`) para probar en vivo las reglas de negocio del
 * enrutador inteligente (`POST /api/v1/router/prompt`).
 * Permite especificar `userId`, una clave opcional `X-API-Key` para pruebas de cuota de Rate Limiting,
 * cargar plantillas de prompts simples o complejos, visualizar la decisión del modelo y mostrar
 * alertas detalladas ante errores HTTP 400 o HTTP 429 (`Retry-After`).
 *
 * @param props Propiedades del componente.
 * @returns Tarjeta interactiva con formulario de envío y panel de resultado/error.
 */
export const PromptPlayground: React.FC<PromptPlaygroundProps> = ({ onPromptSuccess }) => {
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
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorState({
          message: err.message,
          status: err.status,
          retryAfterSeconds: err.retryAfterSeconds,
        });
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

  const isProModel = lastResult?.modelRouted === 'gemini-2.5-pro';

  return (
    <section
      aria-label="Simulador de Enrutamiento de Prompts"
      className="rounded-xl bg-slate-900/90 border border-slate-800 p-6 shadow-lg flex flex-col justify-between"
    >
      <div>
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-lg font-bold text-white flex items-center gap-2">
              <Send className="w-5 h-5 text-emerald-400" aria-hidden="true" />
              Simulador de Enrutamiento en Vivo
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Envía un prompt al Gateway para evaluar su complejidad y verificar el modelo asignado.
            </p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {/* Campo userId */}
            <div>
              <label
                htmlFor="playground-user-id"
                className="block text-xs font-medium text-slate-300 mb-1"
              >
                Identificador de Usuario (<code className="text-emerald-400">userId</code>)
              </label>
              <div className="relative">
                <User
                  className="w-4 h-4 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2"
                  aria-hidden="true"
                />
                <input
                  id="playground-user-id"
                  type="text"
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  placeholder="demo-user"
                  required
                  className="w-full pl-9 pr-3 py-2 text-sm bg-slate-950 border border-slate-700/80 rounded-lg text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
                />
              </div>
            </div>

            {/* Campo opcional X-API-Key */}
            <div>
              <label
                htmlFor="playground-api-key"
                className="block text-xs font-medium text-slate-300 mb-1"
              >
                Cabecera <code className="text-cyan-400">X-API-Key</code>{' '}
                <span className="text-slate-500">(Opcional)</span>
              </label>
              <div className="relative">
                <KeyRound
                  className="w-4 h-4 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2"
                  aria-hidden="true"
                />
                <input
                  id="playground-api-key"
                  type="password"
                  value={apiKey}
                  onChange={(e) => setApiKey(e.target.value)}
                  placeholder="sk-airouter-demo-key..."
                  className="w-full pl-9 pr-3 py-2 text-sm bg-slate-950 border border-slate-700/80 rounded-lg text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-cyan-500/50"
                />
              </div>
            </div>
          </div>

          {/* Botones rápidos de plantilla */}
          <div>
            <div className="flex flex-wrap items-center justify-between gap-2 mb-1.5">
              <label
                htmlFor="playground-prompt"
                className="block text-xs font-medium text-slate-300"
              >
                Contenido del Prompt
              </label>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  data-testid="preset-flash-button"
                  onClick={() => setPrompt(SIMPLE_PROMPT_EXAMPLE)}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-medium bg-emerald-500/10 text-emerald-300 border border-emerald-500/30 hover:bg-emerald-500/20 transition cursor-pointer"
                >
                  <Zap className="w-3 h-3" aria-hidden="true" />
                  Ejemplo Flash (Simple)
                </button>
                <button
                  type="button"
                  data-testid="preset-pro-button"
                  onClick={() => setPrompt(COMPLEX_PROMPT_EXAMPLE)}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-medium bg-purple-500/10 text-purple-300 border border-purple-500/30 hover:bg-purple-500/20 transition cursor-pointer"
                >
                  <Sparkles className="w-3 h-3" aria-hidden="true" />
                  Ejemplo Pro (Arquitectura)
                </button>
              </div>
            </div>

            <textarea
              id="playground-prompt"
              rows={4}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="Escribe aquí tu consulta para el LLM o selecciona un botón de ejemplo..."
              required
              className="w-full p-3 text-sm bg-slate-950 border border-slate-700/80 rounded-lg text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 resize-y"
            />
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            data-testid="submit-prompt-button"
            className="w-full inline-flex items-center justify-center gap-2 py-2.5 px-4 rounded-lg text-sm font-semibold bg-emerald-500 hover:bg-emerald-400 text-slate-950 shadow-md transition cursor-pointer disabled:opacity-60"
          >
            <Send className="w-4 h-4" aria-hidden="true" />
            {isSubmitting ? 'Evaluando y enrutando...' : 'Evaluar y Enrutar Prompt'}
          </button>
        </form>
      </div>

      {/* Alerta de error (HTTP 400 / HTTP 429 Rate Limit) */}
      {errorState && (
        <div
          role="alert"
          data-testid="playground-error-alert"
          className="mt-4 p-4 rounded-xl bg-rose-950/60 border border-rose-500/40 text-rose-200 text-xs space-y-1.5"
        >
          <div className="flex items-center gap-2 font-bold text-rose-300 text-sm">
            <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" aria-hidden="true" />
            <span>
              {errorState.status === 429
                ? 'Límite de Peticiones Excedido (HTTP 429 Too Many Requests)'
                : 'Error en la Petición'}
            </span>
          </div>
          <p className="text-rose-200/90 leading-relaxed">{errorState.message}</p>
          {errorState.retryAfterSeconds !== undefined && (
            <p
              data-testid="retry-after-indicator"
              className="inline-block px-2.5 py-1 rounded-md bg-rose-500/20 border border-rose-500/30 font-semibold text-rose-300"
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
          className="mt-4 p-4 rounded-xl bg-slate-950/90 border border-slate-800 space-y-2.5"
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-400">
              <CheckCircle2 className="w-4 h-4" aria-hidden="true" />
              Enrutamiento Completado
            </span>
            <div className="flex items-center gap-2">
              <span
                data-testid="routed-model-badge"
                className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold border ${
                  isProModel
                    ? 'bg-purple-500/15 text-purple-300 border-purple-500/40'
                    : 'bg-emerald-500/15 text-emerald-300 border-emerald-500/40'
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
                className="text-xs font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-300"
              >
                {lastResult.tokensEstimated} tokens
              </span>
            </div>
          </div>

          {lastResult.simulatedResponse && (
            <p className="text-xs text-slate-300 bg-slate-900/90 p-3 rounded-lg border border-slate-800/80 font-mono leading-relaxed">
              {lastResult.simulatedResponse}
            </p>
          )}
        </div>
      )}
    </section>
  );
};
