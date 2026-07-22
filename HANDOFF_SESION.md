# HANDOFF — Proyecto GroqApp (estado completo al 2026-07-18)

> Pega este documento como primer mensaje en la NUEVA sesión de Claude Code.
> Fecha de corte: 2026-07-18 (final de sesión). Idioma: responde SIEMPRE en español.
> Último commit en main: `60a984f` (el fix de `EmptyTextToolbar`). La rama está limpia salvo este archivo (untracked).

---

## 0. RESUMEN RÁPIDO DE ESTA SESIÓN

Se completó el **§7.1 del handoff anterior**: reemplazar los badges de letra por **logos oficiales reales** de modelos y proveedores, y se corrigieron 3 cosas pedidas por el usuario (logos visibles en claro/oscuro, selección de modelo solo-selección, logos de modelo en todos los selectores).

Commits de la sesión (nuevos → viejos):

| Commit | Qué hizo | Estado CI |
|--------|----------|-----------|
| `60a984f` | Fix: quitar `EmptyTextToolbar` (no existe en esta versión de Compose) | **pendiente de confirmar** (empujado, sin log pegado aún) |
| `c668df5` | Fix: logos con `ColorFilter.tint(onSurface)` + selector de modelo en ajustes a `readOnly` + logos de modelo en ese selector | **FALLÓ** (símbolos inexistentes) → arreglado en `60a984f` |
| `5339760` | Feat: logos de modelo Y proveedor como **drawables PNG locales** (offline) en vez de URLs remotas | empullido (CI no se pegó, pero el usuario continuó dando correcciones) |
| `e8fe249` | Feat: logos de proveedor vía URLs remotas Coil (luego superado por `5339760`) | empullido |
| `3fb875e` | Fix: usar `SubcomposeAsyncImage` (el `AsyncImage.placeholder` original no compilaba) | ✅ compiló (usuario dijo "ya se compilo") |
| `8659276` | Feat: logos oficiales reales de modelo (PNG remotos) en el desplegable | empullido |

**Punto de atención para la próxima IA:** el último commit con CI confirmada fue `3fb875e`. `e8fe249`, `5339760`, `c668df5` y `60a984f` se empujaron pero sus logs de CI no se pegaron explícitamente; `c668df5` SÍ se confirmó que fallaba y se arregló en `60a984f`. **Lo primero es pegar el log del CI de `60a984f` y confirmar que compila.** Si falla, arreglar.

---

## 1. QUÉ ES ESTE PROYECTO / QUÉ HACE

**GroqApp** es una app Android nativa (Jetpack Compose) que actúa como **cliente de chat de IA multi-proveedor**. El usuario introduce su propia clave API y conversa con modelos de lenguaje (Groq, OpenAI, OpenRouter, Together, Ollama). Funciones principales:
- Chat con LLM vía API compatible con OpenAI (`/chat/completions`).
- Historial de chats persistente (Room/SQLite).
- Login con Google (Credential Manager + Firebase Auth) y GitHub (Firebase Auth). Sin login se usa un usuario local anónimo.
- Sandbox de Python en WebView (Pyodide).
- Personalización: color primario del tema + imagen de fondo de pantalla + opacidad.
- Feedback de usuarios a Firestore.
- `applicationId = "com.aistudio.groqapp"`, package base de código = `com.example`.

---

## 2. ENTORNO, LIMITACIONES Y CÓMO TRABAJAR

### 2.1 Quién es el usuario y cómo trabaja
- Usuario hispanohablante. **Responde siempre en español.**
- Entorno: **Termux en Android (aarch64/ARM)**.
- El usuario **NO compila en el PC**: la compilación real ocurre en **GitHub Actions (CI)**. Él pega la salida del workflow y tú diagnosticas/corriges.
- **NO hay `adb` ni `logcat`**: el `adb` del SDK es x86_64 (no corre en ARM), Termux corre como root (bloquea `pkg`), y el dispositivo es el propio móvil. Para crashes de runtime, la app escribe el stack trace en `Descargas/groqapp_crash.txt` (`GroqApplication.kt`).

