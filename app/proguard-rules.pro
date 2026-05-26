# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# ==================== Retrofit ====================
# Retrofit does reflection on generic parameters. InnerClasses is required to use Signature and
# EnclosingMethod is required to use InnerClasses.
-keepattributes Signature, InnerClasses, EnclosingMethod

# Retrofit does reflection on method and parameter annotations.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep annotation default values (e.g., retrofit2.http.Field.encoded).
-keepattributes AnnotationDefault

# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

# Guarded by a NoClassDefFoundError try/catch and only used when available.
-dontwarn kotlin.Unit

# Top-level functions that can only be used by Kotlin.
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# With R8 full mode, it sees no subtypes of Retrofit interfaces since they are created with a Proxy
# and). so it assumes those methods don't exist. You can't make assumptions about what
# interfaces the Proxy implements based on the call site. This keeps the methods intact
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Keep inherited services.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# With R8 full mode generic signatures are stripped for classes that are not
# kept., we need to keep the Retrofit callback generic signatures to be able to
# reflect on the type arguments for the Response type.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# ==================== Gson ====================
# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**

# Application classes that will be serialized/deserialized over Gson
# Keep ALL data model classes (generic + specific) — R8 strips field names otherwise,
# breaking Gson deserialization (fields without @SerializedName get renamed)
-keep class com.pelotcl.app.generic.data.models.** { *; }
-keep class com.pelotcl.app.specific.data.model.** { *; }

# config.yml is parsed via SnakeYAML → Gson reflection on AppConfig + nested *Data classes.
# Without these rules R8 full-mode strips the no-arg constructor or class-merges them, causing
# "Abstract classes can't be instantiated" at runtime.
-keep class com.pelotcl.app.generic.data.config.AppConfig { *; }
-keep class com.pelotcl.app.generic.data.config.**Data { *; }

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ==================== Kotlin Serialization ====================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class com.pelotcl.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Telemetry sealed class hierarchy and DTOs — kept whole because polymorphic
# kotlinx.serialization needs the subclass list at runtime.
-keep class com.pelotcl.app.generic.data.telemetry.** { *; }
-keep class com.pelotcl.app.generic.data.local_history.** { *; }

# ==================== OkHttp ====================
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ==================== Raptor-KT ====================
# Keep only the specific Raptor classes that are actually used
-keep class io.raptor.PeriodData { *; }
-keep class io.raptor.RaptorLibrary { *; }
-keep class io.raptor.model.Stop { *; }

# ==================== MapLibre ====================
# Keep specific MapLibre classes used in the app
-keep class org.maplibre.android.MapLibre { *; }
-keep class org.maplibre.android.camera.CameraPosition { *; }
-keep class org.maplibre.android.camera.CameraUpdateFactory { *; }
-keep class org.maplibre.android.geometry.LatLng { *; }
-keep class org.maplibre.android.geometry.LatLngBounds { *; }
-keep class org.maplibre.android.maps.MapLibreMap { *; }
-keep class org.maplibre.android.maps.MapView { *; }
-keep class org.maplibre.android.maps.Style { *; }
-keep class org.maplibre.android.offline.OfflineManager { *; }
-keep class org.maplibre.android.offline.OfflineRegion { *; }
-keep class org.maplibre.android.offline.OfflineRegionError { *; }
-keep class org.maplibre.android.offline.OfflineRegionStatus { *; }
-keep class org.maplibre.android.offline.OfflineTilePyramidRegionDefinition { *; }
-keep class org.maplibre.android.style.expressions.Expression { *; }
-keep class org.maplibre.android.style.layers.CircleLayer { *; }
-keep class org.maplibre.android.style.layers.LineLayer { *; }
-keep class org.maplibre.android.style.layers.PropertyFactory { *; }
-keep class org.maplibre.android.style.layers.SymbolLayer { *; }
-keep class org.maplibre.android.style.sources.GeoJsonOptions { *; }
-keep class org.maplibre.android.style.sources.GeoJsonSource { *; }
-dontwarn org.maplibre.android.**