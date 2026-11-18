# 📖 Manual de Usuario — AI Cost Router

Bienvenido al **Manual de Usuario de AI Cost Router**, la plataforma inteligente de enrutamiento, caché semántica y gobierno financiero (*FinOps*) para modelos de lenguaje (LLMs).

---

## 🎯 1. ¿Qué es AI Cost Router y qué problemas resuelve?

Cuando una empresa o desarrollador envía consultas (*prompts*) a modelos de Inteligencia Artificial, utilizar siempre el modelo más grande y costoso (`gemini-2.5-pro`) para preguntas sencillas o repetidas dispara la factura mensual de forma innecesaria.

**AI Cost Router** actúa como una pasarela inteligente (*API Gateway*) y panel de control analítico (*Dashboard*) que optimiza el gasto mediante **4 motores combinados**:

1. **Enrutamiento Inteligente por Complejidad:**
   * **Consultas simples o cortas (`<= 50` tokens):** Se dirigen automáticamente a **`gemini-2.5-flash`** ($0.15 USD / 1M tokens), ahorrando un **88%** frente al modelo avanzado.
   * **Consultas complejas o de arquitectura (`> 50` tokens o palabras clave técnicas):** Se dirigen a **`gemini-2.5-pro`** ($1.25 USD / 1M tokens) para garantizar la máxima capacidad de razonamiento.
2. **Caché Semántica de Coste Cero (*Zero-Cost Cache*):**
   * Si se envía una consulta equivalente a otra ya procesada anteriormente (ignorando diferencias de mayúsculas, minúsculas o espacios extra), el sistema devuelve la respuesta en milisegundos desde la caché con **coste `0,000000 €`** (`Caché Hit`), ahorrando el **100%** de la llamada.
3. **Cortafuegos Presupuestario por Usuario (*FinOps Hard Budget Cap*):**
   * Permite fijar un presupuesto máximo por usuario (en `EUR €` o `USD $`).
   * **Al llegar al 80% del presupuesto:** Activa automáticamente el **Modo Ahorro Forzado**, redirigiendo incluso los prompts complejos hacia `gemini-2.5-flash` para estirar el saldo disponible.
   * **Al llegar al 100% del presupuesto:** Activa el **Cortafuegos Duro**, bloqueando nuevas peticiones de coste con un aviso **HTTP `402 Payment Required`**.
4. **Modo Híbrido de Conexión a IA Real (*BYOK — Bring Your Own Key*):**
   * Puedes usar la aplicación gratis en **Modo Simulación** o pegar tu propia API Key de **Google AI Studio (`AIzaSy...`)** para recibir respuestas reales generadas en vivo por Google Gemini.

---

## 🚀 2. Cómo poner en marcha la aplicación

Para utilizar la aplicación en tu equipo necesitas abrir **dos terminales** desde la carpeta raíz del proyecto (`c:\Users\Emilio\Desktop\PERSONAL\Pruebas\airouter\airouter`):

### Paso 1: Iniciar el Servidor Backend (Puerto `:8080`)
```powershell
cd airouter
.\mvnw.cmd spring-boot:run
```
*Sabrás que ha terminado de arrancar cuando veas el mensaje `Started AirouterApplication`.*

### Paso 2: Iniciar el Dashboard Frontend (Puerto `:5173`)
En una segunda terminal:
```powershell
cd frontend
npm run dev
```

### Paso 3: Abrir la aplicación en el navegador
Entra en: 👉 **`http://localhost:5173`**

### Opción Alternativa: Arrancar todo en 1 solo comando con Docker Compose (PostgreSQL 17)
Si tienes **Docker Desktop** instalado, puedes levantar la Base de Datos PostgreSQL 17, el Backend y el Frontend de una sola vez desde la raíz del proyecto sin abrir dos terminales:
```powershell
docker compose up --build -d
```
Y acceder directamente en tu navegador a 👉 **`http://localhost:5173`**.

---

## 🖥️ 3. Guía Detallada de la Interfaz (Sección por Sección)

