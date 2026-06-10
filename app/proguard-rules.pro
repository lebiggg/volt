# ============================================================== #
# Volt — Règles R8 / ProGuard pour la release.                     #
# Objectif : minification agressive sans casser OkHttp / coroutines #
# + élimination totale des logs résiduels (hardening privacy).      #
# ============================================================== #

# --- OkHttp / Okio / TLS providers ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Kotlin Coroutines ---
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.flow.**

# --- BroadcastIntent surface : conserver constantes ACTION_*/EXTRA_* ---
# (un broadcast intra-process référence ces String par valeur, mais R8
# peut être tenté de remplacer les `companion object` constants.)
-keep class com.tonnomdeved.volt.BatteryCommandService {
    public static final java.lang.String ACTION_DELIVER_PUSH;
    public static final java.lang.String EXTRA_TARGET_PACKAGE;
    public static final java.lang.String EXTRA_MESSAGE_DATA;
}

# --- Conserve le Service & Application (référencés par le Manifest) ---
-keep class com.tonnomdeved.volt.BatteryCommandService { *; }
-keep class com.tonnomdeved.volt.VoltApplication { *; }
-keep class com.tonnomdeved.volt.MainActivity { *; }

# --- Privacy hardening : pas de Log.v/d/i en release ---
# `Log.w` et `Log.e` sont préservés pour les vrais incidents.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# --- Compose (la BOM gère déjà l'essentiel, on garde la marge de sûreté) ---
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ============================================================== #
# CRITIQUE — cibles de réflexion. Volt invoque ces méthodes par     #
# NOM (getDeclaredMethod / getMethod). Sans ces règles, R8 les      #
# renomme et la réflexion casse SILENCIEUSEMENT en release.         #
# ============================================================== #

# --- Shizuku : newProcess() invoqué par réflexion dans ShizukuGateway ---
-keep class rikka.shizuku.Shizuku { *; }
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# --- ShizukuProvider référencé par le Manifest ---
-keep class rikka.shizuku.ShizukuProvider { *; }

# --- API framework appelées par réflexion (normalement non touchées par R8
#     car classes système, mais on documente l'intention) ---
-keep class android.app.usage.UsageStatsManager {
    public void setAppStandbyBucket(java.lang.String, int);
}
-keep class android.app.role.RoleManager {
    public java.util.List getRoleHolders(java.lang.String);
}

# ============================================================== #
# Room — entities + DAO générés. KSP produit des classes que R8     #
# ne doit pas stripper.                                             #
# ============================================================== #
-keep class com.tonnomdeved.volt.data.hibernation.persistence.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# --- WorkManager : HibernationWorker instancié par réflexion par le framework ---
-keep class com.tonnomdeved.volt.data.hibernation.HibernationWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- TileService instancié par SystemUI ---
-keep class com.tonnomdeved.volt.system.VoltTileService { *; }
