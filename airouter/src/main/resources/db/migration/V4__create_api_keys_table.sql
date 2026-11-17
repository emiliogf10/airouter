-- =============================================================================
-- Migración: V4__create_api_keys_table.sql
-- Propósito: Crear la tabla api_keys para la gestión y autenticación de claves
--            API por usuario mediante almacenamiento seguro con hash criptográfico
--            SHA-256 (cero secretos en texto plano en base de datos).
-- Compatibilidad: SQL estándar compatible con H2 (desarrollo/test) y PostgreSQL (producción).
-- =============================================================================

-- Creación de la tabla de claves API de usuarios (api_keys)
CREATE TABLE api_keys (
    -- Identificador único universal del registro de la clave API
    id UUID NOT NULL,
    -- Identificador del usuario propietario de la clave API
    user_id VARCHAR(120) NOT NULL,
    -- Nombre descriptivo asignado a la clave (ej. "Producción Web", "Microservicio Facturación")
    key_name VARCHAR(100) NOT NULL,
    -- Prefijo visible enmascarado para identificación segura en interfaz (ej. "air_live_9f8e...")
    key_prefix VARCHAR(32) NOT NULL,
    -- Hash criptográfico SHA-256 en hexadecimal (64 caracteres) de la clave completa
    key_hash VARCHAR(64) NOT NULL,
    -- Estado de activación de la clave (TRUE = activa, FALSE = revocada)
    active BOOLEAN NOT NULL DEFAULT TRUE,
    -- Fecha y hora exacta de emisión de la clave
    created_at TIMESTAMP NOT NULL,
    -- Restricción de clave primaria
    CONSTRAINT pk_api_keys PRIMARY KEY (id),
    -- Restricción de unicidad sobre el hash criptográfico SHA-256
    CONSTRAINT uk_api_keys_key_hash UNIQUE (key_hash)
);

-- Índice B-Tree por usuario para listar rápidamente las claves de un cliente
CREATE INDEX idx_api_keys_user_id ON api_keys (user_id);

-- Índice B-Tree por hash SHA-256 para validación de autenticación en tiempo constante O(log n)
CREATE INDEX idx_api_keys_key_hash ON api_keys (key_hash);