### 3.1. Barra Superior de Navegación (`Header`)
Ubicada en la parte superior fija de la pantalla:
* **Filtro por Usuario (`Filtrar por userId...`):**
  * Escribe el nombre de cualquier usuario (ej. `demo-user`, `alice`, `emilio`).
  * Al instante, todas las tarjetas de métricas, el panel de presupuesto, la gráfica de distribución y la tabla de historial se filtrarán para mostrar únicamente la actividad de ese usuario.
  * Pulsa el botón **`X`** dentro del buscador para limpiar el filtro y volver a la vista global.
* **Conmutador de Divisa (`EUR €` | `USD $`):**
  * **`EUR €` (Seleccionado por defecto):** Muestra todos los costes y presupuestos convertidos a Euros (`1 USD = 0.92 EUR`) con formato europeo de 6 decimales (`0,506000 €`).
  * **`USD $`:** Muestra los importes en Dólares estadounidenses según la tarificación nativa de los proveedores LLM (`$0.550000`).
* **Conmutador de Tema (`Claro` ☀️ / `Oscuro` 🌙):**
  * Permite alternar en tiempo real entre la estética oscura estilo Vercel (*Dark Mode*) y el tema claro de alto contraste (*Light Mode*), recordando tu preferencia en el navegador.
* **Botón `Sincronizar`:** Refresca manualmente todas las métricas, el presupuesto y la tabla desde el servidor.

---

### 3.2. Cuadrícula Superior de Métricas Clave (4 Tarjetas KPI)
1. **Total de Peticiones:** Muestra el volumen total de consultas procesadas y cuántas fueron asignadas a `Flash` frente a `Pro`.
2. **Coste Acumulado:** Muestra el gasto real facturado en la divisa activa (`€` o `$`) y lo compara justo debajo con la **Base Pro** (lo que habrías pagado sin AI Cost Router).
3. **Tokens Usados:** Suma total de tokens estimados analizados y su proporción por modelo.
4. **Ahorro Generado:**
   * Muestra el dinero total ahorrado (en `€` o `$`) y un badge verde con el **porcentaje de ahorro (`%`)**.
   * En la esquina inferior derecha muestra el contador **`Caché Hit (0 €): X`** indicando cuántas peticiones salieron gratis gracias a la caché semántica.

---

### 3.3. Paneles de Gobierno SaaS: Presupuesto (`BudgetControlCard`), Claves API (`ApiKeyManagerCard`) y Billetera Prepago (`WalletBillingCard`)
Ubicados justo debajo de las 4 tarjetas principales en una cuadrícula de tres columnas:

#### A) Cortafuegos Presupuestario (`BudgetControlCard`)
Permite gobernar el techo de gasto del usuario activo:
* **Indicador de Estado (Arriba a la derecha):**
  * ⚪ **`Sin límite (Ilimitado)`:** El usuario puede enviar peticiones sin tope presupuestario.
  * 🟢 **`Presupuesto Activo (OK)`:** El consumo está por debajo del 80% del límite.
  * 🟡 **`Modo Ahorro Forzado (>=80% → Flash)`:** El usuario ha consumido entre el 80% y el 99% de su presupuesto; el router fuerza el modelo económico `gemini-2.5-flash`.
  * 🔴 **`Cortafuegos Activo (100% Bloqueado)`:** Presupuesto agotado; el router rechaza nuevas peticiones con coste (`HTTP 402`).
* **Barra de Progreso FinOps (`0% — 80% — 100%`):** Representación visual del porcentaje consumido, con una marca vertical en el umbral del 80%.
* **Cómo configurar o quitar un límite:**
  1. Escribe un importe en el campo **`Límite máximo`** (en `€` o `$` según tengas seleccionado arriba) o pulsa uno de los botones rápidos: **`Micro (0,000020 €)`** (ideal para pruebas rápidas) o **`Estándar (1,00 €)`**.
  2. Marca o desmarca la casilla **`Degradar a Flash al 80%`** según quieras activar el modo ahorro preventivo.
  3. Pulsa **`Guardar límite`** (o **`Quitar límite`** para volver al estado ilimitado).

