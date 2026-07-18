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
-keep public class * extends androidx.lifecycle.LiveData
-keep interface * implements java.io.Serializable