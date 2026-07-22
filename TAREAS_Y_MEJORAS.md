# GroqApp — Cuadro de tareas y mejoras

Actualizado: revisión de autenticación real (Google, GitHub, Email) + seguridad + rendimiento.

## ✅ Ya aplicado en este zip

| Área | Qué se hizo | Archivo(s) |
|---|---|---|
| Compilación | Corregida coordenada Maven corrupta del BOM de Firebase (`build.gradle.kts` rompía el build) | `app/build.gradle.kts` |
| Compilación | Reemplazado `firebase-auth-ktx` (ya no existe) por `libs.firebase.auth` del catálogo | `app/build.gradle.kts` |
| Autenticación | Login real con Google vía Credential Manager + Firebase Auth | `ZaiViewModel.kt`, `MainActivity.kt` |
| Autenticación | Login real con GitHub vía `OAuthProvider` de Firebase | `ZaiViewModel.kt`, `MainActivity.kt` |
| Autenticación | Login, registro y recuperación de contraseña reales con correo/contraseña (Firebase Auth) | `ZaiViewModel.kt`, `MainActivity.kt` |
| Autenticación | Botón de Apple eliminado (sin cuenta de Apple Developer) | `MainActivity.kt` |
| UX | Spinner + botones deshabilitados durante el login/registro (evita doble toque) | `MainActivity.kt`, `ZaiViewModel.kt` |
| Cumplimiento Play Store | Opción "Eliminar cuenta" (borra usuario de Firebase + todo su historial local) | `ZaiViewModel.kt`, `MainActivity.kt`, `ChatDao.kt`, `AppRepository.kt` |
| Seguridad | API key migrada a `EncryptedSharedPreferences` (Keystore), con migración automática desde texto plano | `ZaiViewModel.kt` |
| Seguridad | `groq_prefs` / `secure_groq_prefs` excluidas del backup automático | `backup_rules.xml`, `data_extraction_rules.xml` |
| Seguridad | `isMinifyEnabled` + `isShrinkResources` activados en release, con reglas ProGuard para Retrofit/Moshi/Room/Firebase | `app/build.gradle.kts`, `proguard-rules.pro` |
| Seguridad | Logging HTTP detallado solo en debug, `NONE` en release | `AppRepository.kt` |
| Rendimiento | Índice en `sessionId` de `chat_messages` + migración `4→5` | `Entities.kt`, `AppDatabase.kt` |
| Consistencia | `logout()` ahora también cierra sesión en Firebase (antes quedaba desincronizado) | `ZaiViewModel.kt` |
| Sincronización en la nube | La configuración (API key, fondo de pantalla, tema/color, proveedor/modelo + creatividad/búsqueda web/TTS/razonamiento) se sincroniza por cuenta en **Firestore** (`users/{uid}`); la imagen de fondo en **Storage** (`backgrounds/{uid}.jpg`). Al iniciar sesión se restaura desde la nube; al logout se limpia lo local (ya no queda la API key de un usuario para el siguiente). | `ZaiViewModel.kt`, `build.gradle.kts`, `libs.versions.toml`, `firestore.rules`, `storage.rules` |
| Historial en la nube | Sesiones y mensajes se espejan en Firestore (`users/{uid}/sessions/{cloudId}` y `.../messages/{msgCloudId}`), cada fila con un `cloudId` (UUID) que mapea 1:1 local↔nube. Se restauran al iniciar sesión y se suben al crear/escribir/renombrar/borrar. Room subió a versión 6 (migración `5→6` no destructiva) añadiendo `cloudId`. Reglas de Firestore ampliadas para cubrir subcolecciones. | `ZaiViewModel.kt`, `Entities.kt`, `ChatDao.kt`, `AppDatabase.kt`, `AppRepository.kt`, `firestore.rules` |

## ⏳ Pendiente / recomendado (no aplicado todavía)

| Prioridad | Tarea | Por qué importa |
|---|---|---|
| 🟠 Alta | Configurar el proveedor **GitHub** en Firebase Console (Client ID/Secret de una GitHub OAuth App) | Ya confirmado que está activado — verificar igual antes de publicar, revisando que la callback URL de Firebase esté registrada en GitHub |
| 🟠 Alta | Rediseñar el onboarding para pedir la API key **justo después** del login | Hoy el usuario entra al chat sin API key configurada y se entera recién con un error al mandar un mensaje |
| 🟡 Media | Reautenticación real para "Eliminar cuenta" cuando Firebase pide sesión reciente | Hoy solo se le pide al usuario cerrar sesión y volver a entrar; se puede automatizar con Google/GitHub/reautenticación por contraseña |
| 🟡 Media | Verificación de correo (`sendEmailVerification`) al registrarse con Email/Contraseña | Evita cuentas con correos inventados o mal escritos |
| 🟢 Baja | Mover todos los textos hardcodeados a `strings.xml` | Facilita mantenimiento y una futura traducción |
| 🟢 Baja | Verificación de conexión a internet antes de llamar a la API | Mensajes de error más claros ("sin conexión" vs error genérico) |
| 🟢 Baja | Revisar `fallbackToDestructiveMigration()` en Room | Si en el futuro cambias el esquema sin migración, se borra la base de datos completa; ya no es "solo un detalle" ahora que hay cuentas reales y persistentes |

## 🐞 Chequeo anti-errores de compilación (antes de compilar)

- [ ] Verificar que `google-services.json` esté en `app/` (ya está, coincide con `applicationId = com.aistudio.groqapp`)
- [ ] Sincronizar Gradle primero (`./gradlew --refresh-dependencies`) para que baje las nuevas dependencias (`firebase-bom`, `credentials`, `googleid`, `security-crypto`)
- [ ] Si compilas en Termux/local sin Google Play services de prueba, probar el login de Google en un dispositivo/emulador **con Google Play Services real** (Credential Manager no funciona en emuladores sin Play Store)
- [ ] Revisar que el plugin `com.google.gms.google-services` tenga la misma versión en `build.gradle.kts` raíz (`4.4.2`) — funciona, pero si ves errores de resolución, súbela a `4.5.0` para que coincida con la del catálogo (`gradle/libs.versions.toml`)
- [ ] Compilar primero en modo `debug` antes de generar el `release` firmado, ya que con `isMinifyEnabled = true` puede aparecer algún `ClassNotFoundException` en runtime si falta alguna regla ProGuard — si pasa, decime la clase exacta del stacktrace y la agrego a `proguard-rules.pro`