### 2.2 Reglas de trabajo (el usuario las exige — RESPÉTALAS)
1. **Un solo `git push` por cambio.** Nunca encadenes dos pushes seguidos.
2. Idealmente **espera el resultado del CI** antes de apilar el siguiente cambio.
3. **Sé honesto.** No afirmes que algo está arreglado si no lo verificaste. Distingue SIEMPRE "bug de código" (lo arreglas) de "problema de configuración/entorno" (Firebase/red/región — lo explicas, no lo arreglas).
4. **Aísla los cambios**: un feature por commit/push.
5. El usuario es **novato** en Firebase/consolas: pasos concretos, copiables, con URLs exactas.
6. Ante la duda, **aísla la causa con un cambio mínimo y verificable** antes de adivinar.
7. **No inventes URLs ni dependencias Maven**: verifícalas con `curl -s -o /dev/null -w "%{http_code}\n" <url>` antes de usarlas.

### 2.3 Limitación crítica: no se puede compilar en Termux
`./gradlew assemble*` **falla SIEMPRE localmente** en `:app:kspReleaseKotlin` (Room/KSP):
`UnsatisfiedLinkError: libsqlitejdbc.so: dlopen failed: library "libm.so.6" not found` — del entorno (glibc vs Bionic), NO del proyecto. **Verificación = SOLO GitHub Actions.** Flujo: editar → comprobaciones estáticas (grep, balance de llaves/paréntesis, TOML) → commit → push → esperar log del CI que pega el usuario.

### 2.4 Rutas y entorno
- Repo: `/data/data/com.termux/files/home/repo-github`. Rama `main` → `origin = https://github.com/manuscooth01/GroqApp.git`.
- SDK: `/data/data/com.termux/files/home/android-sdk`. NO hay `ANDROID_HOME`/`local.properties`. Para Gradle:
  ```bash
  export ANDROID_HOME=/data/data/com.termux/files/home/android-sdk
  export ANDROID_SDK_ROOT=/data/data/com.termux/files/home/android-sdk
  ```
- **GitHub Secrets (Settings → Secrets and variables → Actions):** `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD=GroqAppKey2026`, `KEY_ALIAS=groqapp`, `KEY_PASSWORD=GroqAppKey2026`. `app/release.keystore` está en `.gitignore`.
  - ⚠️ **Los valores de los Secrets NO se ven en la UI de GitHub** (aparecen `••••••`). Eso confundió al usuario ("están vacíos"); en realidad SÍ están configurados. No se puede copiar el valor del secreto.

---

## 3. STACK / VERSIONES

- AGP 9.1.1, Gradle 9.3.1, **Kotlin 2.2.10** (Compose strong skipping activo).
- `plugins` de `app/build.gradle.kts`: `com.android.application`, `org.jetbrains.kotlin.plugin.compose`, `com.google.devtools.ksp`, `com.google.gms.google-services`. NO lleva `org.jetbrains.kotlin.android` (AGP 9 lo integra).
- `compileSdk = 36`, `minSdk = 29` (dentro de `defaultConfig`), `targetSdk = 35`.
- Compose BOM 2024.09.00. Room (KSP). Retrofit + Moshi. Coil 2.7.0. Firebase BoM 34.15.0 (Auth + Firestore). Credential Manager + googleid. androidx.security.crypto (DEPRECADO). Pyodide en WebView.
- Release corre R8 (`isMinifyEnabled=true`, `isShrinkResources=true`). Debug no.
- **Firma release:** el workflow de CI decodifica `KEYSTORE_BASE64` → `app/release.keystore` y firma con ese keystore.

