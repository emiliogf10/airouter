# Plan de Acción: AI Cost Router

Este documento es la fuente de verdad del estado del proyecto. Los agentes deben consultarlo antes de iniciar cualquier tarea compleja.

## 🎯 1. Backlog Priorizado (Por Hacer)
*   **[ALTA - FINOPS]** **Cortafuegos Presupuestario por Usuario (*Hard Budget Cap*):** Permitir definir un límite de gasto mensual por usuario (ej. `10,00 € / mes`). Al alcanzar el 80% del presupuesto, forzar automáticamente el enrutamiento al modelo económico (`gemini-2.5-flash`); al llegar al 100%, bloquear con `HTTP 402 Payment Required` hasta recarga o nuevo ciclo.
*   **[MEDIA - EVOLUCIÓN SAAS]** **Gestión de Usuarios y Autenticación:** Migración Flyway `V2`, registro/login con JWT u OAuth2, perfiles de usuario y gestión segura de múltiples `API Keys` persistidas en base de datos (cifradas/hasheadas).
*   **[MEDIA - EVOLUCIÓN SAAS]** **Billetera Prepago (*Token Credits Wallet*) y Pasarela de Pagos (`Billing`):** Integración con Stripe/PayPal para compra de paquetes de créditos prepago (ej. `5 €`, `20 €`) y descuento en tiempo real de micro-céntimos por petición en `EUR €` / `USD $`.
*   **[MEDIA - RENDIMIENTO/AHORRO]** **Caché Semántica de Prompts (*Zero-Cost Cache*):** Si dos peticiones contienen el mismo prompt (o uno equivalente), devolver la respuesta cacheada en `< 5 ms` con coste `0,000000 €`, contabilizando el 100% del importe dentro del indicador de *Ahorro Generado*.
*   **[BAJA - UX/ANALÍTICA]** **Visor Modal de Detalle (`Inspector Drawer`), Exportación CSV y Tema Claro/Oscuro:** Panel lateral estilo Vercel al pulsar una fila de `RecentLogsTable` (mostrando prompt íntegro, botón de copiar respuesta, latencia en `ms` y coste individual), exportación contable a CSV/JSON y conmutador `Light/Dark Theme`.

## 🚀 2. Historial de Cambios (Hecho)
*   *2026-10-21:* Implementación de la conexión híbrida de 3 niveles con la API oficial de Google Gemini (`GeminiRestClient` con `RestClient` de Spring Boot): soporte **BYOK (*Bring Your Own Key*)** mediante cabecera `X-API-Key`, soporte de clave global `GEMINI_API_KEY` en el servidor y fallback resiliente al modo simulación; indicador visual `Gemini API Real` vs `Modo Simulación` en el Dashboard y ampliación de suites QA a **46 tests en Backend** y **11 tests en Frontend** (57 tests totales).
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

