# Plan de Acción: AI Cost Router

Este documento es la fuente de verdad del estado del proyecto. Los agentes deben consultarlo antes de iniciar cualquier tarea compleja.

## 🎯 1. Backlog Priorizado (Por Hacer)
*   **[ALTA]** Conectar clientes HTTP reales (`RestClient`) para proveedores de IA (Gemini / OpenAI) mediante variables de entorno (`GEMINI_API_KEY` con fallback automático al modo simulación).
*   **[MEDIA - EVOLUCIÓN SAAS]** Implementar **Gestión de Usuarios y Autenticación** (migración Flyway `V2`, registro/login con JWT u OAuth2, perfiles de usuario y gestión de múltiples `API Keys` persistidas en BD).
*   **[MEDIA - EVOLUCIÓN SAAS]** Implementar **Formas de Pago, Billetera y Presupuestos (`Billing`)** (integración con pasarela de pagos tipo Stripe/PayPal, recarga de créditos prepago y límites de gasto mensual por usuario en `EUR €` / `USD $`).
*   **[BAJA - UX/ANALÍTICA]** Añadir exportación de historial a CSV/JSON, visor modal de detalle de prompt/respuesta y modo claro/oscuro (`Light/Dark Theme`) en el Dashboard.

## 🚀 2. Historial de Cambios (Hecho)
*   *2026-10-21:* Rediseño minimalista premium del Dashboard Frontend (estilo Vercel / `shadcn/ui` con paleta `zinc`/`slate`, acentos `indigo`/`emerald`, cero CSS en línea, subcomponentes `MetricCard` y `RecentLogsTable`, *Skeleton Loaders* y *Empty States* con CTA), ampliación de suite QA a 10 tests en Frontend (48 tests totales sumando Backend) y creación del **Manual de Usuario** (`docs/MANUAL_DE_USUARIO.md`) y **Manual del Programador** (`docs/MANUAL_DEL_PROGRAMADOR.md`).
*   *2026-10-21:* Implementación del Dashboard Frontend analítico (`frontend/`) en React 19, Vite, TypeScript y Tailwind CSS v4 conectado a `/api/v1/router`, con selector interactivo de divisa **`EUR €` (por defecto) / `USD $`** (`1 USD = 0.92 EUR`), tarjetas KPI, simulador de prompts (`PromptPlayground`), gráficas (`recharts`), tabla de historial y suite QA de 8 tests en Vitest + Testing Library.
*   *2026-10-21:* Implementación del endpoint de métricas y ahorro económico (`GET /api/v1/router/stats`), DTO `RouterStatsResponse`, cálculo de precisión financiera con `BigDecimal` en `RouterService` y ampliación de suite QA a 38 tests.
*   *2026-10-20:* Configuración de seguridad base (`RateLimitingFilter` con ventana de 60s por `X-API-Key`/IP, respuesta HTTP 429 con `Retry-After` y enmascaramiento de claves en logs; `SecurityConfig` con CORS restrictivo para `http://localhost:5173`) y ampliación de suite QA a 33 tests.
*   *2026-10-20:* Implementación de `RequestLogRepository`, DTOs (`PromptRequest`, `RouterResponse`, `ErrorResponse`), `RouterService` con estimación de tokens y enrutamiento simulado (`gemini-2.5-flash` / `gemini-2.5-pro`), `GatewayController` (`/api/v1/router`) y batería de 25 tests (unitarios e integración) por QA.
*   *2026-10-20:* Configuración de Flyway + H2 en memoria, migración `V1__create_request_logs_table.sql` con índices y entidad JPA `RequestLog`.
*   *2026-10-20:* Inicialización del proyecto Spring Boot (Web, JPA, H2, Lombok).
*   *2026-10-20:* Creación de la arquitectura de agentes (`.agentes/`) y reglas de orquestación.
*   *2026-10-20:* Estructura de carpetas base (`controllers`, `services`, `repositories`, `models`, `clients`).

## ❌ 3. Descartados / No Implementados
*   *(Aún no hay elementos descartados. Aquí anotaremos ideas que decidamos posponer o rechazar por complejidad/coste).*

