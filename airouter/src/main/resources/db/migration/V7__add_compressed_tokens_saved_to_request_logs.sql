-- =============================================================================
-- Migración: V7__add_compressed_tokens_saved_to_request_logs.sql
-- Propósito: Añadir a la tabla request_logs la columna tokens_saved_compression
--            para registrar el número de tokens ahorrados automáticamente por
--            el Compresor de Prompts (Prompt Trimming & Token Optimizer) antes
--            de enviar la petición al modelo de lenguaje.
-- Compatibilidad: SQL estándar compatible con H2 (desarrollo/test) y PostgreSQL 17 (producción).
-- =============================================================================

-- Añadir contador de tokens ahorrados por compresión del prompt (por defecto 0 en registros históricos y nuevos)
ALTER TABLE request_logs ADD COLUMN tokens_saved_compression INTEGER NOT NULL DEFAULT 0;
