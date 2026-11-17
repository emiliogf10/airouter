import React, { useEffect, useRef, useState } from 'react';
import {
  ArrowUpRight,
  Ban,
  Check,
  CheckCircle2,
  Copy,
  KeyRound,
  Plus,
  ShieldCheck,
  Trash2,
} from 'lucide-react';
import { createUserApiKey, revokeUserApiKey } from '../services/api';
import type { ApiKeyCreatedResponse, ApiKeyResponse } from '../types/router';

/**
 * Propiedades del gestor de claves API (`ApiKeyManagerCard`).
 */
export interface ApiKeyManagerCardProps {
  /** Identificador del usuario activo cuyas claves se administran. */
  userId: string;
  /** Lista de claves API registradas para el usuario. */
  apiKeys: ApiKeyResponse[];
  /** Callback invocado tras crear o revocar una clave para refrescar la lista del usuario. */
  onKeysChanged: () => void;
  /** Callback opcional para inyectar una clave recién generada directamente en el simulador (`PromptPlayground`). */
  onUseKeyInPlayground?: (rawApiKey: string) => void;
}

/**
 * Variable de alcance de módulo para recordar el último mensaje anunciado en la región
 * `role="status"` del botón de copiado de claves API, añadiendo `\u00A0` en repeticiones
 * consecutivas para garantizar su anuncio en lectores de pantalla (`copy-button.md`).
 */
let lastApiKeyCopyAnnouncement = '';

/**
 * Respaldo síncrono de copiado mediante `document.execCommand('copy')` cuando la API
 * asíncrona `navigator.clipboard` no está disponible.
 *
 * @param text Texto a copiar al portapapeles.
 * @returns `true` si la operación tuvo éxito, `false` en caso contrario.
 */
