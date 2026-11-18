-- =============================================================================
-- Migración: V5__create_user_wallets_table.sql
-- Propósito: Crear la tabla user_wallets para gestionar la Billetera Prepago
--            (Token Credits Wallet) por usuario, registrando el saldo en USD,
--            el total histórico recargado mediante pasarelas de pago (Stripe / PayPal)
--            y el estado de exigencia del modo prepago.
-- Compatibilidad: SQL estándar compatible con H2 (desarrollo/test) y PostgreSQL (producción).
-- =============================================================================

-- Creación de la tabla de billeteras prepago por usuario (user_wallets)
CREATE TABLE user_wallets (
    -- Identificador único universal del registro de billetera
    id UUID NOT NULL,
    -- Identificador único del usuario titular de la billetera
    user_id VARCHAR(120) NOT NULL,
    -- Saldo prepago disponible en dólares estadounidenses (USD) con precisión de 6 decimales
    balance_usd NUMERIC(19, 6) NOT NULL DEFAULT 0.000000,
    -- Importe total histórico recargado por el usuario en USD con precisión de 6 decimales
    total_topped_up_usd NUMERIC(19, 6) NOT NULL DEFAULT 0.000000,
    -- Indica si la cuenta opera en modo prepago estricto (TRUE tras la primera recarga)
    wallet_enforced BOOLEAN NOT NULL DEFAULT FALSE,
    -- Último método de pago utilizado en la pasarela (ej. "STRIPE_CARD", "PAYPAL")
    last_payment_method VARCHAR(40),
    -- Fecha y hora exacta de la última recarga o cargo de micro-céntimos
    updated_at TIMESTAMP NOT NULL,
    -- Restricción de clave primaria
    CONSTRAINT pk_user_wallets PRIMARY KEY (id),
    -- Restricción de unicidad para garantizar una única billetera por usuario
    CONSTRAINT uk_user_wallets_user_id UNIQUE (user_id)
);

-- Índice B-Tree por usuario para consultas y descuentos de saldo en tiempo real
CREATE INDEX idx_user_wallets_user_id ON user_wallets (user_id);
