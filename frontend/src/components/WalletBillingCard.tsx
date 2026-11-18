import React, { useState } from 'react';
import {
  Ban,
  CheckCircle2,
  Coins,
  CreditCard,
  Infinity as InfinityIcon,
  PlusCircle,
  Wallet,
} from 'lucide-react';
import { topUpUserWallet } from '../services/api';
import type {
  CurrencyCode,
  PaymentMethodCode,
  UserWalletResponse,
  WalletStatus,
} from '../types/router';
import { convertToUsd, formatCurrency } from '../utils/currency';

/**
 * Propiedades del panel de Billetera Prepago y Pasarela de Pagos (`WalletBillingCard`).
 */
export interface WalletBillingCardProps {
  /** Estado actual de la billetera del usuario devuelto por el backend. */
  wallet: UserWalletResponse;
  /** Divisa activa (`'EUR'` por defecto o `'USD'`). */
  currency: CurrencyCode;
  /** Callback invocado tras completar una recarga para actualizar el estado en el Dashboard. */
  onWalletUpdated: (updated: UserWalletResponse) => void;
}

/**
 * Devuelve la etiqueta descriptiva y las clases Tailwind asociadas al estado de la billetera (`WalletStatus`).
 *
 * @param status Estado operativo de la billetera.
 * @returns Etiqueta legible en español y clases para el badge.
 */
function getWalletStatusBadge(status: WalletStatus): {
  label: string;
  classes: string;
} {
  switch (status) {
    case 'PREPAID_ACTIVE':
      return {
        label: 'Prepago Activo (Con Saldo)',
        classes: 'bg-emerald-500/10 text-emerald-300 border-emerald-500/25',
      };
    case 'PREPAID_DEPLETED':
      return {
        label: 'Saldo Agotado (0,000000 €)',
        classes: 'bg-rose-500/15 text-rose-300 border-rose-500/35',
      };
    case 'POSTPAID_UNLIMITED':
    default:
      return {
        label: 'Modo Pospago (Sin prepago)',
        classes: 'bg-zinc-800/80 text-zinc-300 border-white/10',
      };
  }
}

/**
 * Formatea el código de pasarela de pago a una etiqueta legible en español.
 *
 * @param methodCode Código devuelto por el backend (`'STRIPE_CARD'`, `'PAYPAL'` o `null`).
 * @returns Nombre legible del método de pago.
 */
function formatPaymentMethodName(methodCode: string | null): string {
  if (methodCode === 'STRIPE_CARD') {
    return 'Tarjeta Stripe';
  }
  if (methodCode === 'PAYPAL') {
    return 'PayPal';
  }
  return '—';
}

/**
 * Componente de Billetera Prepago (*Token Credits Wallet*) y Pasarela de Pagos (`WalletBillingCard`)
 * con diseño minimalista estilo Vercel / shadcn/ui y 100% clases de utilidad Tailwind CSS v4.
 * Permite visualizar en tiempo real el saldo disponible del usuario (descontado micro-céntimo a micro-céntimo
 * en cada petición), seleccionar la pasarela de pago (**Tarjeta Stripe** o **PayPal**) y abonar paquetes
 * rápidos de créditos o importes personalizados en `EUR €` o `USD $`.
 *
 * @param props Estado de la billetera, divisa activa y callback de actualización.
 * @returns Tarjeta interactiva de facturación y recarga de créditos.
 */