### Archivos clave
- `app/src/main/java/com/example/MainActivity.kt` (~3480 líneas, TODA la UI Compose). Contiene `ModelBadge`, `ProviderBadge`, `LetterBadge`, `modelLogoRes()`, `providerLogoRes()`, `modelBrand()`.
- `app/src/main/java/com/example/ui/ZaiViewModel.kt` (~1320 líneas, lógica + auth + repos + personalización + feedback).
- `app/src/main/java/com/example/GroqApplication.kt` (Application + capturador de crashes a `Descargas/groqapp_crash.txt`).
- `app/src/main/java/com/example/data/AppRepository.kt` (Retrofit + Moshi + Room).
- `app/src/main/java/com/example/data/api/` — `GroqModels.kt`, `GroqService.kt`.
- `app/src/main/java/com/example/ui/theme/Theme.kt` (`MyApplicationTheme`, aplica `primaryColor`).
- `app/proguard-rules.pro`.
- `app/google-services.json` (cliente Android con SHA-1 `bacecf34…`, web client `default_web_client_id`).
- `.github/workflows/build.yml` (CI: assembleRelease + firma + artefacto `app-release` + paso que imprime SHA-1).
- `app/src/main/res/drawable/ic_*.xml` — logos de marca (google, github, drive, slack, notion, trello, apple).
- **`app/src/main/res/drawable/ic_brand_*.png`** y **`ic_provider_*.png`** — logos de modelo y proveedor (AGREGADOS esta sesión, véase §5.1).
- `app/src/main/java/com/example/ui/Logos.kt` — objeto `Logos` con constantes de URLs base (GOOGLE, GITHUB, DRIVE, SLACK, NOTION, TRELLO). **Actualmente NO usadas por los badges** (estos usan drawables locales); podrían reusarse en otras pantallas o eliminarse.

---

## 4. LOGROS Y ESTADO ACTUAL (verificado / terminado y empujado)

- ✅ **Logos oficiales reales de modelos y proveedores** (commits `8659276`→`60a984f`). Ver detalle en §5.1.
- ✅ **Login Google funciona** (Credential Manager + Firebase). El fix real del "doble toque" fue reintentar `getCredential` hasta 3 veces (commit de sesión previa `a46124d`).
- ✅ **SHA-1 de Firebase confirmada correcta** vía paso en CI (`bacecf34…` coincide con la del keystore de firma).
- ✅ **Selección de modelo: dropdown de selección** (`readOnly`), no campo editable (commit `a86da2e` de sesión previa; esta sesión dejó `readOnly` también en el selector de ajustes).
- ✅ **APK release se firma vía GitHub Secrets** (instalable).
- ✅ **Fix Retrofit/Moshi** (commit `ce05291`, sesión previa).
- ✅ **Imagen de fondo de personalización** (commit `81a2fc8` + `068e5d9`, sesión previa). Pendiente de verificación en runtime por el usuario.
- ✅ **Feedback a Firestore** (empaquetado en `068e5d9`, sesión previa). Pendiente de verificación en runtime.
- ✅ **Fix 403 Groq** (interceptor User-Agent browser-like en `AppRepository.kt`, commit `0c3e384`, sesión previa). Pendiente de verificación por el usuario.

---

## 5. CONTEXTO TÉCNICO RELEVANTE

### 5.1 Logos de modelo y proveedor (LO NUEVO de esta sesión) — IMPORTANTE
**Estado final:** los logos son **drawables PNG locales** en `app/src/main/res/drawable/` (offline, sin red). Esta fue la solución definitiva tras descartar URLs remotas.

- **Modelos** (`ic_brand_*`): `ic_brand_meta`, `ic_brand_google`, `ic_brand_deepseek`, `ic_brand_openai`, `ic_brand_mistral`, `ic_brand_qwen`, `ic_brand_microsoft`.
- **Proveedores** (`ic_provider_*`): `ic_provider_groq`, `ic_provider_ollama`, `ic_provider_openrouter`, `ic_provider_together`; OpenAI reusa `ic_brand_openai`.

