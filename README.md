# 🚀 AI Cost Router

**AI Cost Router** es un API Gateway inteligente desarrollado con **Java 21** y **Spring Boot** que intercepta peticiones dirigidas a modelos de lenguaje (LLMs), evalúa su complejidad y las enruta automáticamente hacia el modelo más eficiente en coste, registrando analíticas detalladas de consumo de tokens en tiempo real.

---

## 🛠️ Stack Tecnológico

* **Backend:** Java 21, Spring Boot 4.1.1 (WebMVC, Spring Data JPA), Lombok.
* **Base de Datos y Migraciones:**
  * **Desarrollo / Tests:** H2 Database (en memoria).
  * **Producción (Objetivo):** PostgreSQL.
  * **Control de Esquema:** **Flyway** (`spring-boot-starter-flyway` + `flyway-core`) con validación estricta en Hibernate (`spring.jpa.hibernate.ddl-auto=validate`).
* **Frontend:** React 19, Vite, TypeScript, Tailwind CSS v4, Recharts, Lucide React, Vitest + Testing Library.

---

## 📂 Estructura del Repositorio

```text
.
├── .agentes/                   # Definición de roles y reglas de los agentes de IA
│   ├── 00-orquestador.md       # Tech Lead y coordinador general
│   ├── 01-backend-java.md      # Especialista en controladores, servicios y clientes LLM
│   ├── 02-frontend-ui.md       # Especialista en Dashboard React + Tailwind
│   ├── 03-qa-tester.md         # Especialista en pruebas JUnit, Mockito y E2E
│   ├── 04-auditor-seguridad.md # Especialista en Spring Security, CORS y Rate Limiting
│   └── 05-arquitecto-datos.md  # Especialista en Flyway, SQL e índices
├── airouter/                   # Proyecto Maven / Spring Boot (Backend)
│   ├── src/main/java/com/tuproyecto/airouter/
│   │   ├── clients/            # Clientes HTTP hacia proveedores de IA (OpenAI, Gemini...)
│   │   ├── config/             # Configuración de seguridad, CORS y filtro Rate Limiting
│   │   ├── controllers/        # Endpoints REST (/api/v1/...) y manejo global de errores
│   │   ├── models/             # Entidades JPA (entities/) y DTOs (dto/)
│   │   ├── repositories/       # Repositorios Spring Data JPA
│   │   └── services/           # Lógica de negocio, estimación de tokens y enrutamiento
│   ├── src/main/resources/
│   │   ├── db/migration/       # Scripts SQL versionados de Flyway (V1__..., V2__...)
│   │   └── application.properties
│   └── pom.xml
├── frontend/                   # Aplicación React 19 + Vite + Tailwind CSS v4 (Dashboard)
│   ├── src/
│   │   ├── components/         # Header, MetricCard, StatsCards, PromptPlayground, RecentLogsTable...
│   │   ├── services/           # Cliente HTTP tipado hacia /api/v1/router
│   │   ├── types/              # Contratos TypeScript alineados con los DTOs de Java
│   │   ├── utils/              # Conversión y formateo de divisa (EUR € / USD $)
│   │   └── test/               # Suite de pruebas unitarias y de integración (Vitest)
│   ├── package.json
│   └── vite.config.ts
├── docs/                       # Documentación oficial del proyecto
│   ├── MANUAL_DE_USUARIO.md    # Guía funcional paso a paso del Dashboard y simulador
│   └── MANUAL_DEL_PROGRAMADOR.md # Arquitectura técnica, modelo de datos, seguridad y costes
├── GEMINI.md                   # Contexto global, reglas arquitectónicas y ADRs
└── PLAN_DE_ACCION.md           # Backlog priorizado e historial de cambios
```

---

## 🗄️ Base de Datos y Migraciones (Flyway + H2)

El esquema de base de datos está gobernado exclusivamente por **Flyway** mediante scripts SQL ubicados en `airouter/src/main/resources/db/migration/`. Hibernate opera en modo `validate`, por lo que la aplicación verificará al arrancar que todas las clases `@Entity` coincidan exactamente con las tablas definidas en Flyway.

### Migraciones Activas
* **`V1__create_request_logs_table.sql`**: Crea la tabla `request_logs` (`id`, `user_id`, `prompt_content`, `model_routed`, `tokens_estimated`, `created_at`) y los índices B-Tree de alto rendimiento (`idx_request_logs_user_id`, `idx_request_logs_created_at`, `idx_request_logs_model_routed`).

### Acceso a la Consola H2 (Desarrollo)
Con el backend en ejecución, puedes inspeccionar la base de datos en memoria desde el navegador:
* **URL:** `http://localhost:8080/h2-console`
* **Driver Class:** `org.h2.Driver`
* **JDBC URL:** `jdbc:h2:mem:airouterdb`
* **User Name:** `sa`
* **Password:** *(dejar en blanco)*

---

## 🔌 Endpoints de la API REST (`/api/v1/router`)