export const WalletBillingCard: React.FC<WalletBillingCardProps> = ({
  wallet,
  currency,
  onWalletUpdated,
}) => {
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethodCode>('STRIPE_CARD');
  const [customAmount, setCustomAmount] = useState<string>('');
  const [isProcessing, setIsProcessing] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const badgeInfo = getWalletStatusBadge(wallet.status);

  /**
   * Ejecuta una recarga de créditos convirtiendo el importe desde la divisa activa a USD.
   *
   * @param amountInActiveCurrency Importe positivo expresado en `EUR` o `USD`.
   */
  const executeTopUp = async (amountInActiveCurrency: number) => {
    setErrorMessage(null);
    if (!Number.isFinite(amountInActiveCurrency) || amountInActiveCurrency <= 0) {
      setErrorMessage('Introduce un importe de recarga válido mayor que 0.');
      return;
    }

    setIsProcessing(true);
    try {
      const amountUsd = convertToUsd(amountInActiveCurrency, currency);
      const updated = await topUpUserWallet(wallet.userId, {
        amountUsd,
        paymentMethod,
      });
      setCustomAmount('');
      onWalletUpdated(updated);
    } catch (err) {
      setErrorMessage(
        err instanceof Error
          ? err.message
          : 'No se pudo procesar la recarga de créditos.',
      );
    } finally {
      setIsProcessing(false);
    }
  };

  /**
   * Gestiona el envío del formulario de recarga personalizada.
   *
   * @param event Evento de envío del formulario.
   */
  const handleCustomTopUpSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const normalized = customAmount.trim().replace(',', '.');
    const parsed = Number.parseFloat(normalized);
    await executeTopUp(parsed);
  };

  return (
    <section
      aria-label="Billetera Prepago y Pasarela de Pagos"
      data-testid="wallet-billing-card"
      className="rounded-xl bg-zinc-900/60 border border-white/10 p-6 shadow-sm flex flex-col justify-between space-y-5"
    >
      <div className="space-y-5">
        {/* Cabecera y Badge de Estado */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <div className="flex items-center gap-2.5 flex-wrap">
              <Coins className="w-5 h-5 text-emerald-400" aria-hidden="true" />
              <h2 className="text-xl font-semibold tracking-tight text-zinc-100">
                Billetera Prepago y Facturación (Billing)
              </h2>
              <span className="px-2.5 py-0.5 rounded-md text-xs font-mono bg-zinc-800/90 text-zinc-300 border border-white/10">
                Usuario: <strong>{wallet.userId}</strong>
              </span>
            </div>
            <p className="text-sm text-zinc-400 mt-1">
              Recarga créditos mediante Stripe o PayPal y observa el descuento en vivo por cada petición enrutada.
            </p>
          </div>

          <span
            data-testid="wallet-status-badge"
            className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium border self-start sm:self-auto ${badgeInfo.classes}`}
          >
            {wallet.status === 'POSTPAID_UNLIMITED' && (
              <InfinityIcon className="w-3.5 h-3.5" aria-hidden="true" />
            )}
            {wallet.status === 'PREPAID_ACTIVE' && (
              <CheckCircle2 className="w-3.5 h-3.5" aria-hidden="true" />
            )}
            {wallet.status === 'PREPAID_DEPLETED' && (
              <Ban className="w-3.5 h-3.5" aria-hidden="true" />
            )}
            {badgeInfo.label}
          </span>
        </div>

        {/* Métricas de Saldo en Vivo */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 p-4 rounded-xl bg-zinc-950/60 border border-white/5 text-sm">
          <div>
            <span className="text-xs text-zinc-400 block">Saldo Disponible en Vivo</span>
            <p
              data-testid="wallet-balance-value"
              className="text-lg font-bold font-mono text-emerald-400 mt-0.5"
            >
              {formatCurrency(wallet.balanceUsd, currency)}
            </p>
          </div>

          <div>
            <span className="text-xs text-zinc-400 block">Total Recargado Histórico</span>
            <p
              data-testid="wallet-total-topped-up"
              className="text-lg font-bold font-mono text-zinc-100 mt-0.5"
            >
              {formatCurrency(wallet.totalToppedUpUsd, currency)}
            </p>
          </div>

          <div className="sm:text-right">
            <span className="text-xs text-zinc-400 block">Última Pasarela Utilizada</span>
            <p
              data-testid="wallet-last-method"
              className="text-sm font-semibold text-zinc-200 mt-1"
            >
              {formatPaymentMethodName(wallet.lastPaymentMethod)}
            </p>
          </div>
        </div>

        {/* Selector de Pasarela de Pago (Stripe / PayPal) y Paquetes Rápidos */}
        <div className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <span className="text-xs font-medium text-zinc-300">
              1. Selecciona Pasarela de Pago:
            </span>
            <div
              role="group"
              aria-label="Selector de método de pago"
              className="inline-flex items-center bg-zinc-950 p-1 rounded-lg border border-white/10"
            >
              <button
                type="button"
                data-testid="payment-method-stripe"
                aria-pressed={paymentMethod === 'STRIPE_CARD'}
                onClick={() => setPaymentMethod('STRIPE_CARD')}
                className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-medium transition cursor-pointer ${
                  paymentMethod === 'STRIPE_CARD'
                    ? 'bg-zinc-100 text-zinc-950 shadow-sm'
                    : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                <CreditCard className="w-3.5 h-3.5" aria-hidden="true" />
                <span>Tarjeta (Stripe)</span>
              </button>

              <button
                type="button"
                data-testid="payment-method-paypal"
                aria-pressed={paymentMethod === 'PAYPAL'}
                onClick={() => setPaymentMethod('PAYPAL')}
                className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-medium transition cursor-pointer ${
                  paymentMethod === 'PAYPAL'
                    ? 'bg-zinc-100 text-zinc-950 shadow-sm'
                    : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                <Wallet className="w-3.5 h-3.5" aria-hidden="true" />
                <span>PayPal</span>
              </button>
            </div>
          </div>

          {/* Botones de Paquetes de Recarga Rápida + Formulario Personalizado */}
          <form
            onSubmit={handleCustomTopUpSubmit}
            className="pt-2 border-t border-white/5 flex flex-col lg:flex-row lg:items-end justify-between gap-3"
            noValidate
          >
            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                data-testid="wallet-topup-micro"
                disabled={isProcessing}
                onClick={() => void executeTopUp(0.00005)}
                className="px-2.5 py-1.5 rounded-lg text-xs font-medium bg-zinc-800/80 hover:bg-zinc-800 text-zinc-200 border border-white/10 transition cursor-pointer disabled:opacity-50"
              >
                {currency === 'EUR' ? 'Micro (+0,000050 €)' : 'Micro (+$0.000050)'}
              </button>

              <button
                type="button"
                data-testid="wallet-topup-5"
                disabled={isProcessing}
                onClick={() => void executeTopUp(5)}
                className="px-2.5 py-1.5 rounded-lg text-xs font-medium bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-300 border border-emerald-500/25 transition cursor-pointer disabled:opacity-50"
              >
                {currency === 'EUR' ? '+ 5,00 €' : '+ $5.00'}
              </button>

              <button
                type="button"
                data-testid="wallet-topup-20"
                disabled={isProcessing}
                onClick={() => void executeTopUp(20)}
                className="px-2.5 py-1.5 rounded-lg text-xs font-medium bg-indigo-500/10 hover:bg-indigo-500/20 text-indigo-300 border border-indigo-500/25 transition cursor-pointer disabled:opacity-50"
              >
                {currency === 'EUR' ? '+ 20,00 €' : '+ $20.00'}
              </button>
            </div>

            <div className="flex items-center gap-2">
              <input
                type="number"
                step="0.000001"
                min="0"
                data-testid="wallet-custom-amount-input"
                aria-label="Importe personalizado de recarga"
                value={customAmount}
                onChange={(e) => setCustomAmount(e.target.value)}
                placeholder={currency === 'EUR' ? 'Otro importe (€)...' : 'Otro importe ($)...'}
                className="w-40 px-3 py-1.5 text-xs font-mono bg-zinc-950/80 border border-white/10 rounded-lg text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/40 transition"
              />
              <button
                type="submit"
                data-testid="wallet-topup-submit-button"
                disabled={isProcessing}
                className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-medium bg-zinc-100 hover:bg-white text-zinc-950 shadow-sm transition cursor-pointer disabled:opacity-60"
              >
                <PlusCircle className="w-3.5 h-3.5" aria-hidden="true" />
                <span>{isProcessing ? 'Abonando...' : 'Recargar Créditos'}</span>
              </button>
            </div>
          </form>
        </div>

        {errorMessage && (
          <p role="alert" className="text-xs text-rose-400">
            {errorMessage}
          </p>
        )}
      </div>
    </section>
  );
};