Renderizado en `MainActivity.kt`:
- `modelLogoRes(id): Int?` → devuelve `R.drawable.ic_brand_*` por marca (llama→Meta, gemma/gemini→Google, deepseek, gpt/openai, mistral/mixtral, qwen, phi→Microsoft; `else → null`).
- `providerLogoRes(p): Int?` → `R.drawable.ic_provider_*` por proveedor.
- `ModelBadge` / `ProviderBadge` renderizan con `androidx.compose.foundation.Image(painter = painterResource(res), ..., colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))`. El tint hace el logo **visible en ambos temas** (oscuro en claro, claro en oscuro) — req. del usuario: "no negros en oscuro, no blancos en claro".
- Fallback `LetterBadge(bg, letter)` (círculo de color + inicial) solo para ids no mapeados.
- Se muestran en: campo `leadingIcon` + cada ítem del desplegable, en **los dos selectores de modelo** (onboarding paso 3 y pantalla de ajustes) y **los dos selectores de proveedor** (onboarding y ajustes).

**Por qué locales y no remotos:** primero se intentaron URLs PNG remotas (Wikimedia + CDN de Together) vía Coil. En el dispositivo del usuario algunos proveedores caían en la bola gris de fallback (la imagen remota no cargaba fiable, sobre todo la de Together en `cdn.prod.website-files.com`). Se descargaron los PNG verificados (HTTP 200, firma PNG válida vía `curl`) y se embebieron como drawables → siempre se ven. **No volver a URLs remotas para estos badges.**

### 5.2 Selección de modelo / proveedor
- Ambos selectores (onboarding y ajustes) usan `ExposedDropdownMenuBox` + `OutlinedTextField(readOnly = true)` + `menuAnchor()`. El usuario **no puede escribir, solo elegir del desplegable**.
- El selector de modelo en ajustes **era editable** (permitía escribir/filtrar) y se cambió a `readOnly` en esta sesión (`c668df5`).
- Proveedores: `viewModel.providers.keys.forEach { p -> ... }`. Modelos: `viewModel.getFallbackModels(provider)` (listas estáticas por proveedor).
- Valor guardado del modelo = el ID real (p.ej. `llama-3.3-70b-versatile`), que es lo que se manda a la API (`viewModel.saveSelectedModel(model)`).

### 5.3 Login Google (Credential Manager)
- Flujo en `ZaiViewModel.signInWithGoogle`: `CredentialManager.getCredential(request)` → `GetGoogleIdOption` (`setFilterByAuthorizedAccounts(false)`, `setServerClientId(R.string.default_web_client_id)`) → `GoogleIdTokenCredential` → `FirebaseAuth.signInWithCredential` → `completeLogin(email)`.
- La PRIMERA llamada a `getCredential` a veces falla con `NoCredentialException` (cold-start del proveedor) y acierta en el reintento. Por eso el reintento (hasta 3) es el arreglo del doble toque. NO es problema de foco/clic.
- El mensaje de error muestra el detalle real de Google.

### 5.4 Firebase SHA-1
- La huella registrada en Firebase (`bacecf34…`) DEBE coincidir con la SHA-1 del keystore que FIRMA el APK instalado (el de `KEYSTORE_BASE64`). Verificado por CI que coincide.
- Si falla el login Google con `NoCredentialException` y la cuenta existe, revisar que la SHA-1 del keystore de firma esté en Firebase console (Project settings → SHA certificate fingerprints).

### 5.5 Lista de proveedores y modelos por defecto
- providers (`ZaiViewModel.kt:203`): Groq, OpenAI, Ollama, OpenRouter, Together (mapa `providers` con sus base URLs).
- defaultModels: Groq→`llama-3.1-8b-instant`, OpenAI→`gpt-4o`, Ollama→`llama3`, OpenRouter→`meta-llama/llama-3.1-8b-instruct`, Together→`meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo`.
- Listas de modelo por proveedor: `groqModels`, `openAiModels`, `togetherModels`, `openRouterModels`, `ollamaModels` (todas NO vacías).

---

## 6. ERRORES Y PROBLEMAS CONOCIDOS / GOTCHAS

