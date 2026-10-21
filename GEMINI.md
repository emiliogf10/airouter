# Contexto Global: AI Cost Router

Este archivo contiene las reglas globales, decisiones arquitectónicas y el estado técnico del proyecto. **Todos los agentes deben leer este archivo antes de ejecutar cualquier acción.**

## 🛠️ Stack Tecnológico
*   **Backend:** Java 21, Spring Boot 4.1.1 (compatible con convenciones Spring Boot 3+), Spring Data JPA, H2 (desarrollo).
*   **Frontend:** React 19, Vite, Tailwind v4.
*   **Gestión de Base de Datos:** Flyway (`spring-boot-starter-flyway` + `flyway-core`, validación estricta de esquemas con `spring.jpa.hibernate.ddl-auto=validate`).

## 📂 Estructura de Directorios Clave
*   **Raíz del repositorio:** Contiene `GEMINI.md`, `PLAN_DE_ACCION.md`, `README.md` y la carpeta `.agentes/`.
*   **Proyecto Backend Maven (`airouter/`):** El código fuente (`airouter/src/`), el `pom.xml` (`airouter/pom.xml`) y el wrapper de Maven (`airouter/mvnw`, `airouter/mvnw.cmd`) residen dentro de la subcarpeta `airouter/`. Todos los comandos de Maven deben ejecutarse desde `airouter/`.
*   **Paquete base Java:** `com.tuproyecto.airouter`.

## 📐 Convenciones Globales de Arquitectura
*   **Lombok:** Uso intensivo en Java (`@Data`, `@RequiredArgsConstructor`, `@Slf4j`) para evitar boilerplate. **Prohibido** usar `@Data`, `@ToString` o `@EqualsAndHashCode` en entidades JPA (`@Entity`); en ellas se usa `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` y se implementa `equals`/`hashCode` manualmente por `id`.
*   **Idioma y Documentación Javadoc Obligatoria:** Todo el código nuevo que se implemente debe estar exhaustivamente documentado con **Javadoc en español** (clases, interfaces, `record`, métodos públicos y privados relevantes, constantes, atributos y etiquetas `@param`, `@return`, `@throws`). Asimismo, todos los comentarios explicativos en SQL/código y mensajes de commit deben estar en español.
*   **Manuales del Proyecto (En Hoja de Ruta):** El proyecto contempla la creación y mantenimiento de dos documentos clave: un **Manual de Usuario** (explicación funcional del uso de la aplicación) y un **Manual del Programador** (explicación técnica detallada de la arquitectura y funcionamiento interno).
*   **Commits:** Uso de Semantic Versioning (ej. `feat:`, `fix:`, `chore:`, `docs:`).

## 🔌 Puertos y Entorno (Desarrollo)
*   Backend Spring Boot: `:8080` (por defecto). Consola H2 en `/h2-console` (`jdbc:h2:mem:airouterdb`).
*   Frontend Vite: `:5173` (por defecto).

## 📝 Registro de Decisiones (ADRs)
*   *2026-10-20:* Se decide utilizar arquitectura por capas estándar (Controllers -> Services -> Repositories) con especialización de agentes de IA por dominio.
*   *2026-10-20:* Se configura Flyway con `spring-boot-starter-flyway` (requerido por Spring Boot 4.1.1 para la autoconfiguración) y validación estricta de esquema JPA (`ddl-auto=validate`) sobre H2 en memoria.

