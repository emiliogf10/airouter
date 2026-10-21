# 📖 Manual de Usuario — AI Cost Router

Bienvenido al **Manual de Usuario de AI Cost Router**, la plataforma inteligente de enrutamiento y optimización de costes para modelos de lenguaje (LLMs).

---

## 🎯 1. ¿Qué es AI Cost Router y para qué sirve?

Cuando una empresa o aplicación envía peticiones (*prompts*) a modelos de Inteligencia Artificial, utilizar siempre el modelo más grande y potente (como `gemini-2.5-pro`) para preguntas sencillas dispara la factura mensual de forma innecesaria.

**AI Cost Router** actúa como una pasarela inteligente (*API Gateway*) y panel de control (*Dashboard*) que:
1. **Recibe tu consulta (*prompt*)** antes de enviarla al proveedor de IA.
2. **Evalúa automáticamente su complejidad** y longitud en tokens.
3. **Decide en milisegundos el modelo óptimo en coste:**
   * **Consultas simples o cortas (`<= 50` tokens):** Se envían a **`gemini-2.5-flash`** ($0.15 USD por millón de tokens), ahorrando hasta un **88%** respecto al modelo avanzado.
   * **Consultas complejas o de razonamiento profundo (`> 50` tokens o con palabras clave técnicas):** Se envían a **`gemini-2.5-pro`** ($1.25 USD por millón de tokens) para garantizar la máxima calidad de respuesta.
4. **Calcula y audita tu ahorro económico en tiempo real**, permitiéndote visualizar los costes tanto en **Euros (`EUR €`)** como en **Dólares (`USD $`)**.

---

## 🚀 2. Cómo poner en marcha la aplicación

Para utilizar la aplicación en tu equipo local necesitas tener iniciados el servidor **Backend** (puerto `:8080`) y el **Dashboard Frontend** (puerto `:5173`).

### Paso 1: Iniciar el Backend
Abre una terminal en la raíz del proyecto y ejecuta:
```powershell
cd airouter
.\mvnw.cmd spring-boot:run
```

### Paso 2: Iniciar el Dashboard Frontend
Abre una segunda terminal en la raíz del proyecto y ejecuta:
```powershell
cd frontend
npm run dev
```

### Paso 3: Acceder desde el navegador
Abre tu navegador web y entra en:
👉 **`http://localhost:5173`**

---

## 🖥️ 3. Recorrido por el Dashboard (Estilo Minimalista)

El Dashboard está diseñado con una interfaz limpia de alto contraste para facilitar la lectura de métricas financieras y técnicas:

### 3.1. Barra de Navegación Superior (`Header`)
En la parte superior de la pantalla encontrarás tres herramientas de control global:
* **Filtro por Usuario (`Filtrar por userId...`):** Escribe el identificador de un usuario o aplicación cliente (por ejemplo, `demo-user` o `alice`) para que todas las tarjetas de métricas, gráficas y la tabla inferior muestren únicamente los datos de ese usuario. Pulsa el icono **`X`** para volver a la vista global.
* **Conmutador de Divisa (`EUR €` | `USD $`):**
  * **`EUR €` (Activo por defecto):** Convierte automáticamente todos los costes a Euros (`1 USD = 0.92 EUR`) y los formatea con coma decimal (`0,506000 €`).
  * **`USD $`:** Muestra los costes en Dólares estadounidenses según las tarifas oficiales de los proveedores de IA (`$0.550000`).
  * *Nota:* El cambio es instantáneo y no recarga la página.
* **Botón `Sincronizar`:** Fuerza una actualización inmediata de las estadísticas y del historial desde el servidor.

---

### 3.2. Cuadrícula Superior de Métricas Clave (`MetricCard`)
Justo debajo de la barra superior se muestran **4 tarjetas de indicadores clave (KPIs)** con estados de carga suaves (*Skeleton Loaders*):
1. **Total de Peticiones:** Número total de consultas procesadas por el enrutador y su desglose entre el modelo rápido (*Flash*) y el avanzado (*Pro*).
2. **Coste Acumulado:** Lo que realmente has gastado gracias al enrutador, comparado justo debajo con el **Coste Base Pro** (lo que habrías pagado si hubieses enviado todas las peticiones directamente al modelo más caro).
3. **Tokens Usados:** Volumen total de tokens estimados consumidos entre todas las peticiones.
4. **Ahorro Generado:** Importe económico ahorrado (en `€` o `$`) y el **porcentaje de ahorro (`%`)** destacado en verde esmeralda.