### 1. Evaluar y Enrutar un Prompt (`POST /api/v1/router/prompt`)
Analiza la complejidad del prompt, calcula los tokens estimados, decide el modelo óptimo en coste, persiste el registro en base de datos y devuelve la respuesta simulada.

* **Heurística de Enrutamiento:**
  * **`gemini-2.5-flash` (Rápido / Económico):** Prompts con `<= 50` tokens estimados y sin términos de alta complejidad.
  * **`gemini-2.5-pro` (Avanzado / Razonamiento):** Prompts con `> 50` tokens estimados o que contienen palabras clave complejas (`arquitectura`, `refactorizar`, `optimizar`, `analizar en profundidad`).

**Ejemplo de Petición (`POST`):**
```json
{
  "userId": "demo-user",
  "prompt": "Hola, ¿qué día es hoy?"
}
```

**Ejemplo de Respuesta (`200 OK`):**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "userId": "demo-user",
  "promptContent": "Hola, ¿qué día es hoy?",
  "modelRouted": "gemini-2.5-flash",
  "tokensEstimated": 6,
  "simulatedResponse": "[Simulación AI Cost Router] Petición procesada exitosamente por el modelo 'gemini-2.5-flash' (6 tokens estimados).",
  "createdAt": "2026-10-20T22:30:00"
}
```

### 2. Consultar Historial de Peticiones (`GET /api/v1/router/logs`)
Devuelve la lista de peticiones evaluadas ordenadas de más reciente a más antigua (`createdAt DESC`).
* **Global:** `GET /api/v1/router/logs`
* **Filtrado por usuario:** `GET /api/v1/router/logs?userId=demo-user`

### 3. Consultar Métricas de Tráfico y Ahorro Económico (`GET /api/v1/router/stats`)
Calcula y devuelve las estadísticas agregadas de uso de tokens, distribución de peticiones entre modelos (`gemini-2.5-flash` a $0.15/1M tokens vs `gemini-2.5-pro` a $1.25/1M tokens) y el ahorro económico estimado en USD frente a haber procesado todo con el modelo Pro.
* **Global:** `GET /api/v1/router/stats`
* **Filtrado por usuario:** `GET /api/v1/router/stats?userId=demo-user`

**Ejemplo de Respuesta (`200 OK`):**
```json
{
  "totalRequests": 2,
  "totalTokensEstimated": 1000000,
  "fastModelRequests": 1,
  "proModelRequests": 1,
  "fastModelTokens": 500000,
  "proModelTokens": 500000,
  "estimatedActualCostUsd": 0.700000,
  "estimatedBaselineProCostUsd": 1.250000,
  "estimatedSavingsUsd": 0.550000,
  "savingsPercentage": 44.0
}
```

---

## 🛡️ Seguridad, Rate Limiting y CORS

* **Rate Limiting (`RateLimitingFilter`):**
  * Protege el endpoint `POST /api/v1/router/prompt` contra abuso de facturación (Billing Abuse / DDoS).
  * Identifica al cliente por la cabecera `X-API-Key` (si se incluye) o por dirección IP (`RemoteAddr`).
  * Por defecto permite **20 peticiones por minuto** por cliente (`airouter.security.rate-limit.requests-per-minute=20`).
  * Al superar la cuota, responde con **HTTP `429 Too Many Requests`**, cabecera `Retry-After` (segundos restantes de la ventana) y cuerpo JSON `ErrorResponse`, enmascarando siempre las claves en los logs de auditoría (`apikey:sk-***`).
* **Política CORS (`SecurityConfig`):**
  * Configurada para `/api/v1/**` autorizando exclusivamente el origen del Frontend (`http://localhost:5173`, configurable en `airouter.security.cors.allowed-origins`), métodos `GET`, `POST`, `OPTIONS`, y exponiendo la cabecera `Retry-After`.

---

## 🚀 Ejecución y Comandos de Desarrollo

> ⚠️ **Importante:** Todos los comandos de Maven (`./mvnw`) deben ejecutarse desde el subdirectorio `airouter/`.

### 1. Compilar y ejecutar tests (incluye validación de Flyway + JPA)
```bash
cd airouter
./mvnw clean test
```
*(En Windows PowerShell: `.\mvnw.cmd clean test`)*

### 2. Arrancar el servidor Backend en local (Puerto `:8080`)
```bash
cd airouter
./mvnw spring-boot:run
```
*(En Windows PowerShell: `.\mvnw.cmd spring-boot:run`)*

### 3. Ejecutar el Dashboard Frontend en local (Puerto `:5173`)
El Frontend incluye un selector interactivo de divisa en la cabecera (**`EUR €` por defecto** con conversión `1 USD = 0.92 EUR`, o **`USD $`** para consultar las tarifas nativas de los proveedores de IA), simulador de enrutamiento de prompts, tarjetas KPI, gráficas de distribución y tabla de historial en tiempo real.

```bash
cd frontend
npm install
npm run dev
```

### 4. Ejecutar tests y validación estática del Frontend
```bash
cd frontend
npm test
npm run lint
npm run build
```

