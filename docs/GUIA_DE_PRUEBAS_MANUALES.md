# 🧪 Guía de Pruebas Manuales Paso a Paso — AI Cost Router

Este documento es tu **lista de verificación interactiva (*Checklist*)** para revisar personalmente todas las funcionalidades de **AI Cost Router** de principio a fin.

---

## 🚀 Paso 0: Arrancar la Aplicación

Abre **dos terminales** en la carpeta raíz del proyecto (`c:\Users\Emilio\Desktop\PERSONAL\Pruebas\airouter\airouter`):

### Terminal 1 — Backend (Puerto `:8080`)
```powershell
cd airouter
.\mvnw.cmd spring-boot:run
```
*(Espera a ver el mensaje `Started AirouterApplication`).*

### Terminal 2 — Frontend (Puerto `:5173`)
```powershell
cd frontend
npm run dev
```

Abre tu navegador en: 👉 **`http://localhost:5173`**

---

## ✅ Checklist de Pruebas Funcionales

### 1. Estado Vacío Interactivo y Enrutamiento Económico (`gemini-2.5-flash`)
- [ ] **1.1.** Nada más abrir el Dashboard (con la base de datos limpia), baja hasta la tabla **"Registro de Peticiones Recientes"**.
- [ ] **1.2.** Comprueba que aparece el **Estado Vacío** con un icono y el botón **`Probar un prompt de ejemplo`**.
- [ ] **1.3.** Haz clic en **`Probar un prompt de ejemplo`**: verifica que la pantalla sube suavemente al simulador y rellena automáticamente una consulta corta.
- [ ] **1.4.** Pulsa el botón **`Ejecutar enrutamiento`**.
- [ ] **1.5. Resultado esperado:**
  * Aparece la tarjeta inferior **"Resultado de la decisión"** con el badge verde **`gemini-2.5-flash`** y el badge **`Modo Simulación`**.
  * Las 4 tarjetas superiores se actualizan al instante mostrando **1 petición** y un **88.0% de ahorro generado**.

---

### 2. Enrutamiento Avanzado por Complejidad (`gemini-2.5-pro`)
- [ ] **2.1.** En la consola **"Simulador de Enrutamiento"**, haz clic en el botón de plantilla **`Prompt Complejo (Pro)`** (verás que incluye términos técnicos como *arquitectura*, *refactorizar* y *optimizar*).
- [ ] **2.2.** Pulsa **`Ejecutar enrutamiento`**.
- [ ] **2.3. Resultado esperado:**
  * El Router detecta la alta complejidad técnica y asigna el modelo **`gemini-2.5-pro`** (badge color índigo).
  * La gráfica **"Distribución de Consumo"** (a la derecha) se actualiza mostrando el reparto al 50% entre `Flash` y `Pro`.

---

### 3. Caché Semántica de Coste Cero (`Caché Hit 0 €`)
- [ ] **3.1.** Sin cambiar el prompt anterior (o pulsando otra vez **`Prompt Simple (Flash)`**), vuelve a hacer clic en **`Ejecutar enrutamiento`**.
- [ ] **3.2.** Prueba también a escribir el mismo prompt pero alterando espacios y mayúsculas:
  ```text
     HOLA,   ¿puedes darme   3 ideas breves de nombres para una cafetería de barrio?  
  ```
  y pulsa **`Ejecutar enrutamiento`**.
- [ ] **3.3. Resultado esperado:**
  * En la tarjeta de resultado aparece el nuevo badge cian **`Caché Hit (0 €)`**.
  * En la tarjeta superior **Ahorro Generado**, el contador inferior sube a **`Caché Hit (0 €): 2`**.
  * El **Coste Acumulado** no sube ni un céntimo en esas llamadas repetidas, mientras que el **Ahorro Generado** suma el 100% de lo que habrían costado.
  * En la tabla inferior **"Registro de Peticiones Recientes"**, esas filas muestran la etiqueta **`Caché Hit (0 €)`**.

---

### 4. Conmutador de Divisa en Tiempo Real (`EUR €` ↔ `USD $`)
- [ ] **4.1.** En la esquina superior derecha de la barra de navegación, observa que por defecto está activo **`EUR €`** (importes con coma decimal y símbolo `€`, ej. `0,000018 €`).
- [ ] **4.2.** Haz clic en **`USD $`**.
- [ ] **4.3. Resultado esperado:**
  * Todas las cifras monetarias del Dashboard (Tarjetas KPI y Panel de Cortafuegos Presupuestario) se convierten al instante a Dólares (`$0.000020`, tasa `1 USD = 0.92 EUR`).
