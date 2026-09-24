package com.apkmanager.app.ui.screens.installer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.data.ApkFileInfo
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.ui.components.AppIcon

private val APK_MIME_TYPES = arrayOf("application/vnd.android.package-archive", "application/octet-stream")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerScreen(
    adbRepository: AdbRepository,
    incomingUris: List<Uri>,
    onIncomingUrisHandled: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenWireless: () -> Unit,
    viewModel: InstallerViewModel = viewModel(factory = InstallerViewModel.Factory(adbRepository))
) {
    val context = LocalContext.current
    val apks by viewModel.selectedApks.collectAsStateWithLifecycle()
    val progress by viewModel.installProgress.collectAsStateWithLifecycle()
    val connection by adbRepository.connectionState.collectAsStateWithLifecycle()

    fun select(uris: List<Uri>) {
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        viewModel.onApksSelected(context, uris)
    }

    LaunchedEffect(incomingUris) {
        if (incomingUris.isNotEmpty()) {
            select(incomingUris)
            onIncomingUrisHandled()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) select(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Install APK") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (apks.isEmpty()) {
                PickFilesCard(onPick = { picker.launch(APK_MIME_TYPES) })
                return@Column
            }

            when (val state = progress) {
                is InstallProgress.Installing -> ProgressHeader(apks, state.message, viaAdb = connection.isConnected)
                is InstallProgress.Success -> ResultHeader(apks, success = true, message = state.message)
                is InstallProgress.Failure -> ResultHeader(apks, success = false, message = state.message)
                InstallProgress.Idle -> PackageHeader(apks)
            }

            if (!connection.isConnected && progress !is InstallProgress.Success) {
                NoAdbCard(onConnect = onOpenWireless)
            }

            PartsList(apks)

            Spacer(Modifier.height(8.dp))
            when (progress) {
                is InstallProgress.Installing -> Unit
                is InstallProgress.Success -> OutlinedButton(
                    onClick = { viewModel.reset(); picker.launch(APK_MIME_TYPES) },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Install another") }
                else -> {
                    Button(
                        onClick = viewModel::install,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    ) { Text(if (progress is InstallProgress.Failure) "Try again" else "Install") }
                    OutlinedButton(
                        onClick = { picker.launch(APK_MIME_TYPES) },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Choose other files") }
                }
            }
        }
    }
}

@Composable
private fun PickFilesCard(onPick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onPick,
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(48.dp))
            Text("Choose APK files", style = MaterialTheme.typography.titleMedium)
            Text(
                "A single APK, or every part of a split APK (base + config splits).",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PackageHeader(apks: List<ApkFileInfo>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AppIcon(size = 72.dp, uri = apks.first().uri)
        Column(Modifier.weight(1f)) {
            Text(apks.first().fileName, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(summary(apks), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProgressHeader(apks: List<ApkFileInfo>, message: String, viaAdb: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(120.dp),
                strokeWidth = 5.dp,
                color = MaterialTheme.colorScheme.tertiary
            )
            AppIcon(size = 84.dp, uri = apks.first().uri)
        }
        Text(message, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(
                if (viaAdb) "Silent install over ADB — no system prompt" else "Confirm in Android's installer",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultHeader(apks: List<ApkFileInfo>, success: Boolean, message: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (success) scheme.tertiaryContainer else scheme.errorContainer
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppIcon(size = 56.dp, uri = apks.first().uri)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (success) scheme.onTertiaryContainer else scheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        if (success) "Installed" else "Install failed",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (success) scheme.onTertiaryContainer else scheme.onErrorContainer
                    )
                }
                Text(
                    if (success) apks.first().fileName else message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (success) scheme.onTertiaryContainer else scheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun NoAdbCard(onConnect: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Adb, contentDescription = null, tint = scheme.primary)
                Text("Wireless ADB is off", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "Android's installer will ask you to confirm. Connect ADB for silent installs.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onConnect) { Text("Connect ADB") }
        }
    }
}

@Composable
private fun PartsList(apks: List<ApkFileInfo>) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                if (apks.size == 1) "1 file" else "${apks.size} parts",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            apks.forEach { apk ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text(apk.fileName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (apk.size > 0) Text(apk.sizeDisplay, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun summary(apks: List<ApkFileInfo>): String {
    val total = apks.sumOf { it.size }
    val size = if (total > 0) " · " + ApkFileInfo(apks.first().uri, "", total).sizeDisplay else ""
    return (if (apks.size == 1) "APK" else "Split APK · ${apks.size} parts") + size
}
