# -------------------------------------------------
# Room (persistencia SQLite)
# -------------------------------------------------
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# -------------------------------------------------
# Moshi (JSON) — modelos de API, adapters generados (codegen) y reflexion
# -------------------------------------------------
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
# Mantener los modelos de la API (necesario para la reflexion de Moshi)
-keep class com.example.data.api.** { *; }
# Mantener los adapters generados por codegen (KSP) — R8 los borraria si no
-keep class **JsonAdapter { *; }
-keepnames @com.squareup.moshi.JsonClass class *
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <init>(...);
    <fields>;
}
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
# KotlinJsonAdapterFactory usa kotlin-reflect: mantener metadata Kotlin
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Retrofit (and its annotations)
-keep class retrofit2.** { *; }
-keepattributes Signature, *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault, InnerClasses, EnclosingMethod
-keep interface retrofit2.Endpoint
-dontwarn retrofit2.**

# Gson (if used elsewhere)
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# General attributes
-keepattributes AndroidManifestApplication
-keep public class * extends androidx.lifecycle.LiveData
-keep interface * implements java.io.Serializable