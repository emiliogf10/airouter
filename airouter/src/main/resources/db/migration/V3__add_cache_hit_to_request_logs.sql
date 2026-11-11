-- =============================================================================
-- Migración: V3__add_cache_hit_to_request_logs.sql
-- Propósito: Añadir la columna cache_hit a la tabla request_logs para registrar
--            las peticiones servidas desde la Caché Semántica (Zero-Cost Cache)
--            con coste real $0.000000 USD, junto con su índice de filtrado.
-- Compatibilidad: SQL estándar compatible con H2 (desarrollo/test) y PostgreSQL (producción).
-- =============================================================================

-- Añadir indicador booleano de acierto de caché semántica (por defecto FALSE en registros previos y nuevos)
ALTER TABLE request_logs ADD COLUMN cache_hit BOOLEAN NOT NULL DEFAULT FALSE;

-- Índice para optimizar el conteo de aciertos de caché y el cálculo de ahorros FinOps
CREATE INDEX idx_request_logs_cache_hit ON request_logs (cache_hit);
