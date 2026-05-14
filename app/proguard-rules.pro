# ── App Data Layer ─────────────────────────────────────
# Room entities, DAOs, Firestore serialization models,
# repository classes, and data transfer objects.
# These use reflection for DB mapping and cloud sync.
-keep class com.anantva.tether.data.** { *; }

# ── Insights & Domain Models ───────────────────────────
# Engine return types, use-case results, and sealed
# result classes referenced across ViewModel boundaries.
-keep class com.anantva.tether.insights.** { *; }
-keep class com.anantva.tether.calculator.** { *; }

# ── Room ────────────────────────────────────────────────
# Room uses annotation processing + reflection for
# SQL generation. Entities/DAOs/Database must survive.
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.Database class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ── Hilt / Dagger ──────────────────────────────────────
# Generated components, factories, and modules.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityRetainedComponentManager$ActivityRetainedComponentBuilderEntryPoint { *; }

# ── Firebase / Firestore ────────────────────────────────
# Firebase Auth and Firestore use runtime class resolution.
-keep class com.google.firebase.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.Exclude <methods>;
    @com.google.firebase.firestore.IgnoreExtraProperties <methods>;
    @com.google.firebase.firestore.ServerTimestamp <methods>;
}

# ── Google Sign-In ──────────────────────────────────────
-keep class com.google.android.gms.auth.** { *; }

# ── Kotlin ──────────────────────────────────────────────
# Coroutine continuations, sealed class hierarchies,
# and metadata used by reflection-based frameworks.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions
-keep class kotlin.Metadata { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.internal.**

# ── Jetpack Compose ────────────────────────────────────
# Stability annotations used by Compose compiler.
-keep @androidx.compose.runtime.Stable class *
-keep @androidx.compose.runtime.Immutable class *

# ── Debug Symbols ──────────────────────────────────────
# Preserve line numbers for crash reporting.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