- [ ] **4.4.** Vuelve a hacer clic en **`EUR €`** para dejarlo en Euros.

---

### 5. Cortafuegos Presupuestario FinOps (`80% Auto-Downgrade` y `100% Bloqueo HTTP 402`)
- [ ] **5.1.** En el panel **"Cortafuegos Presupuestario (FinOps)"**, pulsa el botón rápido **`Micro (0,000020 €)`** y haz clic en **`Guardar límite`**.
- [ ] **5.2.** Observa cómo cambia el badge superior del panel y la barra de progreso:
  * **Si el consumo está entre el 80% y el 99%:** Verás el aviso ámbar **`Modo Ahorro Forzado (>=80% → Flash)`**. Si en ese estado envías un prompt nuevo con la palabra `"arquitectura"`, comprobarás que el Router lo fuerza a **`gemini-2.5-flash`** en lugar de `Pro` para no agotar tu saldo.
  * **Cuando el consumo alcanza o supera el 100%:** Verás el badge rojo **`Cortafuegos Activo (100% Bloqueado)`**.
- [ ] **5.3.** Con el presupuesto al 100%, intenta enviar cualquier prompt en el simulador.
- [ ] **5.4. Resultado esperado:**
  * El simulador rechaza la petición y muestra la alerta roja: **`Cortafuegos Presupuestario Activado (HTTP 402 Payment Required)`**.
- [ ] **5.5.** Pulsa el botón **`Quitar límite`** en el panel de presupuesto: verifica que vuelve al estado **`Sin límite (Ilimitado)`** y ya te permite volver a enviar prompts.

---

### 6. Conexión Híbrida a Google Gemini Real (`Modo BYOK`)
- [ ] **6.1. (Opcional — si tienes una API Key de Google AI Studio):**
  * Pega tu clave (`AIzaSy...`) en el campo **`Cabecera X-API-Key (Modo Híbrido BYOK)`** del simulador.
  * Escribe un prompt nuevo que no esté en caché (ej. `"Explícame en 2 frases qué es Spring Boot"`) y pulsa **`Ejecutar enrutamiento`**.
- [ ] **6.2. Resultado esperado:**
  * El badge del resultado cambia de `Modo Simulación` a **`Gemini API Real`** (en verde esmeralda) y muestra la respuesta real redactada por Google Gemini.
- [ ] **6.3. Prueba de Resiliencia (Fallback automático):**
  * Escribe una clave inventada en `X-API-Key` (por ejemplo `clave-de-prueba-123`) y envía un prompt nuevo.
  * Comprueba que la aplicación **no se rompe**: al detectar que la clave no es válida en Google, hace fallback transparente al **`Modo Simulación`**.

---

### 7. Filtro Reactivo por Usuario (`userId`)
- [ ] **7.1.** En el simulador, cambia el campo **`Identificador de Usuario (userId)`** de `demo-user` a `emilio` y envía un prompt.
- [ ] **7.2.** Arriba del todo, en el buscador de la barra superior (`Filtrar por userId...`), escribe `emilio`.
- [ ] **7.3. Resultado esperado:**
  * Las tarjetas KPI, el panel de presupuesto, la gráfica y la tabla inferior muestran exclusivamente los datos de `emilio`.
- [ ] **7.4.** Pulsa la **`X`** en el buscador superior para volver a ver todos los usuarios.

---

### 8. Protección Anti-Abuso (`Rate Limiting — HTTP 429`)
- [ ] **8.1.** Pulsa el botón **`Ejecutar enrutamiento`** de forma rápida y seguida **21 veces** (en menos de 1 minuto).
- [ ] **8.2. Resultado esperado:**
  * En la petición número 21 salta el filtro de seguridad y aparece la alerta **`Límite de Peticiones Excedido (HTTP 429)`** indicando el **`Tiempo de espera (Retry-After): X segundos`**.
- [ ] **8.3. Prueba de aislamiento por API Key:**
  * Sin esperar a que pasen los segundos, escribe cualquier texto nuevo en el campo **`Cabecera X-API-Key`** (ej. `usuario-vip-2`) y pulsa **`Ejecutar enrutamiento`**.
  * Comprueba que te deja pasar inmediatamente porque cada `X-API-Key` tiene su propia cuota aislada de 20 peticiones/minuto.

