package com.apkmanager.app.ui.screens.updater

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkmanager.app.data.updater.TrackedApp
import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.ui.components.AppIcon
import com.apkmanager.app.ui.components.AppListRow
import com.apkmanager.app.ui.components.EmptyState
import com.apkmanager.app.ui.components.InstallButton
import com.apkmanager.app.ui.components.ProgressAppIcon
import com.apkmanager.app.ui.components.SecondaryPillButton
import com.apkmanager.app.ui.components.isBusy
import com.apkmanager.app.ui.components.progressText
import com.apkmanager.app.ui.components.rememberAdbGate

@Composable
fun UpdaterScreen(
    viewModel: UpdaterViewModel,
    adbRepository: AdbRepository,
    onOpenWireless: () -> Unit
) {
    val apps by viewModel.trackedApps.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val connection by adbRepository.connectionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val requireAdb = rememberAdbGate(connection.isConnected, snackbarHostState, onOpenWireless)
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TrackedApp?>(null) }

    val pending = apps.filter { it.status is UpdateStatus.UpdateAvailable || it.status.isBusy || it.status is UpdateStatus.Error }
    val checking = apps.filter { it.status is UpdateStatus.Checking || it.status is UpdateStatus.Idle }
    val upToDate = apps.filter { it.status is UpdateStatus.UpToDate || it.status is UpdateStatus.Success }
    val available = apps.count { it.status is UpdateStatus.UpdateAvailable }
    val scheme = MaterialTheme.colorScheme

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Updates", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::checkForUpdates, enabled = !isRefreshing) {
                        if (isRefreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Check for updates")
                    }
                }
            }

            if (apps.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = scheme.surfaceContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        checking.isNotEmpty() && available == 0 -> "Checking ${apps.size} apps…"
                                        available == 0 -> "All apps are up to date"
                                        available == 1 -> "1 update available"
                                        else -> "$available updates available"
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "${apps.size} tracked on GitHub",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            if (available > 0) InstallButton("Update all", onClick = { requireAdb(viewModel::updateAll) })
                        }
                    }
                }
            } else if (!isRefreshing) {
                item {
                    EmptyState(
                        icon = Icons.Default.SystemUpdate,
                        title = "Nothing tracked yet",
                        body = "Apps you install from GitHub show up here automatically. You can also track any repo.",
                        action = { InstallButton("Track a GitHub repo", onClick = { showAdd = true }) }
                    )
                }
            }

            items(pending, key = { "p-" + it.packageName }) { app ->
                UpdateRow(app, onUpdate = { requireAdb { viewModel.updateApp(app) } }, onRetry = { viewModel.checkSingleApp(app) },
                    onEdit = { editing = app }, onRemove = { viewModel.removeTrackedApp(app) })
            }
            items(checking, key = { "c-" + it.packageName }) { app ->
                UpdateRow(app, onUpdate = {}, onRetry = {}, onEdit = { editing = app }, onRemove = { viewModel.removeTrackedApp(app) })
            }

            if (upToDate.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = scheme.outlineVariant)
                    Text(
                        "Up to date",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(upToDate, key = { "u-" + it.packageName }) { app ->
                    UpdateRow(app, onUpdate = {}, onRetry = {}, onEdit = { editing = app }, onRemove = { viewModel.removeTrackedApp(app) })
                }
            }

            if (apps.isNotEmpty()) {
                item {
                    Surface(
                        onClick = { showAdd = true },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = scheme.primary)
                            Column {
                                Text("Track a GitHub repo", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Get updates for any app you sideloaded",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddRepoDialog(
            installedApps = installedApps,
            onDismiss = { showAdd = false },
            onConfirm = { pkg, repo ->
                viewModel.addCustomRepo(pkg, repo)
                showAdd = false
            }
        )
    }
    editing?.let { app ->
        EditRepoDialog(
            app = app,
            onDismiss = { editing = null },
            onConfirm = { repo ->
                viewModel.editRepo(app, repo)
                editing = null
            }
        )
    }
}

@Composable
private fun UpdateRow(
    app: TrackedApp,
    onUpdate: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val status = app.status
    var menu by remember { mutableStateOf(false) }
    val available = status as? UpdateStatus.UpdateAvailable

    Column {
        AppListRow(
            title = app.appName,
            subtitle = available?.let { "${app.installedVersionName} → ${it.release.cleanVersion}" }
                ?: "${app.installedVersionName} · ${app.githubRepo}",
            supporting = status.progressText(),
            supportingColor = if (status is UpdateStatus.Error) scheme.error else scheme.tertiary,
            icon = {
                ProgressAppIcon(status = status, size = 52.dp) {
                    AppIcon(size = 52.dp, packageName = app.packageName)
                }
            },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        available != null -> InstallButton("Update", onClick = onUpdate)
                        status is UpdateStatus.Error -> SecondaryPillButton("Retry", onClick = onRetry)
                        status is UpdateStatus.UpToDate || status is UpdateStatus.Success -> {
                            val launch = remember(app.packageName) { context.packageManager.getLaunchIntentForPackage(app.packageName) }
                            if (launch != null) SecondaryPillButton("Open", onClick = { runCatching { context.startActivity(launch) } })
                        }
                        else -> Unit
                    }
                    Column {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options for ${app.appName}")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Change repo") }, onClick = { menu = false; onEdit() })
                            DropdownMenuItem(text = { Text("Stop tracking") }, onClick = { menu = false; onRemove() })
                        }
                    }
                }
            }
        )
        if (available != null && available.release.body.isNotBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Column(Modifier.padding(start = 96.dp, end = 16.dp, bottom = 8.dp)) {
                Text("What's new", style = MaterialTheme.typography.labelLarge)
                Text(
                    available.release.body.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3
                )
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (expanded) "Show less" else "Show more")
                }
            }
        }
    }
}
