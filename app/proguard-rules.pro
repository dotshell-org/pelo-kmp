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

# ==================== Attributes ====================
# Signature carries generic type information; InnerClasses and EnclosingMethod are what make it
# resolvable. Annotations must survive for kotlinx.serialization. These arrived with Retrofit and
# Gson, both long gone, but the serialization layer needs them just the same.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# ==================== Suppressions ====================
# Optional references reachable through OkHttp, which is still here as Ktor's Android engine.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn sun.misc.**

# Guarded by a NoClassDefFoundError try/catch and only used when available.
-dontwarn kotlin.Unit

# ==================== Data models ====================
# Inherited from the Gson era, when R8 renaming a field broke reflective deserialization.
# kotlinx.serialization does not read field names at runtime — it generates serializers at compile
# time — so these blanket keeps are most likely unnecessary now and are costing shrinking across
# three whole packages. Narrowing them safely needs a release build to verify against, which this
# machine cannot produce (the signing keystore points at an absolute path from another machine),
# so they stay until someone can check.
-keep class eu.dotshell.pelo.generic.data.models.** { *; }
-keep class eu.dotshell.pelo.specific.data.model.** { *; }

# config.json is parsed by kotlinx.serialization into AppConfig and its nested *Data classes.
-keep class eu.dotshell.pelo.generic.data.config.AppConfig { *; }
-keep class eu.dotshell.pelo.generic.data.config.**Data { *; }

# ==================== Kotlin Serialization ====================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class eu.dotshell.pelo.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Telemetry sealed class hierarchy and DTOs — kept whole because polymorphic
# kotlinx.serialization needs the subclass list at runtime.
-keep class eu.dotshell.pelo.generic.data.telemetry.** { *; }
-keep class eu.dotshell.pelo.generic.data.local_history.** { *; }

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

# ProfileInstaller must survive R8 (full mode + strict keep rules here) intact, or its
# ProfileInstallReceiver answers install/skip broadcasts with result=0 and the packaged Baseline
# Profile never gets installed — silently, in real release builds, not just macrobenchmarks.
-keep class androidx.profileinstaller.** { *; }