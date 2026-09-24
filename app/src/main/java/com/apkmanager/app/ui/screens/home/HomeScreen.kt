package com.apkmanager.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhonelinkErase
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.repository.SelfUpdateRepository
import com.apkmanager.app.ui.components.AdbStatusButton
import com.apkmanager.app.ui.components.AppIcon
import com.apkmanager.app.ui.components.AppListRow
import com.apkmanager.app.ui.components.InstallButton
import com.apkmanager.app.ui.components.ProgressAppIcon
import com.apkmanager.app.ui.components.SecondaryPillButton
import com.apkmanager.app.ui.components.SectionHeader
import com.apkmanager.app.ui.components.StatusBanner
import com.apkmanager.app.ui.components.isBusy
import com.apkmanager.app.ui.components.progressText
import com.apkmanager.app.ui.screens.store.StoreViewModel
import com.apkmanager.app.ui.screens.updater.UpdaterViewModel

@Composable
fun HomeScreen(
    adbRepository: AdbRepository,
    selfUpdateRepository: SelfUpdateRepository,
    storeViewModel: StoreViewModel,
    updaterViewModel: UpdaterViewModel,
    onOpenWireless: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenInstaller: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(adbRepository, selfUpdateRepository))
) {
    val context = LocalContext.current
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val selfInfo by viewModel.selfUpdateInfo.collectAsStateWithLifecycle()
    val trackedApps by updaterViewModel.trackedApps.collectAsStateWithLifecycle()
    val storeItems by storeViewModel.filteredItems.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val pendingUpdates = trackedApps.filter { it.status is UpdateStatus.UpdateAvailable || it.status.isBusy }
    val discover = storeItems.filter { !it.isInstalled }.ifEmpty { storeItems }
    val scheme = MaterialTheme.colorScheme

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIcon(size = 36.dp, packageName = context.packageName)
                    Text("APK Manager", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    AdbStatusButton(state = connection, onClick = onOpenWireless)
                }
            }

            item {
                val (title, body, icon) = when {
                    connection.isConnected -> Triple("Wireless ADB is on", "Silent installs ready · reconnects by itself", Icons.Default.VerifiedUser)
                    connection.isLoading -> Triple("Connecting…", "Finding Wireless debugging on this phone", Icons.Default.WifiFind)
                    else -> Triple("Wireless ADB is off", "Tap to connect — installs need it", Icons.Default.PhonelinkErase)
                }
                StatusBanner(
                    title = title,
                    body = body,
                    icon = icon,
                    iconContainer = if (connection.isConnected) scheme.tertiary else if (connection.isLoading) scheme.primary else scheme.error,
                    iconTint = if (connection.isConnected) scheme.onTertiary else if (connection.isLoading) scheme.onPrimary else scheme.onError,
                    container = if (connection.isConnected || connection.isLoading) scheme.surfaceContainer else scheme.errorContainer,
                    onClick = onOpenWireless,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant) }
                )
            }

            val info = selfInfo
            if (info != null && info.status !is UpdateStatus.UpToDate && info.status !is UpdateStatus.Idle && info.latestRelease != null) {
                item {
                    SelfUpdateCard(
                        info = info,
                        onUpdate = { viewModel.installSelfUpdate() },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            if (pendingUpdates.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Updates available",
                        subtitle = "${pendingUpdates.size} app${if (pendingUpdates.size == 1) "" else "s"} from GitHub",
                        onClick = onOpenUpdates
                    )
                }
                items(pendingUpdates.take(3), key = { "upd-" + it.packageName }) { app ->
                    AppListRow(
                        title = app.appName,
                        subtitle = (app.status as? UpdateStatus.UpdateAvailable)?.let { "${app.installedVersionName} → ${it.release.cleanVersion}" }
                            ?: app.status.progressText(),
                        icon = {
                            ProgressAppIcon(status = app.status, size = 52.dp) {
                                AppIcon(size = 52.dp, packageName = app.packageName)
                            }
                        },
                        trailing = {
                            if (!app.status.isBusy) {
                                InstallButton("Update", onClick = { updaterViewModel.updateApp(app) })
                            }
                        }
                    )
                }
            }

            if (discover.isNotEmpty()) {
                item {
                    SectionHeader(title = "From the Store", subtitle = "Open-source apps on GitHub", onClick = onOpenStore)
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(discover, key = { "store-" + it.app.id }) { item ->
                            Column(
                                Modifier.width(104.dp).clickable(onClick = onOpenStore),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AppIcon(
                                    size = 104.dp,
                                    packageName = item.installedPackage,
                                    iconKey = item.app.icon,
                                    category = item.app.category
                                )
                                Text(item.app.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(item.app.category, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    onClick = onOpenInstaller,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = scheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text("Install from files", style = MaterialTheme.typography.titleSmall)
                            Text("APK or split APK parts", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelfUpdateCard(info: SelfUpdateRepository.SelfUpdateInfo, onUpdate: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val status = info.status
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppIcon(size = 64.dp, packageName = context.packageName)
                Column {
                    Text("UPDATE AVAILABLE", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                    Text("APK Manager ${info.latestRelease?.cleanVersion.orEmpty()}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        status.progressText() ?: "You have ${info.currentVersionName} · installs over ADB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status is UpdateStatus.Error) scheme.error else scheme.onSurfaceVariant
                    )
                }
            }
            when (status) {
                is UpdateStatus.Downloading -> LinearProgressIndicator(
                    progress = { status.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = scheme.tertiary,
                    drawStopIndicator = {}
                )
                is UpdateStatus.Installing, is UpdateStatus.Success -> LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = scheme.tertiary
                )
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstallButton(if (status is UpdateStatus.Error) "Try again" else "Update", onClick = onUpdate, modifier = Modifier.weight(1f))
                    info.latestRelease?.htmlUrl?.let { url ->
                        SecondaryPillButton("What's new", onClick = {
                            runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            }
                        })
                    }
                }
            }
        }
    }
}