#### B) Gestor de Claves API SHA-256 (`ApiKeyManagerCard`)
Permite emitir y revocar credenciales propias del Gateway (`air_live_...`) asociadas al usuario activo:
1. **Generar una nueva clave:** Escribe un nombre identificativo (ej. `"Producción App Móvil"`) y pulsa **`Generar API Key`**.
2. **Banner de Visualización Única:** Aparecerá un recuadro verde mostrando tu clave completa (`air_live_...`). Por seguridad (igual que en OpenAI, Stripe o GitHub), **el secreto completo solo se muestra una vez** porque en la base de datos únicamente se guarda su hash criptográfico **SHA-256**.
   * Pulsa **`Copiar clave`** para guardarla en el portapapeles o pulsa **`Usar en Simulador`** para inyectarla directamente en el campo `X-API-Key` de la consola inferior.
3. **Revocar una clave:** En la lista inferior verás todas las claves del usuario con su prefijo enmascarado (`air_live_9f8e...`) y su estado (**`Activa`** o **`Revocada`**). Si pulsas **`Revocar`**, la clave quedará invalidada al instante y cualquier intento de uso devolverá **HTTP `401 Unauthorized`**.

#### C) Billetera Prepago y Pasarela de Pagos (`WalletBillingCard`)
Permite recargar paquetes de créditos prepago mediante **Tarjeta (Stripe)** o **PayPal** y ver cómo se descuentan los micro-céntimos en tiempo real con cada petición:
* **Estados de la Billetera:**
  * ⚪ **`Modo Pospago (Sin prepago)`:** Estado inicial por defecto antes de hacer la primera recarga.
  * 🟢 **`Prepago Activo (Con Saldo)`:** Se activa al realizar cualquier recarga; cada consulta descuenta su coste exacto del **Saldo Disponible** (excepto los *Caché Hits*, que son gratuitos).
  * 🔴 **`Saldo Agotado (0,000000 €)`:** Cuando el saldo prepago llega a cero, nuevas consultas con coste se bloquean con **HTTP `402 Payment Required`** hasta que recargues créditos.
* **Cómo recargar créditos:**
  1. Elige el método de pago (**`Tarjeta (Stripe)`** o **`PayPal`**).
  2. Pulsa uno de los botones rápidos de recarga (**`Micro (+0,000050 €)`**, **`+ 5,00 €`** o **`+ 20,00 €`**) o introduce un importe personalizado y pulsa **`Recargar Créditos`**.

---

### 3.4. Consola de Enrutamiento (`PromptPlayground`)
Ubicada en la columna izquierda central, permite enviar consultas y observar la decisión del Router en vivo:
1. **Botones de Plantilla Rápida:**
   * **`Prompt Simple (Flash)`:** Carga una consulta breve para probar el enrutamiento económico.
   * **`Prompt Complejo (Pro)`:** Carga una consulta técnica con palabras clave (`arquitectura`, `optimizar`, `refactorizar`) para probar el enrutamiento avanzado.
2. **Campo `Identificador de Usuario (userId)`:** Define qué usuario firma la petición.
3. **Campo `Cabecera X-API-Key (Modo Híbrido BYOK)`:**
   * **Si lo dejas vacío:** El sistema funciona en **Modo Simulación** sin coste ni necesidad de claves externas.
   * **Si introduces una clave de Google AI Studio (`AIzaSy...`):** El Backend invoca en tiempo real la API oficial de Google Gemini con tu clave.
   * Además, el sistema de seguridad aplica la cuota de **20 peticiones/minuto** de forma independiente por cada `X-API-Key`.