---

### 3.3. Consola de Enrutamiento (`PromptPlayground`)
Ubicada en la columna izquierda de la zona central, te permite probar el motor de decisiones en directo:
1. ** Botones de Plantilla Rápida:**
   * **`Prompt Simple (Flash)`:** Rellena automáticamente una pregunta corta para comprobar cómo el router elige el modelo económico.
   * **`Prompt Complejo (Pro)`:** Rellena una consulta de arquitectura de software para comprobar cómo el router escala al modelo de razonamiento avanzado.
2. **Identificador de Usuario (`userId`):** Indica qué usuario realiza la petición (obligatorio).
3. **Clave de API (`X-API-Key` — Opcional):** Si introduces una clave (ej. `sk-mi-clave-123`), el sistema de seguridad contabilizará tu límite de peticiones por minuto de forma aislada para esa clave.
4. **Contenido del Prompt:** Escribe cualquier consulta y pulsa **`Ejecutar enrutamiento`**.
5. **Resultado de la Decisión:** Al instante aparecerá una tarjeta inferior indicando:
   * El **modelo asignado** (`gemini-2.5-flash` en esmeralda o `gemini-2.5-pro` en índigo).
   * Los **tokens estimados** consumidos.
   * La **respuesta generada** y el identificador único de auditoría (`UUID`).

---

### 3.4. Distribución de Consumo (`ModelDistributionChart`)
En la columna derecha de la zona central se visualiza la proporción de uso entre modelos:
* Barras de progreso horizontales comparando el porcentaje de tráfico y tokens absorbidos por **`gemini-2.5-flash`** frente a **`gemini-2.5-pro`**.
* Gráfica comparativa de volumen de peticiones y tokens.
* Si aún no hay datos registrados, verás un **Estado Vacío** informativo.

---

### 3.5. Registro de Peticiones Recientes (`RecentLogsTable`)
En la sección inferior se lista la tabla de auditoría en tiempo real ordenada desde la petición más reciente a la más antigua:
* Muestra **Fecha/Hora**, **Usuario**, **Extracto del Prompt**, **Modelo Enrutado** y **Tokens Estimados**.
* **Estado Vacío Interactivo:** Si la base de datos aún no tiene registros (o el usuario filtrado no tiene actividad), la tabla mostrará un bloque limpio con el botón **`Probar un prompt de ejemplo`**, que rellena y enfoca automáticamente el simulador superior para que puedas hacer tu primera prueba con un clic.

---

## 🛡️ 4. Mensajes de Error y Protección contra Abuso (Rate Limiting)

El sistema cuenta con un filtro de seguridad que protege tu presupuesto frente a bucles infinitos o ataques:
* **Límite de cuota:** Por defecto se permiten **20 peticiones por minuto** por cada dirección IP o `X-API-Key`.
* **Aviso `429 Too Many Requests`:** Si superas las 20 peticiones en menos de un minuto, la consola mostrará un aviso en color ámbar/rojo indicando exactamente **cuántos segundos debes esperar (`Retry-After`)** antes de que tu cuota se restablezca automáticamente.
* **Aviso `400 Bad Request`:** Si intentas enviar un formulario con el usuario o el prompt en blanco, el sistema te indicará qué campo debes completar.

---

## 🔮 5. Próximas Funcionalidades (En Hoja de Ruta)
* **Gestión de Usuarios y Autenticación:** Inicio de sesión por cuentas, perfiles de organización y generación de múltiples `API Keys` por proyecto.
* **Formas de Pago y Facturación (Billing):** Integración de pasarela de pagos (Stripe / PayPal), recarga de saldo prepago por créditos de tokens y alertas de límite de gasto mensual en Euros o Dólares.
* **Conexión en vivo con proveedores LLM:** Integración directa mediante clave de entorno con las APIs oficiales de Google Gemini y OpenAI.