### 6.1 Errores cometidos y corregidos esta sesión (para no repetir)
- ❌ **`AsyncImage.placeholder`/`error` esperan `Painter?`, no `@Composable () -> Unit`.** El slot componible es de `SubcomposeAsyncImage` (`loading`/`error`). Nos pegó en `8659276` → fix `3fb875e`. Al final terminamos usando `Image(painterResource(...))` local, así que ni SubcomposeAsyncImage se necesita.
- ❌ **`EmptyTextToolbar` / `LocalTextToolbar` NO EXISTEN en esta versión de Compose del proyecto.** Referencia no resuelta en `c668df5` → fix `60a984f` (se eliminaron). Para desactivar la selección de texto usa `readOnly = true` (permite long-press copy) o, si se quiere 0% selección, reemplaza el `TextField` por un `Text` no editable anclado con `menuAnchor()`. **No vuelvas a usar `EmptyTextToolbar`.**

### 6.2 GOTCHAS de Compose/Coil de este proyecto
- **Coil 2.7.0 NO decodifica SVG** (haría falta `coil-svg`). Usar siempre PNG.
- **Los logos de badge deben ser PNG locales** (`res/drawable/ic_*.png`), no URLs remotas, porque en el dispositivo del usuario las remotas fallan (CDN/red) y caen en el fallback gris.
- **Logos temáticos:** usar `ColorFilter.tint(MaterialTheme.colorScheme.onSurface)` para que se vean en claro y oscuro (patrón ya usado en la app para íconos de GitHub, etc.).
- `readOnly = true` en un `OutlinedTextField` ya impide escribir; el teclado no aparece.

### 6.3 Limitaciones de entorno (NO son bugs del proyecto)
- `./gradlew` falla local en KSP (glibc vs Bionic). Solo CI compila.
- `adb` del SDK es x86, no corre en Termux/ARM. No hay `logcat`. Runtime crashes → `Descargas/groqapp_crash.txt`.
- `pkg` no corre como root. No se puede instalar `adb` ARM.

### 6.4 Advertencias de deprecación (bajo riesgo, pendientes)
- `AutoMirrored` icons, `menuAnchor`, `androidx.security.crypto` — warnings de deprecación, no bloquean.

---

## 7. QUÉ FALTA POR HACER

### 7.1 Inmediato
1. **Pegar el log del CI de `60a984f` y confirmar que compila.** Si falla, arreglar (casi seguro es una tontería de API; revisar primero los GOTCHAS de §6).
2. Construir/instalar la APK y que el usuario verifique en el dispositivo que: los logos de modelo y proveedor se ven (no bola gris), son visibles en claro y oscuro, y el selector de modelo no permite escribir.

### 7.2 Verificación en runtime por el usuario (pendiente de confirmación desde sesiones previas)
- Imagen de fondo de personalización (¿persiste tras reiniciar?).
- Feedback a Firestore (¿llegó el doc a la colección `feedback`?).
- Fix 403 Groq (¿sigue el 403 al "probar conexión"?).
- Login GitHub (nunca se probó en runtime; usa `pendingAuthResult` + OAuthProvider de Firebase).

### 7.3 Otros posibles pendientes
- **Selección de texto 100% desactivada en el modelo:** con `readOnly` aún se puede hacer long-press y copiar. Si el usuario lo exige, cambiar el campo de modelo por un `Text` no editable + `menuAnchor()` (ver §6.1).
- Revisar warnings de deprecación (`AutoMirrored`, `menuAnchor`, `security.crypto`).
- El objeto `Logos.kt` tiene constantes de URL base no usadas por los badges; decidir si reusarlas en otra pantalla o eliminarlas.

---

## 8. LO QUE DEBERÍA FALTAR / CAVEATS (honestidad)

- **Nada de lo anterior está verificado en runtime por mí** (no puedo compilar ni ejecutar). Lo verificado es: (a) balance de llaves/paréntesis 0/0 en los .kt editados, (b) el CI compiló hasta `3fb875e` y el usuario lo confirmó; (c) los PNG de drawable se descargaron con firma válida. Lo demás (imagen de fondo, feedback, 403, GitHub login) está empujado pero el usuario aún no lo ha confirmado en su dispositivo.
- **No asumas que el 403 está resuelto** solo porque hay un commit de fix; depende de la cuenta/clave/red de Groq del usuario.
- **El `canFocus=false` (de sesión previa, commit `068e5d9`) es código muerto útil** (hipótesis incorrecta del doble toque). No lo borres sin avisar; es inofensivo pero reduce a11y.
- **Las comprobaciones estáticas NO detectan errores de tipos/API de Compose** (nos pegó 2 veces: `AsyncImage.placeholder` y `EmptyTextToolbar`). El CI es la única verificación real.
- **No inventes URLs ni dependencias Maven**: verifícalas con `curl` antes de usarlas.

