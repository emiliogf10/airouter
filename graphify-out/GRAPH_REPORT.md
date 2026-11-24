# Graph Report - airouter  (2026-11-22)

## Corpus Check
- 88 files · ~67,995 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 14 file(s) not represented in the graph (top: (none) 7, .properties 3, .example 1)

## Summary
- 705 nodes · 1823 edges · 41 communities (18 shown, 23 thin omitted)
- Extraction: 88% EXTRACTED · 12% INFERRED · 0% AMBIGUOUS · INFERRED: 220 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `1401763f`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Frontend Dashboard & UI Components
- RequestLog
- Core Routing & DLP Service
- GatewayController.java
- Google Gemini Hybrid Client
- RouterService
- Multi-Agent Architecture & Docs
- TypeScript App Configuration
- TypeScript Node Configuration
- Frontend Package Dependencies
- ESLint & Code Quality
- RequestLogRepositoryTest
- Flyway Request Logs Schema (V1 & V3)
- React & Chart Runtime Libs
- Vite & Tailwind Bundler Config
- Vitest Setup & Browser Mocks
- Frontend NPM Scripts
- Spring Boot Application Entrypoint
- Flyway SHA-256 API Keys Schema (V4)
- Flyway FinOps Budgets Schema (V2)
- Flyway Prepaid Wallets Schema (V5)
- TypeScript Project References
- tailwindcss@^4.1.4
- org.springframework.boot.test.context.SpringBootTest
- Maven Artifact Coordinate
- AGENTS.md
- RouterService.java
- @testing-library/react
- @types/react@^19.1.2

## God Nodes (most connected - your core abstractions)
1. `RouterService` - 61 edges
2. `RouterServiceTest` - 39 edges
3. `RequestLog` - 32 edges
4. `PromptRequest` - 24 edges
5. `GatewayControllerTest` - 23 edges
6. `ApiKey` - 22 edges
7. `GatewayController` - 21 edges
8. `UserWallet` - 21 edges
9. `UserBudget` - 19 edges
10. `RateLimitingFilter` - 19 edges

## Surprising Connections (you probably didn't know these)
- `AI Cost Router Overview & API Reference` --references--> `Manual Functional Testing Checklist (12 Scenarios)`  [EXTRACTED]
  README.md → docs/GUIA_DE_PRUEBAS_MANUALES.md
- `React 19 + Tailwind v4 Frontend Architecture` --references--> `Frontend HTML Entrypoint & Color-Scheme Bootstrapper (frontend/index.html)`  [EXTRACTED]
  docs/MANUAL_DEL_PROGRAMADOR.md → frontend/index.html
- `Frontend UI Specialist (02-frontend-ui)` --implements--> `React 19 + Tailwind v4 Frontend Architecture`  [EXTRACTED]
  .agentes/02-frontend-ui.md → docs/MANUAL_DEL_PROGRAMADOR.md
- `Backend Java Specialist (01-backend-java)` --implements--> `RouterService (Heuristics, DLP, Semantic Cache, FinOps & Wallet)`  [EXTRACTED]
  .agentes/01-backend-java.md → docs/MANUAL_DEL_PROGRAMADOR.md
- `RouterService (Heuristics, DLP, Semantic Cache, FinOps & Wallet)` --implements--> `3-Tier Hybrid Gemini Execution (BYOK / Server Key / Resilient Simulation)`  [EXTRACTED]
  docs/MANUAL_DEL_PROGRAMADOR.md → README.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Core Project Governance & Documentation Suite** — gemini_global_context, plan_de_accion_roadmap, readme_ai_cost_router, docs_manual_del_programador_spec, docs_manual_de_usuario_guide, docs_guia_de_pruebas_manuales_checklist [EXTRACTED 1.00]
- **End-to-End Request Routing, FinOps, DLP & Security Pipeline** — docs_manual_del_programador_security_hardening, docs_manual_del_programador_routerservice, readme_hybrid_routing_3_tiers, readme_flyway_migrations_v1_v6, docs_manual_del_programador_frontend_architecture [EXTRACTED 1.00]
- **AI Cost Router Domain-Specialized Multi-Agent Team** — agentes_00_orquestador_tech_lead, agentes_01_backend_java_specialist, agentes_02_frontend_ui_specialist, agentes_03_qa_tester_specialist, agentes_04_auditor_seguridad_specialist, agentes_05_arquitecto_datos_specialist [EXTRACTED 1.00]

## Communities (41 total, 23 thin omitted)

### Community 0 - "Frontend Dashboard & UI Components"
Cohesion: 0.06
Nodes (85): vitest@^3.1.2, App(), INITIAL_STATS, ApiKeyManagerCard(), ApiKeyManagerCardProps, legacyCopyApiKeyFallback(), BudgetControlCard(), BudgetControlCardProps (+77 more)

### Community 1 - "RequestLog"
Cohesion: 0.10
Nodes (4): ApiKey, RequestLog, UserBudget, UserWallet

### Community 2 - "Core Routing & DLP Service"
Cohesion: 0.09
Nodes (5): PromptRequest, GeminiRestClientTest, SecurityAndRateLimitingTest, GatewayControllerTest, RouterServiceTest

### Community 3 - "GatewayController.java"
Cohesion: 0.06
Nodes (13): GatewayController, BudgetExceededException, GlobalExceptionHandler, ApiKeyCreatedResponse, ApiKeyResponse, CreateApiKeyRequest, ErrorResponse, RouterResponse (+5 more)

