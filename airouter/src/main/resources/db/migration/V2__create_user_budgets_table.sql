-- =============================================================================
-- Migración: V2__create_user_budgets_table.sql
-- Propósito: Crear la tabla user_budgets para almacenar los límites de
--            presupuesto máximo (Hard Budget Cap / FinOps) por usuario y
--            la configuración de degradación automática al 80% de consumo.
-- Compatibilidad: SQL estándar compatible con H2 (desarrollo/test) y PostgreSQL (producción).
-- =============================================================================

-- Creación de la tabla de presupuestos por usuario (user_budgets)
CREATE TABLE user_budgets (
    -- Identificador único universal del registro de presupuesto
    id UUID NOT NULL,
    -- Identificador único del usuario al que se le aplica el límite presupuestario
    user_id VARCHAR(120) NOT NULL,
    -- Presupuesto máximo permitido en dólares estadounidenses (USD) con precisión de 6 decimales
    max_budget_usd NUMERIC(19, 6) NOT NULL,
    -- Indicador de degradación automática a modelo económico (gemini-2.5-flash) al alcanzar el 80% del presupuesto
    auto_downgrade_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    -- Fecha y hora exacta de creación o última actualización del presupuesto
    updated_at TIMESTAMP NOT NULL,
    -- Restricción de clave primaria
    CONSTRAINT pk_user_budgets PRIMARY KEY (id),
    -- Restricción de unicidad para garantizar un único registro de presupuesto por usuario
    CONSTRAINT uk_user_budgets_user_id UNIQUE (user_id)
);

-- Índice de búsqueda rápida por identificador de usuario para evaluación FinOps en tiempo real
CREATE INDEX idx_user_budgets_user_id ON user_budgets (user_id);