---

## 9. CÓMO CONTINUAR (guion)

1. Pedir al usuario el log del CI de `60a984f`. Si compila ✅ → paso 2. Si falla ❌ → aislar el error (revisar §6) y un commit de fix aislado + push.
2. Una vez compilando, pedir al usuario que instale la APK y confirme visualmente logos de modelo/proveedor (claros y oscuros) y que el modelo no se puede escribir.
3. Si el usuario reporta un nuevo bug, aislar con cambio mínimo y verificable.
4. Exporta `ANDROID_HOME` si corres Gradle; NO esperes poder compilar (KSP/glibc falla).
5. **Un cambio → un commit → un push → esperar CI.**

---

## 10. HISTORIAL DE COMMITS (del más nuevo al más viejo, relevantes)

| Commit | Qué hizo |
|--------|----------|
| `60a984f` | Fix: quitar `EmptyTextToolbar` (no existe en esta versión de Compose) |
| `c668df5` | Fix: logos con tint `onSurface` + modelo en ajustes `readOnly` + logos de modelo en ese selector (FALLÓ por `EmptyTextToolbar`) |
| `5339760` | Feat: logos de modelo y proveedor como drawables PNG locales (offline) |
| `e8fe249` | Feat: logos de proveedor vía URLs remotas Coil (superado por `5339760`) |
| `3fb875e` | Fix: usar `SubcomposeAsyncImage` para el fallback del badge de modelo |
| `8659276` | Feat: logos oficiales reales de modelo (PNG) en el desplegable |
| `75bd548` | Feat: badge de marca por modelo en el desplegable de selección |
| `a86da2e` | Fix: selección de modelo en modo lista (no escritura) en onboarding |
| `a46124d` | Fix: reintentar `getCredential` de Google (primer toque fallaba) — fix real del doble toque |
| `0f6107a` | Mejorar mensaje de error de login Google (`NoCredentialException`) |
| `36674c6` | CI: imprimir SHA-1 de firma para diagnosticar login Google |
| `068e5d9` | "Fix doble toque" (`canFocus=false`, hipótesis equivocada) + WIP imagen fondo + feedback |
| `0c3e384` | Fix: interceptor User-Agent browser-like para evitar 403 de Cloudflare/Groq |
| `81a2fc8` | Feat: imagen de fondo persistente en personalización |
| `ce05291` | Fix Retrofit/Moshi: reglas R8 para adapters + revertir dep inexistente |

---

## 11. LECCIONES DE ESTA SESIÓN

- **Las comprobaciones estáticas (balance de llaves) NO detectan errores de API/tipos de Compose.** Nos pegó 2 veces (`AsyncImage.placeholder` espera `Painter?`; `EmptyTextToolbar` inexistente). El CI es la única verificación real; no afirmes "compila" hasta ver el log.
- **Verifica la existencia de símbolos de Compose antes de usarlos.** Si no estás 100% seguro de que un símbolo existe en la versión del proyecto, busca su uso en el codebase o evítalo.
- **Lo remoto falla en el dispositivo del usuario:** para assets que deben verse siempre, usa **drawables locales** (offline), no URLs de CDN.
- **Logos temáticos:** `ColorFilter.tint(onSurface)` hace que cualquier logo sea visible en claro y oscuro sin mantener dos versiones.
- **Aísla y verifica en CI**: cada arreglo en su commit, y el usuario confirma en el dispositivo.
- **Sé honesto sobre qué está y qué no verificado** (runtime vs compilación).

FIN DEL HANDOFF.
