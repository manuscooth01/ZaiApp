# -------------------------------------------------
# Room (persistencia SQLite)
# -------------------------------------------------
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Moshi (JSON)
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.JsonClass <fields>;
}
-dontwarn com.squareup.moshi.**

# Retrofit (and its annotations)
-keep class retrofit2.** { *; }
-keepattributes Signature, *Annotation*
-keep interface retrofit2.Endpoint
-dontwarn retrofit2.**

# Gson (if used elsewhere)
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# General attributes
-keepattributes AndroidManifestApplication

# Crashlytics (reglas oficiales de Firebase para reportes legibles):
# https://firebase.google.com/docs/crashlytics/android/get-deobfuscated-reports
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Protege el SDK de Crashlytics del shrinking de R8 (evita que el
# inicializador se borre y rompa el arranque en release).
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**
-keep public class * extends androidx.lifecycle.LiveData
-keep interface * implements java.io.Serializable