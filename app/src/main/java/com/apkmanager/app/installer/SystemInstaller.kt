package com.apkmanager.app.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.apkmanager.app.adb.AdbInstaller.InstallResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Fallback installer used when Wireless ADB isn't connected: a PackageInstaller
 * session for one APK or all split parts. Android shows its confirmation dialog;
 * [install] suspends until the user confirms or cancels.
 */
object SystemInstaller {

    private const val TAG = "SystemInstaller"
    private const val ACTION_STATUS = "com.apkmanager.app.SYSTEM_INSTALL_STATUS"
    private const val CONFIRM_TIMEOUT_MS = 10 * 60 * 1000L

    private val pending = ConcurrentHashMap<Int, CompletableDeferred<InstallResult>>()

    suspend fun install(context: Context, uris: List<Uri>): InstallResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext InstallResult.Failure("No APK selected")
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return@withContext InstallResult.Failure("Allow APK Manager to install apps, then try again.")
        }

        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        val result = CompletableDeferred<InstallResult>()
        pending[sessionId] = result

        try {
            installer.openSession(sessionId).use { session ->
                uris.forEachIndexed { index, uri ->
                    val input = context.contentResolver.openInputStream(uri)
                        ?: return@withContext InstallResult.Failure("Can't read ${uri.lastPathSegment}").also { session.abandon() }
                    input.use { source ->
                        session.openWrite("part_$index.apk", 0, -1).use { out ->
                            source.copyTo(out)
                            session.fsync(out)
                        }
                    }
                }
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(ACTION_STATUS).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(callback.intentSender)
            }
            withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { result.await() }
                ?: InstallResult.Failure("Install wasn't confirmed")
        } catch (e: Exception) {
            Log.e(TAG, "System install failed", e)
            InstallResult.Failure(e.message ?: "Install failed")
        } finally {
            pending.remove(sessionId)
        }
    }

    /** Receives PackageInstaller status for sessions started by [install]. */
    class StatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
                }
                PackageInstaller.STATUS_SUCCESS -> pending[sessionId]?.complete(
                    InstallResult.Success(intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME).orEmpty())
                )
                else -> {
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    pending[sessionId]?.complete(
                        InstallResult.Failure(
                            if (status == PackageInstaller.STATUS_FAILURE_ABORTED) "Install cancelled"
                            else message ?: "Install failed"
                        )
                    )
                }
            }
        }
    }
}
