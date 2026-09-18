package com.apkmanager.app.data.updater

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for interacting with the GitHub Releases API and downloading release APKs.
 */
class GitHubClient {

    companion object {
        private const val USER_AGENT = "APKManager-Android"
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 30000
        private const val MAX_REDIRECTS = 5
        private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes
    }

    private val releaseCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, GitHubRelease>>()

    /**
     * Fetches the latest release info for a given GitHub repository.
     * @param repo Repository identifier in "owner/repo" format.
     */
    suspend fun getLatestRelease(repo: String, forceRefresh: Boolean = false): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        val cleanRepo = repo.trim().removePrefix("https://github.com/").trim('/')
        if (!forceRefresh) {
            val cached = releaseCache[cleanRepo]
            if (cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS) {
                return@withContext Result.success(cached.second)
            }
        }

        try {
            val endpoint = "https://api.github.com/repos/$cleanRepo/releases/latest"
            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                // If rate limited, fall back to any available cache
                if (responseCode == 403) {
                    val cached = releaseCache[cleanRepo]
                    if (cached != null) {
                        return@withContext Result.success(cached.second)
                    }
                }

                val errorMsg = when (responseCode) {
                    404 -> "Repository not found or has no releases (HTTP 404)"
                    403 -> {
                        val resetHeader = connection.getHeaderField("x-ratelimit-reset")
                        if (!resetHeader.isNullOrBlank()) {
                            try {
                                val resetEpoch = resetHeader.toLong()
                                val waitMinutes = maxOf(1, (resetEpoch - System.currentTimeMillis() / 1000) / 60)
                                "GitHub rate limit exceeded. Resets in ~$waitMinutes min."
                            } catch (_: Exception) {
                                "GitHub rate limit exceeded. Try again in a few minutes."
                            }
                        } else {
                            "GitHub rate limit exceeded. Try again in a few minutes."
                        }
                    }
                    else -> {
                        val body = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        "GitHub error ($responseCode)${if (!body.isNullOrBlank()) ": $body" else ""}"
                    }
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)

            val tagName = json.optString("tag_name", "")
            val name = json.optString("name", tagName)
            val body = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", "")

            val assetsArray = json.optJSONArray("assets")
            val assets = mutableListOf<GitHubAsset>()
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val assetJson = assetsArray.getJSONObject(i)
                    val assetName = assetJson.optString("name", "")
                    val assetSize = assetJson.optLong("size", 0L)
                    val downloadUrl = assetJson.optString("browser_download_url", "")
                    val contentType = assetJson.optString("content_type", "")

                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        assets.add(
                            GitHubAsset(
                                name = assetName,
                                size = assetSize,
                                downloadUrl = downloadUrl,
                                contentType = contentType
                            )
                        )
                    }
                }
            }

            val release = GitHubRelease(
                tagName = tagName,
                name = name,
                body = body,
                publishedAt = publishedAt,
                assets = assets,
                htmlUrl = htmlUrl
            )
            releaseCache[cleanRepo] = Pair(System.currentTimeMillis(), release)
            Result.success(release)
        } catch (e: Exception) {
            // Fall back to cache on any network error if available
            val cached = releaseCache[cleanRepo]
            if (cached != null) {
                Result.success(cached.second)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Chooses the best APK asset for the current device's CPU architecture.
     * Prioritizes mobile over TV assets on phone devices.
     */
    fun findBestAssetForDevice(assets: List<GitHubAsset>): GitHubAsset? {
        val allApkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (allApkAssets.isEmpty()) return null
        if (allApkAssets.size == 1) return allApkAssets.first()

        // Filter out TV-only assets when general/mobile assets exist
        val apkAssets = if (allApkAssets.any { !it.name.contains("only-tv", ignoreCase = true) && !it.name.contains("leanback", ignoreCase = true) }) {
            allApkAssets.filter { !it.name.contains("only-tv", ignoreCase = true) && !it.name.contains("leanback", ignoreCase = true) }
        } else {
            allApkAssets
        }

        // Prefer mobile-specific asset if present (e.g. streamflix-v1.7.231-only-mobile.apk)
        apkAssets.firstOrNull { it.name.contains("only-mobile", ignoreCase = true) || it.name.contains("mobile", ignoreCase = true) }?.let {
            return it
        }

        val deviceAbis = Build.SUPPORTED_ABIS.toList()

        // 1. Try matching the primary device ABI
        for (abi in deviceAbis) {
            val normalizedAbi = abi.lowercase()
            val match = apkAssets.firstOrNull { asset ->
                val name = asset.name.lowercase()
                name.contains(normalizedAbi) ||
                        (normalizedAbi == "arm64-v8a" && (name.contains("arm64") || name.contains("v8a"))) ||
                        (normalizedAbi == "armeabi-v7a" && (name.contains("arm-v7a") || name.contains("v7a") || name.contains("arm32")))
            }
            if (match != null) return match
        }

        // 2. Look for universal APK
        val universalMatch = apkAssets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
        if (universalMatch != null) return universalMatch

        // 3. Fallback to first APK asset
        return apkAssets.first()
    }

    /**
     * Downloads an asset to a destination file with progress callbacks.
     */
    suspend fun downloadAsset(
        asset: GitHubAsset,
        destinationFile: File,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destinationFile.parentFile?.mkdirs()
            if (destinationFile.exists()) destinationFile.delete()

            var currentUrl = asset.downloadUrl
            var connection: HttpURLConnection? = null
            var redirects = 0

            while (redirects < MAX_REDIRECTS) {
                if (!currentUrl.startsWith("https://")) {
                    return@withContext Result.failure(Exception("Insecure HTTP redirect rejected"))
                }

                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                }

                val status = connection.responseCode
                if (status in listOf(
                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_SEE_OTHER,
                        307, 308
                    )
                ) {
                    val newLocation = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newLocation.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("Redirect with missing Location header"))
                    }
                    currentUrl = newLocation
                    redirects++
                } else if (status in 200..299) {
                    break
                } else {
                    return@withContext Result.failure(Exception("Download failed with HTTP status $status"))
                }
            }

            val validConnection = connection ?: return@withContext Result.failure(Exception("Failed to open connection"))
            val totalBytes = if (asset.size > 0) asset.size else validConnection.contentLengthLong

            // Check usable space on the existing parent directory or fallback to avoid statvfs ENOENT returning 0L on non-existent files
            val checkDir = destinationFile.parentFile?.apply { mkdirs() } ?: destinationFile
            val availableSpace = checkDir.usableSpace
            if (totalBytes > 0 && availableSpace > 0L && availableSpace < totalBytes + 25 * 1024 * 1024L) {
                val availableMb = availableSpace / (1024 * 1024)
                val requiredMb = (totalBytes + 25 * 1024 * 1024L) / (1024 * 1024)
                return@withContext Result.failure(Exception("Insufficient disk space on device (Available: ${availableMb}MB, Required: ${requiredMb}MB)"))
            }

            validConnection.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var bytesRead: Int
                    var lastProgressTime = 0L
                    var lastProgressPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0.5f
                        val currentPercent = (progress * 100).toInt()
                        val now = System.currentTimeMillis()
                        if (currentPercent != lastProgressPercent && (currentPercent - lastProgressPercent >= 1 || now - lastProgressTime >= 200 || downloaded == totalBytes)) {
                            lastProgressPercent = currentPercent
                            lastProgressTime = now
                            onProgress(progress, downloaded, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            if (destinationFile.length() < 4) {
                destinationFile.delete()
                return@withContext Result.failure(Exception("Downloaded file is empty or truncated"))
            }

            val header = ByteArray(4)
            destinationFile.inputStream().use { it.read(header) }
            if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte() || header[2] != 0x03.toByte() || header[3] != 0x04.toByte()) {
                destinationFile.delete()
                return@withContext Result.failure(Exception("Downloaded file is corrupted or not a valid APK archive"))
            }

            Result.success(destinationFile)
        } catch (e: Exception) {
            if (destinationFile.exists()) destinationFile.delete()
            Result.failure(e)
        }
    }
}