---

### 9. Visor Modal de Detalle (`LogInspectorDrawer`), Exportación CSV y Tema Claro/Oscuro
- [ ] **9.1. Inspeccionar una petición en detalle:**
  * Baja a la tabla **"Registro de Peticiones Recientes"** y haz clic sobre cualquier fila o sobre su botón **`Ver detalle`**.
  * Comprueba que se abre el panel lateral modal mostrando el `UUID`, el **Coste Facturado individual**, el **Ahorro Generado individual**, el **Prompt íntegro** y la **Respuesta completa**.
  * Pulsa **`Copiar prompt`** y **`Copiar respuesta`**: verifica que el icono cambia a un check verde y aparece la confirmación **`Copiado`**.
  * Pulsa `Esc` o haz clic fuera del panel para cerrarlo.
- [ ] **9.2. Exportar historial a CSV:**
  * En la cabecera de la tabla inferior, pulsa el botón **`Exportar CSV`**.
  * Comprueba que el navegador descarga el archivo `ai-cost-router-logs.csv` con todas las filas y costes en la moneda activa (`EUR` o `USD`).
- [ ] **9.3. Conmutador de Tema Claro / Oscuro (`Claro` ☀️ / `Oscuro` 🌙):**
  * En la barra superior derecha (junto al selector `EUR € / USD $`), pulsa el botón **`Claro`** (icono de Sol).
  * Comprueba que toda la interfaz cambia suavemente al tema claro de alto contraste, y al volver a pulsarlo (**`Oscuro`**) regresa a la estética oscura tipo Vercel.

---

### 10. Gestor de Claves API con Hash SHA-256 (`ApiKeyManagerCard` y Bloqueo `HTTP 401`)
- [ ] **10.1. Emitir una nueva API Key:**
  * En el panel **"Gestor de Claves API (SHA-256)"** (situado junto al panel de presupuesto), escribe `"Clave Producción"` en el campo de nombre y pulsa **`Generar API Key`**.
  * Comprueba que aparece el banner verde de **Visualización Única** mostrando tu clave completa (`air_live_...`) y que en la lista inferior solo aparece el prefijo enmascarado (`air_live_xxxx...`) con estado **`Activa`**.
- [ ] **10.2. Probar la clave en el Simulador:**
  * Pulsa el botón **`Usar en Simulador`** dentro del banner verde: verifica que la clave se copia automáticamente al campo `X-API-Key` del simulador inferior.
  * Pulsa **`Ejecutar enrutamiento`** y comprueba que la petición se procesa con éxito (`200 OK`).
- [ ] **10.3. Revocar la clave y verificar bloqueo `HTTP 401 Unauthorized`:**
  * En la lista de claves del panel **"Gestor de Claves API"**, pulsa el botón **`Revocar`** de esa clave: verás que su estado cambia a **`Revocada`**.
  * Con esa misma clave aún puesta en el campo `X-API-Key` del simulador, vuelve a pulsar **`Ejecutar enrutamiento`**.
  * **Resultado esperado:** El Gateway rechaza la petición y muestra la alerta roja **`Clave API Inválida o Revocada (HTTP 401 Unauthorized)`**.
  * Limpia el campo `X-API-Key` para seguir operando normalmente.

---

### 11. Billetera Prepago y Pasarela de Pagos (`WalletBillingCard` — Stripe / PayPal)
- [ ] **11.1. Recargar créditos con Stripe o PayPal:**
  * En el panel **"Billetera Prepago (Billing)"**, selecciona el método **`PayPal`** (o deja **`Tarjeta (Stripe)`**) y pulsa el botón rápido **`Micro (+0,000050 €)`** (o escribe `5` y pulsa **`Recargar Créditos`**).
  * Comprueba que el badge superior cambia a **`Prepago Activo (Con Saldo)`**, el **Saldo Disponible** muestra el importe abonado y el **Último Método de Pago** refleja la pasarela elegida.
- [ ] **11.2. Comprobar descuento de micro-céntimos en tiempo real:**
  * Envía un prompt nuevo en el simulador inferior.
  * Observa cómo el **Saldo Disponible** de la billetera se reduce automáticamente en los micro-céntimos exactos que ha costado esa petición (y comprueba que si envías el mismo prompt repetido con **`Caché Hit (0 €)`**, el saldo de tu billetera no baja).
