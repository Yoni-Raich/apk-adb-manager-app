package com.apkmanager.app.ui.screens.packages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.data.PackageInfo
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.repository.PackageRepository
import com.apkmanager.app.ui.components.AppIcon
import com.apkmanager.app.ui.components.AppListRow
import com.apkmanager.app.ui.components.EmptyState
import com.apkmanager.app.ui.components.SearchPill
import com.apkmanager.app.util.rememberAppLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagesScreen(
    adbRepository: AdbRepository,
    packageRepository: PackageRepository,
    onOpenWireless: () -> Unit,
    viewModel: PackagesViewModel = viewModel(factory = PackagesViewModel.Factory(adbRepository, packageRepository))
) {
    val context = LocalContext.current
    val allPackages by viewModel.packages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showSystem by viewModel.showSystemApps.collectAsStateWithLifecycle()
    val packages = remember(allPackages, showSystem) { allPackages.filter { it.isSystemApp == showSystem } }
    val selected by viewModel.selectedPackage.collectAsStateWithLifecycle()
    val uninstallResult by viewModel.uninstallResult.collectAsStateWithLifecycle()
    val connection by adbRepository.connectionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uninstallResult) {
        uninstallResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUninstallResult()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    "Installed apps",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
                )
            }
            item {
                SearchPill(query, viewModel::updateSearchQuery, "Name or package", Modifier.padding(horizontal = 16.dp))
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TypeChip("User apps", !showSystem) { if (showSystem) viewModel.toggleSystemApps() }
                    TypeChip("System apps", showSystem) { if (!showSystem) viewModel.toggleSystemApps() }
                }
            }
            if (isLoading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            }
            if (!isLoading && packages.isEmpty()) {
                item {
                    EmptyState(icon = Icons.Default.Apps, title = "No apps", body = "Nothing matches your search.")
                }
            }
            items(packages, key = { it.packageName }) { pkg ->
                val label = rememberAppLabel(pkg.packageName, pkg.displayName)
                AppListRow(
                    title = label,
                    subtitle = "${pkg.packageName} · ${pkg.versionName.ifBlank { pkg.versionDisplay }}",
                    icon = { AppIcon(size = 48.dp, packageName = pkg.packageName) },
                    onClick = { viewModel.selectPackage(pkg) }
                )
            }
        }
    }

    selected?.let { pkg ->
        ModalBottomSheet(onDismissRequest = viewModel::clearSelection) {
            AppActionsSheet(
                pkg = pkg,
                onOpen = { openApp(context, pkg.packageName); viewModel.clearSelection() },
                onDetails = { openAppDetails(context, pkg.packageName); viewModel.clearSelection() },
                onUninstall = { viewModel.uninstallPackage(context, pkg.packageName) }
            )
        }
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null
    )
}

@Composable
private fun AppActionsSheet(pkg: PackageInfo, onOpen: () -> Unit, onDetails: () -> Unit, onUninstall: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val canOpen = remember(pkg.packageName) { context.packageManager.getLaunchIntentForPackage(pkg.packageName) != null }
    Column(Modifier.padding(bottom = 24.dp)) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppIcon(size = 56.dp, packageName = pkg.packageName)
            Column {
                Text(rememberAppLabel(pkg.packageName, pkg.displayName), style = MaterialTheme.typography.titleMedium)
                Text(pkg.packageName, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Text("Version ${pkg.versionDisplay}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = scheme.outlineVariant)
        if (canOpen) SheetAction(Icons.AutoMirrored.Filled.OpenInNew, "Open", onOpen)
        SheetAction(Icons.Default.Info, "App details", onDetails)
        if (!pkg.isSystemApp) {
            SheetAction(Icons.Default.Delete, "Uninstall silently", onUninstall, tint = scheme.error)
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    ListItem(
        headlineContent = { Text(label, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    )
}

private fun openApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    runCatching { context.startActivity(intent) }
}

private fun openAppDetails(context: Context, packageName: String) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }
}
