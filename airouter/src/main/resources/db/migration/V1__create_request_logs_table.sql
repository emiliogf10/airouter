-- =============================================================================
-- Migración: V1__create_request_logs_table.sql
-- Propósito: Crear la tabla principal request_logs para almacenar el historial
--            de peticiones enrutadas por el AI Cost Router y el consumo estimado
--            de tokens, junto con los índices de alto rendimiento para filtrado.
-- Compatibilidad: SQL estándar compatible con H2 (desarrollo/test) y PostgreSQL (producción).
-- =============================================================================

-- Creación de la tabla de registros de peticiones (request_logs)
CREATE TABLE request_logs (
    -- Identificador único universal de la petición
    id UUID NOT NULL,
    -- Identificador del usuario o cliente que realiza la petición
    user_id VARCHAR(255) NOT NULL,
    -- Contenido íntegro del prompt enviado al enrutador de IA
    prompt_content TEXT NOT NULL,
    -- Nombre del modelo de IA al que se enrutó la petición (puede ser nulo antes de enrutar)
    model_routed VARCHAR(255),
    -- Número estimado de tokens consumidos por la petición
    tokens_estimated INTEGER NOT NULL,
    -- Fecha y hora exacta de creación del registro
    created_at TIMESTAMP NOT NULL,
    -- Restricción de clave primaria
    CONSTRAINT pk_request_logs PRIMARY KEY (id)
);

-- Índices para optimizar consultas de filtrado y agregación en el Dashboard (Big Data)
-- Índice por usuario para consultas de historial y gasto por cliente
CREATE INDEX idx_request_logs_user_id ON request_logs (user_id);

-- Índice por fecha de creación para rangos temporales y gráficas de evolución
CREATE INDEX idx_request_logs_created_at ON request_logs (created_at);

-- Índice por modelo enrutado para analíticas de distribución de uso de LLMs
CREATE INDEX idx_request_logs_model_routed ON request_logs (model_routed);
