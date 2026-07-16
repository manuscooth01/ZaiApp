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
