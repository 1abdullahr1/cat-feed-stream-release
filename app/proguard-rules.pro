# ==========================================================================
# PurrFeed ProGuard / R8 rules
# ==========================================================================

# --- Kotlin ---
-dontwarn kotlin.**

# --- Gson (reflection-based serialization) ---
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod

# Keep Gson model classes and their members so reflection-based parsing works
-keep class com.catfeed.stream.data.model.** { <fields>; }
-keep class com.catfeed.stream.data.model.CataasResponse { *; }

# Keep generic type information used by Gson's TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken

# Prevent R8 from stripping Gson internals
-keep class com.google.gson.stream.** { *; }
-dontwarn com.google.gson.**

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Coil ---
-keep class coil.** { *; }
-dontwarn coil.**

# --- AndroidX / Lifecycle ---
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.**