function legacyCopyApiKeyFallback(text: string): boolean {
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
 * Panel gestor de claves API por usuario (`ApiKeyManagerCard`) con diseño minimalista
 * estilo Vercel / shadcn/ui y cumplimiento estricto de `modern-web-guidance` (`copy-button`)
 * y `security-audit`:
 * - Permite generar tokens `air_live_...` cuyo secreto en texto plano (`rawApiKey`) se muestra
 *   **una única vez** tras su creación (en base de datos solo se persiste su hash SHA-256).
 * - Incorpora copiado accesible con región viva `<span role="status">` externa al `<button>`
 *   y botón rápido **"Usar en Simulador"**.
 * - Lista las claves emitidas con su prefijo enmascarado (`keyPrefix`), estado (`Activa` / `Revocada`)
 *   y opción de revocación inmediata (`DELETE /api/v1/router/keys/{keyId}`).
 *
 * @param props Usuario activo, lista de claves y callbacks de sincronización.
 * @returns Tarjeta interactiva de administración de claves API.
 */
export const ApiKeyManagerCard: React.FC<ApiKeyManagerCardProps> = ({
  userId,
  apiKeys,
  onKeysChanged,
  onUseKeyInPlayground,
}) => {
  const [keyName, setKeyName] = useState<string>('');
  const [isCreating, setIsCreating] = useState<boolean>(false);
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const [newlyCreatedKey, setNewlyCreatedKey] = useState<ApiKeyCreatedResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [keyCopied, setKeyCopied] = useState<boolean>(false);
  const [copyStatusText, setCopyStatusText] = useState<string>('');
  const copyTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setNewlyCreatedKey(null);
    setErrorMessage(null);
    setKeyCopied(false);
    setCopyStatusText('');
  }, [userId]);

  useEffect(() => {
    return () => {
      if (copyTimerRef.current) {
        clearTimeout(copyTimerRef.current);
      }
    };
  }, []);

  /**
   * Gestiona la creación de una nueva clave API para el usuario activo.
   *
   * @param event Evento de envío del formulario.
   */
  const handleCreateKey = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setErrorMessage(null);

    const trimmedName = keyName.trim();
    if (!trimmedName) {
      setErrorMessage('Introduce un nombre descriptivo para identificar la clave API.');
      return;
    }

    setIsCreating(true);
    try {
      const created = await createUserApiKey({
        userId: userId.trim() || 'demo-user',
        keyName: trimmedName,
      });
      setNewlyCreatedKey(created);
      setKeyName('');
      onKeysChanged();
    } catch (err) {
      setErrorMessage(
        err instanceof Error ? err.message : 'No se pudo generar la clave API.',
      );
    } finally {
      setIsCreating(false);
    }
  };

  /**
   * Revoca una clave API activa invocando al endpoint `DELETE /api/v1/router/keys/{keyId}`.
   *
   * @param keyId Identificador UUID de la clave a revocar.
   */
  const handleRevokeKey = async (keyId: string) => {
    setErrorMessage(null);
    setRevokingId(keyId);
    try {
      await revokeUserApiKey(keyId);
      if (newlyCreatedKey?.id === keyId) {
        setNewlyCreatedKey(null);
      }
      onKeysChanged();
    } catch (err) {
      setErrorMessage(
        err instanceof Error ? err.message : 'No se pudo revocar la clave API.',
      );
    } finally {
      setRevokingId(null);
    }
  };

  /**
   * Copia el token `rawApiKey` recién generado siguiendo la guía `copy-button` de `modern-web-guidance`.
   *
   * @param rawKey Secreto en texto plano a copiar.
   */
  const handleCopyNewKey = async (rawKey: string) => {
    if (!rawKey) {
      return;
    }

    let ok = false;
    try {
      if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
        await navigator.clipboard.writeText(rawKey);
        ok = true;
      } else {
        ok = legacyCopyApiKeyFallback(rawKey);
      }
    } catch {
      ok = legacyCopyApiKeyFallback(rawKey);
    }

    let message = ok ? 'Copiado' : 'Error al copiar';
    if (message === lastApiKeyCopyAnnouncement) {
      message += '\u00A0';
    }
    lastApiKeyCopyAnnouncement = message;

    if (copyTimerRef.current) {
      clearTimeout(copyTimerRef.current);
    }
    setKeyCopied(ok);
    setCopyStatusText(message);
    copyTimerRef.current = setTimeout(() => {
      setKeyCopied(false);
      setCopyStatusText('');
    }, 2000);
  };

  return (
    <section
      aria-label="Gestor de Claves API por Usuario"
      data-testid="api-key-manager-card"
      className="rounded-xl bg-zinc-900/60 border border-white/10 p-6 shadow-sm flex flex-col justify-between space-y-5"
    >
      <div className="space-y-5">
        {/* Cabecera */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <div className="flex items-center gap-2.5 flex-wrap">
              <KeyRound className="w-5 h-5 text-indigo-400" aria-hidden="true" />
              <h2 className="text-xl font-semibold tracking-tight text-zinc-100">
                Claves API del Gateway (SHA-256)
              </h2>
              <span className="px-2.5 py-0.5 rounded-md text-xs font-mono bg-zinc-800/90 text-zinc-300 border border-white/10">
                Usuario: <strong>{userId}</strong>
              </span>
            </div>
            <p className="text-sm text-zinc-400 mt-1">
              Emite tokens <code className="text-indigo-300 font-mono">air_live_...</code> con cuota propia. En base de datos solo se almacena su hash SHA-256.
            </p>
          </div>

          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-zinc-800/80 text-zinc-300 border border-white/10 self-start sm:self-auto">
            <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" aria-hidden="true" />
            {apiKeys.filter((k) => k.active).length} activas
          </span>
        </div>

        {/* Formulario para crear nueva API Key */}
        <form
          onSubmit={handleCreateKey}
          className="flex flex-col sm:flex-row items-stretch sm:items-end gap-3"
          noValidate
        >
          <div className="flex-1">
            <label
              htmlFor="apikey-name-input"
              className="block text-xs font-medium text-zinc-300 mb-1.5"
            >
              Nombre descriptivo de la nueva clave
            </label>
            <input
              id="apikey-name-input"
              data-testid="apikey-name-input"
              type="text"
              value={keyName}
              onChange={(e) => setKeyName(e.target.value)}
              placeholder="Ej: Producción App Web"
              className="w-full px-3 py-1.5 text-sm bg-zinc-950/80 border border-white/10 rounded-lg text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/40 transition"
            />
          </div>

          <button
            type="submit"
            data-testid="apikey-create-button"
            disabled={isCreating}
            className="inline-flex items-center justify-center gap-1.5 px-4 py-2 rounded-lg text-xs font-medium bg-zinc-100 hover:bg-white text-zinc-950 shadow-sm transition cursor-pointer disabled:opacity-60"
          >
            <Plus className="w-4 h-4" aria-hidden="true" />
            <span>{isCreating ? 'Generando...' : 'Generar API Key'}</span>
          </button>
        </form>

        {/* Banner de visualización única de la clave recién creada */}
        {newlyCreatedKey && (
          <div
            data-testid="newly-created-apikey-banner"
            className="p-4 rounded-xl bg-emerald-950/30 border border-emerald-500/35 space-y-3"
          >
            <div className="flex items-start justify-between gap-2">
              <div>
                <p className="text-xs font-semibold text-emerald-300 flex items-center gap-1.5">
                  <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" aria-hidden="true" />
                  Clave «{newlyCreatedKey.keyName}» generada exitosamente
                </p>
                <p className="text-[11px] text-zinc-300 mt-0.5">
                  Copia este secreto ahora. Por seguridad solo se muestra <strong>esta única vez</strong> (en BD solo persiste su hash SHA-256).
                </p>
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2 p-2.5 rounded-lg bg-zinc-950 border border-white/10">
              <code
                data-testid="newly-created-apikey-raw"
                className="text-xs font-mono text-emerald-300 break-all"
              >
                {newlyCreatedKey.rawApiKey}
              </code>

              <div className="flex flex-wrap items-center gap-2">
                <span className="inline-flex items-center gap-2">
                  <button
                    type="button"
                    data-testid="copy-new-apikey-button"
                    data-copied={keyCopied ? '' : undefined}
                    onClick={() => void handleCopyNewKey(newlyCreatedKey.rawApiKey)}
                    className="min-w-6 min-h-6 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium bg-zinc-800 hover:bg-zinc-700 text-zinc-100 border border-white/10 transition cursor-pointer"
                  >
                    {keyCopied ? (
                      <Check className="w-3.5 h-3.5 text-emerald-400" aria-hidden="true" />
                    ) : (
                      <Copy className="w-3.5 h-3.5 text-zinc-300" aria-hidden="true" />
                    )}
                    <span>Copiar clave</span>
                  </button>
                  <span
                    role="status"
                    data-testid="copy-new-apikey-status"
                    className={`text-xs font-medium text-emerald-400 transition-opacity ${
                      copyStatusText ? 'opacity-100' : 'opacity-0'
                    }`}
                  >
                    {copyStatusText}
                  </span>
                </span>

                {onUseKeyInPlayground && (
                  <button
                    type="button"
                    data-testid="use-apikey-in-playground-button"
                    onClick={() => onUseKeyInPlayground(newlyCreatedKey.rawApiKey)}
                    className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-medium bg-emerald-500/15 hover:bg-emerald-500/25 text-emerald-300 border border-emerald-500/30 transition cursor-pointer"
                  >
                    <span>Usar en Simulador</span>
                    <ArrowUpRight className="w-3.5 h-3.5" aria-hidden="true" />
                  </button>
                )}
              </div>
            </div>
          </div>
        )}

        {errorMessage && (
          <p role="alert" className="text-xs text-rose-400">
            {errorMessage}
          </p>
        )}

        {/* Listado de claves del usuario */}
        <div className="space-y-2">
          {apiKeys.length === 0 ? (
            <div className="p-4 rounded-xl bg-zinc-950/50 border border-white/5 text-center text-xs text-zinc-400">
              No hay claves API registradas para <strong>{userId}</strong>. Genera una clave para autenticar peticiones con cuota dedicada.
            </div>
          ) : (
            <ul className="divide-y divide-white/5 rounded-xl bg-zinc-950/60 border border-white/10 overflow-hidden">
              {apiKeys.map((apiKeyItem) => (
                <li
                  key={apiKeyItem.id}
                  data-testid={`apikey-row-${apiKeyItem.id}`}
                  className="p-3.5 flex flex-wrap items-center justify-between gap-3 text-xs"
                >
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-zinc-100">
                        {apiKeyItem.keyName}
                      </span>
                      <span
                        data-testid={`apikey-status-${apiKeyItem.id}`}
                        className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium border ${
                          apiKeyItem.active
                            ? 'bg-emerald-500/10 text-emerald-300 border-emerald-500/25'
                            : 'bg-rose-500/10 text-rose-300 border-rose-500/25'
                        }`}
                      >
                        {apiKeyItem.active ? (
                          <CheckCircle2 className="w-3 h-3" aria-hidden="true" />
                        ) : (
                          <Ban className="w-3 h-3" aria-hidden="true" />
                        )}
                        {apiKeyItem.active ? 'Activa' : 'Revocada'}
                      </span>
                    </div>
                    <p className="font-mono text-zinc-400">{apiKeyItem.keyPrefix}</p>
                  </div>

                  {apiKeyItem.active && (
                    <button
                      type="button"
                      data-testid={`revoke-apikey-btn-${apiKeyItem.id}`}
                      disabled={revokingId === apiKeyItem.id}
                      onClick={() => void handleRevokeKey(apiKeyItem.id)}
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-medium bg-zinc-900 hover:bg-rose-950/50 text-zinc-300 hover:text-rose-300 border border-white/10 hover:border-rose-500/30 transition cursor-pointer disabled:opacity-50"
                    >
                      <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                      <span>
                        {revokingId === apiKeyItem.id ? 'Revocando...' : 'Revocar'}
                      </span>
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </section>
  );
};
