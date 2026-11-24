-- =============================================================================
-- Migración: V8__add_provider_to_request_logs.sql
-- Propósito: Añadir a la tabla request_logs la columna provider para almacenar
--            el proveedor de inteligencia artificial seleccionado por el motor
--            de enrutamiento multi-proveedor (GOOGLE_GEMINI, OPENAI, ANTHROPIC).
-- Compatibilidad: SQL estándar compatible con H2 (desarrollo/test) y PostgreSQL 17 (producción).
-- =============================================================================

-- Añadir identificador del proveedor LLM utilizado (por defecto 'GOOGLE_GEMINI' en registros históricos y nuevos)
ALTER TABLE request_logs ADD COLUMN provider VARCHAR(40) NOT NULL DEFAULT 'GOOGLE_GEMINI';
