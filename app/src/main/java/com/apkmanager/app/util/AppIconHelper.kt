package com.apkmanager.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Utility for extracting and rendering the original application icons
 * for installed packages, uninstalled APK files, and cached APK archives.
 */
object AppIconHelper {

    private val iconCache = LruCache<String, ImageBitmap>(150)

    /**
     * Converts any Android Drawable (Adaptive, Bitmap, Vector, etc.) to a Bitmap.
     */
    fun drawableToBitmap(drawable: Drawable, targetSize: Int = 128): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            return drawable.bitmap
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else targetSize
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else targetSize

        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Retrieves the original application icon for an installed package.
     */
    suspend fun getInstalledAppIcon(context: Context, packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext null

        synchronized(iconCache) {
            val cached = iconCache.get(packageName)
            if (cached != null) return@withContext cached
        }

        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = drawableToBitmap(drawable)
            val imageBitmap = bitmap.asImageBitmap()
            synchronized(iconCache) {
                iconCache.put(packageName, imageBitmap)
            }
            imageBitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts the original application icon from an uninstalled APK file on disk.
     */
    suspend fun getApkFileIcon(context: Context, apkFile: File): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!apkFile.exists() || apkFile.length() == 0L) return@withContext null

        val cacheKey = "file:${apkFile.absolutePath}:${apkFile.lastModified()}"
        synchronized(iconCache) {
            val cached = iconCache.get(cacheKey)
            if (cached != null) return@withContext cached
        }

        try {
            val packageArchiveInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                ?: return@withContext null

            val appInfo = packageArchiveInfo.applicationInfo ?: return@withContext null
            appInfo.sourceDir = apkFile.absolutePath
            appInfo.publicSourceDir = apkFile.absolutePath

            val drawable = appInfo.loadIcon(context.packageManager)
            val bitmap = drawableToBitmap(drawable)
            val imageBitmap = bitmap.asImageBitmap()

            synchronized(iconCache) {
                iconCache.put(cacheKey, imageBitmap)
            }
            imageBitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts the application icon from a content or file URI.
     */
    suspend fun getApkUriIcon(context: Context, uri: android.net.Uri): ImageBitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "uri:$uri"
        synchronized(iconCache) {
            val cached = iconCache.get(cacheKey)
            if (cached != null) return@withContext cached
        }

        try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return@withContext null
                val file = File(path)
                return@withContext getApkFileIcon(context, file)
            }

            // For content:// URIs, copy to a temp cache file to extract archive info
            val tempFile = File(context.cacheDir, "icon_extract_${System.currentTimeMillis()}.apk")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val icon = getApkFileIcon(context, tempFile)
                if (icon != null) {
                    synchronized(iconCache) {
                        iconCache.put(cacheKey, icon)
                    }
                }
                icon
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Composable that displays the real original icon for an application, package, or APK URI.
 * Falls back to [contentFallback] or [fallbackIcon] if unavailable.
 */
@Composable
fun AppIconImage(
    packageName: String? = null,
    uri: android.net.Uri? = null,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector = Icons.Default.Android,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentFallback: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = packageName, key2 = uri) {
        if (!packageName.isNullOrBlank()) {
            value = AppIconHelper.getInstalledAppIcon(context, packageName)
        } else if (uri != null) {
            value = AppIconHelper.getApkUriIcon(context, uri)
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(10.dp))
        )
    } else if (contentFallback != null) {
        contentFallback()
    } else {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}
