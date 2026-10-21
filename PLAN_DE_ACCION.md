# Plan de Acción: AI Cost Router

Este documento es la fuente de verdad del estado del proyecto. Los agentes deben consultarlo antes de iniciar cualquier tarea compleja.

## 🎯 1. Backlog Priorizado (Por Hacer)
*   **[ALTA]** Añadir endpoint de métricas/estadísticas agregadas de ahorro y consumo de tokens para el Dashboard.
*   **[MEDIA]** Iniciar el proyecto frontend (Dashboard) con Vite + React + Tailwind v4 conectado a `/api/v1/router`.
*   **[BAJA]** Conectar clientes HTTP reales (`RestClient`) para proveedores de IA (Gemini / OpenAI) mediante variables de entorno.
*   **[DOCUMENTACIÓN]** Crear el **Manual de Usuario** (`docs/MANUAL_DE_USUARIO.md`), explicando de forma clara y funcional el uso de la aplicación.
*   **[DOCUMENTACIÓN]** Crear el **Manual del Programador** (`docs/MANUAL_DEL_PROGRAMADOR.md`), explicando de forma técnica y detallada la arquitectura, flujo interno, modelo de datos y decisiones de diseño.

## 🚀 2. Historial de Cambios (Hecho)
*   *2026-10-20:* Configuración de seguridad base (`RateLimitingFilter` con ventana de 60s por `X-API-Key`/IP, respuesta HTTP 429 con `Retry-After` y enmascaramiento de claves en logs; `SecurityConfig` con CORS restrictivo para `http://localhost:5173`) y ampliación de suite QA a 33 tests.
*   *2026-10-20:* Implementación de `RequestLogRepository`, DTOs (`PromptRequest`, `RouterResponse`, `ErrorResponse`), `RouterService` con estimación de tokens y enrutamiento simulado (`gemini-2.5-flash` / `gemini-2.5-pro`), `GatewayController` (`/api/v1/router`) y batería de 25 tests (unitarios e integración) por QA.
*   *2026-10-20:* Configuración de Flyway + H2 en memoria, migración `V1__create_request_logs_table.sql` con índices y entidad JPA `RequestLog`.
*   *2026-10-20:* Inicialización del proyecto Spring Boot (Web, JPA, H2, Lombok).
*   *2026-10-20:* Creación de la arquitectura de agentes (`.agentes/`) y reglas de orquestación.
*   *2026-10-20:* Estructura de carpetas base (`controllers`, `services`, `repositories`, `models`, `clients`).

## ❌ 3. Descartados / No Implementados
*   *(Aún no hay elementos descartados. Aquí anotaremos ideas que decidamos posponer o rechazar por complejidad/coste).*