4. **Área de Texto (`Contenido del Prompt`) y Botón `Ejecutar enrutamiento`:** Envía la consulta al Backend.
5. **Tarjeta de Resultado de la Decisión:**
   * Muestra el badge del **Modelo Asignado** (`gemini-2.5-flash` o `gemini-2.5-pro`).
   * Muestra el badge del **Modo de Ejecución**: **`Modo Simulación`** o **`Gemini API Real`**.
   * Si la consulta ya se había hecho antes, muestra el badge cian **`Caché Hit (0 €)`**.
   * Muestra los **tokens estimados** y el texto de la respuesta generada.

---

### 3.5. Gráfica de Distribución de Consumo (`ModelDistributionChart`)
Ubicada en la columna derecha central:
* Muestra mediante barras proporcionales el porcentaje de peticiones y tokens absorbidos por `gemini-2.5-flash` frente a `gemini-2.5-pro`.
* Incluye una gráfica comparativa inferior para auditar visualmente la eficiencia de desvío de tráfico.

---

### 3.6. Tabla de Registro de Peticiones Recientes (`RecentLogsTable`), Exportación CSV y Visor de Detalle (`LogInspectorDrawer`)
Ubicada en la parte inferior del Dashboard:
* Muestra en orden cronológico inverso todas las peticiones con su **Fecha/Hora**, **Usuario**, **Extracto del Prompt**, **Modelo Enrutado** (incluyendo el badge **`Caché Hit (0 €)`** cuando aplica), **Tokens** y un botón de acción **`Ver detalle`**.
* **Exportación Contable a CSV (`Exportar CSV`):** En la esquina superior derecha de la tabla dispones del botón **`Exportar CSV`**, que descarga al instante un archivo `ai-cost-router-logs.csv` compatible con Microsoft Excel y LibreOffice Calc con los costes y ahorros individuales en la divisa activa (`EUR` o `USD`).
* **Visor Modal de Detalle (`LogInspectorDrawer`):**
  * Al hacer clic en cualquier fila de la tabla o en su botón **`Ver detalle`**, se despliega un panel lateral modal que muestra:
    1. **Metadatos y Finanzas Individuales:** Identificador único (`UUID`), usuario, fecha exacta, modelo asignado, tokens estimados, **Coste Individual Facturado** y **Ahorro Individual Generado** en esa única llamada.
    2. **Prompt Íntegro y Respuesta Completa:** Texto completo sin recortar tanto de la consulta enviada como de la respuesta generada por Gemini o el simulador, cada uno con su botón **`Copiar prompt`** / **`Copiar respuesta`** al portapapeles.
  * Puedes cerrar el visor pulsando el botón **`Cerrar`**, haciendo clic fuera del panel o pulsando la tecla `Esc`.
* Si aún no hay datos, muestra un **Estado Vacío** con el botón **`Probar un prompt de ejemplo`** que rellena automáticamente el simulador.

---

## 🛡️ 4. Guía de Mensajes de Alerta y Seguridad

| Código / Alerta | Cuándo ocurre | Qué debes hacer |
| :--- | :--- | :--- |
| **`HTTP 400 Bad Request`** | El campo `userId` o el texto del `prompt` están vacíos o superan el límite permitido. | Completa ambos campos antes de pulsar *Ejecutar enrutamiento*. |
| **`HTTP 401 Unauthorized`** *(Clave API Inválida o Revocada)* | Has enviado en `X-API-Key` una clave gestionada (`air_live_...`) que no existe o ha sido revocada en el gestor de claves. | Genera una nueva clave activa en el panel *Gestor de Claves API* o limpia el campo `X-API-Key`. |
| **`HTTP 402 Payment Required`** *(Cortafuegos Presupuestario)* | El usuario ha alcanzado el **100%** del presupuesto configurado en el panel FinOps. | Aumenta el límite en la tarjeta *Cortafuegos Presupuestario* o pulsa **`Quitar límite`**. |
| **`HTTP 429 Too Many Requests`** *(Rate Limiting)* | Has enviado más de **20 peticiones en menos de 60 segundos** desde la misma IP o `X-API-Key`. | Espera el número de segundos indicado en el contador **`Retry-After`** de la alerta o cambia de `X-API-Key`. |