### Community 4 - "Google Gemini Hybrid Client"
Cohesion: 0.06
Nodes (10): GeminiRestClient, GeminiCandidate, GeminiContent, GeminiGenerateRequest, GeminiGenerateResponse, GeminiPart, RateLimitingFilter, RateLimitBucket (+2 more)

### Community 6 - "RouterService"
Cohesion: 0.17
Nodes (4): RouterService, CachedPromptEntry, DlpSanitizationResult, PromptCompressionResult

### Community 7 - "Multi-Agent Architecture & Docs"
Cohesion: 0.14
Nodes (18): Tech Lead & Orquestador (00-orquestador), Backend Java Specialist (01-backend-java), Frontend UI Specialist (02-frontend-ui), QA Tester Specialist (03-qa-tester), Security & DevSecOps Specialist (04-auditor-seguridad), Data Architect Specialist (05-arquitecto-datos), Manual Functional Testing Checklist (12 Scenarios), User Manual — AI Cost Router Dashboard & FinOps Guide (+10 more)

### Community 8 - "TypeScript App Configuration"
Cohesion: 0.10
Nodes (20): compilerOptions, allowImportingTsExtensions, isolatedModules, jsx, lib, module, moduleDetection, moduleResolution (+12 more)

### Community 9 - "TypeScript Node Configuration"
Cohesion: 0.11
Nodes (17): compilerOptions, allowImportingTsExtensions, isolatedModules, lib, module, moduleDetection, moduleResolution, noEmit (+9 more)

### Community 11 - "Frontend Package Dependencies"
Cohesion: 0.13
Nodes (14): jsdom@^26.1.0, @testing-library/user-event@^14.6.1, @types/node@^22.14.1, @types/react-dom@^19.1.2, typescript@~5.8.3, name, private, type (+6 more)

### Community 12 - "ESLint & Code Quality"
Cohesion: 0.19
Nodes (13): devDependencies, eslint@^9.25.0, @eslint/js@^9.25.0, eslint-plugin-react-hooks@^5.2.0, eslint-plugin-react-refresh@^0.5.7, globals@^16.0.0, typescript-eslint@^8.72.2, eslint (+5 more)

### Community 14 - "RequestLogRepositoryTest"
Cohesion: 0.36
Nodes (5): ApiKeyRepository, RequestLogRepository, UserBudgetRepository, UserWalletRepository, RequestLogRepositoryTest

### Community 15 - "Flyway Request Logs Schema (V1 & V3)"
Cohesion: 0.43
Nodes (5): idx_request_logs_created_at, idx_request_logs_model_routed, idx_request_logs_user_id, request_logs, idx_request_logs_cache_hit

### Community 16 - "React & Chart Runtime Libs"
Cohesion: 0.29
Nodes (7): dependencies, lucide-react@^1.73.0, react@^19.1.0, react-dom@^19.1.0, recharts@^2.15.3, react-dom, recharts

### Community 17 - "Vite & Tailwind Bundler Config"
Cohesion: 0.29
Nodes (6): @tailwindcss/vite@^4.1.4, vite@^6.3.2, @vitejs/plugin-react@^4.4.1, @tailwindcss/vite, vite, @vitejs/plugin-react

### Community 18 - "Vitest Setup & Browser Mocks"
Cohesion: 0.29
Nodes (3): @testing-library/jest-dom@^6.6.3, ResizeObserverMock, @testing-library/jest-dom

### Community 19 - "Frontend NPM Scripts"
Cohesion: 0.33
Nodes (6): scripts, build, dev, lint, preview, test

### Community 21 - "Flyway SHA-256 API Keys Schema (V4)"
Cohesion: 0.83
Nodes (3): api_keys, idx_api_keys_key_hash, idx_api_keys_user_id

## Knowledge Gaps
- **5 isolated node(s):** `RFC-4180`, `RFC-4180`, `com.tuproyecto:airouter`, `Frontend HTML Entrypoint & Color-Scheme Bootstrapper (frontend/index.html)`, `QA Tester Specialist (03-qa-tester)`
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 132 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **23 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `RouterService` connect `RouterService` to `RequestLog`, `.createApiKey`, `org.springframework.transaction.annotation.Transactional`, `GatewayController.java`, `.getUserBudget`, `RouterService.java`, `Core Routing & DLP Service`, `Backend Integration & Security Tests`, `Google Gemini Hybrid Client`, `RouterServiceTest.java`?**
  _High betweenness centrality (0.068) - this node is a cross-community bridge._
- **What connects `RFC-4180`, `RFC-4180`, `com.tuproyecto:airouter` to the rest of the system?**
  _5 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Frontend Dashboard & UI Components` be split into smaller, more focused modules?**
  _Cohesion score 0.056251102098395345 - nodes in this community are weakly interconnected._
- **Why does `RateLimitingFilter` connect `Google Gemini Hybrid Client` to `Core Routing & DLP Service`?**
  _High betweenness centrality (0.025) - this node is a cross-community bridge._
- **Should `RequestLog` be split into smaller, more focused modules?**
  _Cohesion score 0.09935064935064936 - nodes in this community are weakly interconnected._
- **Why does `RequestLog` connect `RequestLog` to `Core Routing & DLP Service`, `RouterServiceTest.java`, `RouterService.java`, `RequestLogRepositoryTest`?**
  _High betweenness centrality (0.024) - this node is a cross-community bridge._
- **Should `Core Routing & DLP Service` be split into smaller, more focused modules?**
  _Cohesion score 0.08526315789473685 - nodes in this community are weakly interconnected._