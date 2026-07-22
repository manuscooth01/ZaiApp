# GroqApp

App Android nativa (Kotlin + Jetpack Compose + Material 3) para chatear con la API de Groq (y otros proveedores compatibles OpenAI).

## Características

- **Chat** — conversación directa con la IA, adjuntos múltiples y dictado
- **Agente** — tareas complejas con pasos de razonamiento colapsables y ejecución automática de Python (Pyodide en WebView)
- **Historial** — menú en cabecera con filtros Chat/Agente y botones de nueva sesión
- **Configuración** — proveedor, URL base, modelo, API Key, prueba de conexión y tema
- **Onboarding** guiado con logo y tutorial
- **Room** para sesiones/mensajes separados por tipo
- **Retrofit + Moshi** para llamadas reales a la API (sin simulaciones)

## Colores

| Token | Hex |
|-------|-----|
| Fondo | `#0D0D0D` |
| Naranja | `#FF5722` |
| Tarjetas | `#18181B` |
| Bordes | `#52525B` |

## Compilación

- Gradle **9.3.1**
- Android Gradle Plugin **9.1.1**
- Kotlin **2.2.10**
- minSdk 24 · targetSdk 36 · compileSdk 36

```bash
./gradlew :app:assembleDebug
```

## Configuración

1. Abre el proyecto en Android Studio
2. Ejecuta en emulador o dispositivo
3. Completa el onboarding e ingresa tu API Key de [console.groq.com/keys](https://console.groq.com/keys)
4. En Configuración (engranaje) puedes cambiar proveedor, modelo y probar la conexión

## Estructura

```
app/src/main/java/com/example/
├── MainActivity.kt          # UI Compose completa
├── data/
│   ├── AppRepository.kt     # Room + API
│   ├── api/                 # Retrofit models & service
│   └── database/            # Entities, DAO, Room DB
└── ui/
    ├── ZaiViewModel.kt
    ├── SandboxWebView.kt    # Pyodide
    └── theme/
```

## Licencia

Proyecto de demostración.

## Sincronización en la nube

La configuración del usuario (API key, fondo de pantalla, tema/color y proveedor/modelo,
además de creatividad, búsqueda web, TTS y razonamiento) se sincroniza por cuenta en
**Firestore** (`users/{uid}`, donde `uid` es el de Firebase Auth) y la imagen de fondo en
**Firebase Storage** (`backgrounds/{uid}.jpg`).

- Al iniciar sesión se restaura la config desde la nube (la nube manda) o se sube la local
  la primera vez.
- Cada cambio de ajuste se empuja a la nube (merge) y hay un listener en tiempo real.
- Al cerrar sesión se limpia la config local (ya vive en la nube), evitando que la siguiente
  cuenta herede la API key o el fondo.
- **Historial de chat**: sesiones y mensajes se espejan en Firestore
  (`users/{uid}/sessions/{cloudId}` y `.../messages/{msgCloudId}`), con un `cloudId` (UUID)
  por fila que mapea 1:1 local↔nube. Se restauran al iniciar sesión y se suben al escribir.
- El modo invitado (sin cuenta) es 100% local y no toca la nube.

**Migración de base de datos**: el esquema de Room subió a la versión 6 (migración `5→6`, no
destructiva) para añadir la columna `cloudId` a `chat_sessions` y `chat_messages`. Al actualizar
desde una versión anterior el historial local se conserva y se sube a la nube con nuevos `cloudId`.

**Requisitos y despliegue (no afectan la compilación del APK):**

1. En Firebase Console: habilitar **Firestore Database** y **Storage** para el proyecto
   (`applicationId = com.aistudio.groqapp`).
2. Desplegar las reglas de seguridad para que solo el dueño acceda a su documento/fondo:
   - `firestore.rules` → `firebase deploy --only firestore:rules`
   - `storage.rules` → `firebase deploy --only storage`
3. Las reglas ya están en la raíz del repo. Sin ellas, cualquiera podría leer/escribir la
   config de otros usuarios.

> Nota de seguridad: la API key se guarda en la nube en texto (protegida por las reglas de
> Auth y el cifrado en reposo de Firebase). No se cifra en el cliente.
