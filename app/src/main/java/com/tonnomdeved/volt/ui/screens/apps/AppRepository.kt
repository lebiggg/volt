package com.tonnomdeved.volt.ui.screens.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Représentation immuable d'une appli affichée dans la liste.
 *
 * `icon` est volontairement nullable + porté par `ImageBitmap` (et non `Drawable`)
 * pour permettre une utilisation sûre depuis un Composable sans `remember { ... }`
 * supplémentaire et sans fuite de Context.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

/**
 * Repository de la liste des apps installées.
 *
 * - Filtre les apps système non lançables (préserve la pertinence UI).
 * - Travaille en `Dispatchers.IO` → jamais sur le main thread (cf. spec §I, perfs).
 * - Renvoie une `List` immuable (Thread-Safety, spec §IV).
 */
class AppRepository(private val context: Context) {

    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        val installed: List<ApplicationInfo> =
            pm.getInstalledApplications(PackageManager.GET_META_DATA)

        installed
            .asSequence()
            .filter { info ->
                // Ne garder que les apps avec un launcher (pertinent pour l'utilisateur)
                pm.getLaunchIntentForPackage(info.packageName) != null &&
                info.packageName != context.packageName // exclure Volt lui-même
            }
            .map { info ->
                AppInfo(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = runCatching { pm.getApplicationIcon(info).toImageBitmap() }
                        .getOrNull()
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}

/** Conversion `Drawable` → `ImageBitmap` (gère VectorDrawable + BitmapDrawable). */
private fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap.asImageBitmap()
    val width  = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp.asImageBitmap()
}
