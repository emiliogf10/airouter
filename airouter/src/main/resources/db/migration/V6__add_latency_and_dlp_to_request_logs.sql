-- =============================================================================
-- Migración: V6__add_latency_and_dlp_to_request_logs.sql
-- Propósito: Añadir a la tabla request_logs las columnas latency_ms (telemetría
--            del tiempo de respuesta extremo a extremo en milisegundos) y
--            redacted_items_count (auditoría del Escudo DLP de Privacidad que
--            contabiliza los elementos PII/secretos anonimizados antes de salir
--            del Gateway o persistirse).
-- Compatibilidad: SQL estándar compatible con H2 (desarrollo/test) y PostgreSQL 17 (producción).
-- =============================================================================

-- Añadir columna de latencia en milisegundos (por defecto 0 en registros históricos y nuevos)
ALTER TABLE request_logs ADD COLUMN latency_ms BIGINT NOT NULL DEFAULT 0;

-- Añadir contador de elementos sensibles redactados por el Escudo DLP (por defecto 0)
ALTER TABLE request_logs ADD COLUMN redacted_items_count INTEGER NOT NULL DEFAULT 0;
